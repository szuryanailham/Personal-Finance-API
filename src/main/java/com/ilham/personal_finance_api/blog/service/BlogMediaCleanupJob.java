package com.ilham.personal_finance_api.blog.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(name = {"app.blog.enabled", "app.blog.media.cleanup.enabled"}, havingValue = "true")
public class BlogMediaCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(BlogMediaCleanupJob.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ImageKitMediaClient imageKit;
    private final Clock clock;
    private final Duration grace;
    private final Duration retry;
    private final int batchSize;

    public BlogMediaCleanupJob(@Qualifier("blogDataSource") DataSource dataSource,
            ImageKitMediaClient imageKit, Clock clock,
            @Value("${app.blog.media.cleanup.grace-period:24h}") Duration grace,
            @Value("${app.blog.media.cleanup.retry-delay:1h}") Duration retry,
            @Value("${app.blog.media.cleanup.batch-size:50}") int batchSize) {
        if (grace.isNegative() || retry.isNegative() || retry.isZero() || batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("Invalid media cleanup settings");
        }
        this.jdbc = new JdbcTemplate(dataSource);
        this.transactions = new TransactionTemplate(new JdbcTransactionManager(dataSource));
        this.imageKit = imageKit;
        this.clock = clock;
        this.grace = grace;
        this.retry = retry;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.blog.media.cleanup.interval:1h}",
            initialDelayString = "${app.blog.media.cleanup.initial-delay:1m}")
    public void clean() {
        if (!imageKit.configured()) return;
        for (int i = 0; i < batchSize; i++) {
            Boolean processed = transactions.execute(tx -> {
                var now = clock.instant();
                record Candidate(UUID id, String fileId) {}
                var candidates = jdbc.query("""
                        SELECT m.id,m.provider_asset_id FROM media m
                        WHERE m.storage_provider='imagekit' AND m.lifecycle_status='ACTIVE'
                          AND m.provider_asset_id IS NOT NULL AND btrim(m.provider_asset_id)<>''
                          AND m.unused_since<=? AND (m.next_cleanup_at IS NULL OR m.next_cleanup_at<=?)
                          AND NOT EXISTS (SELECT 1 FROM post_media pm WHERE pm.media_id=m.id)
                          AND NOT EXISTS (SELECT 1 FROM posts p WHERE p.featured_media_id=m.id AND p.deleted_at IS NULL)
                          AND NOT EXISTS (SELECT 1 FROM users u WHERE u.photo_profile=m.url)
                        ORDER BY m.unused_since,m.id LIMIT 1 FOR NO KEY UPDATE OF m SKIP LOCKED
                        """, (rs, row) -> new Candidate(rs.getObject("id", UUID.class), rs.getString("provider_asset_id")),
                        Timestamp.from(now.minus(grace)), Timestamp.from(now));
                if (candidates.isEmpty()) return false;
                var media = candidates.getFirst();
                // Fresh statement/snapshot after acquiring the lock: a save might have just committed.
                boolean unused = Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT m.lifecycle_status='ACTIVE' AND m.unused_since<=?
                          AND NOT EXISTS (SELECT 1 FROM post_media pm WHERE pm.media_id=m.id)
                          AND NOT EXISTS (SELECT 1 FROM posts p WHERE p.featured_media_id=m.id AND p.deleted_at IS NULL)
                          AND NOT EXISTS (SELECT 1 FROM users u WHERE u.photo_profile=m.url)
                        FROM media m WHERE m.id=?
                        """, Boolean.class, Timestamp.from(now.minus(grace)), media.id()));
                if (!unused) return false;
                // Keep this row locked across the bounded HTTP request. Saves lock the same row.
                try {
                    imageKit.delete(media.fileId());
                } catch (RestClientException ex) {
                    String reason = ex instanceof RestClientResponseException response
                            ? "ImageKit HTTP " + response.getStatusCode().value() : "ImageKit network error";
                    jdbc.update("""
                            UPDATE media SET cleanup_attempts=cleanup_attempts+1,last_cleanup_error=?,next_cleanup_at=?
                            WHERE id=?
                            """, reason, Timestamp.from(clock.instant().plus(retry)), media.id());
                    log.warn("Media cleanup failed for {}: {}; will retry", media.id(), reason);
                    return true;
                }
                jdbc.update("""
                        UPDATE media SET lifecycle_status='DELETED',deleted_at=?,cleanup_attempts=cleanup_attempts+1,
                            next_cleanup_at=NULL,last_cleanup_error=NULL WHERE id=?
                        """, Timestamp.from(clock.instant()), media.id());
                return true;
            });
            if (!Boolean.TRUE.equals(processed)) break;
        }
    }
}

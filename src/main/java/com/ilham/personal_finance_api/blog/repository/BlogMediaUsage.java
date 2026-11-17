package com.ilham.personal_finance_api.blog.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.jsoup.Jsoup;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Called inside the post transaction; media locks coordinate saves with cleanup. */
public class BlogMediaUsage {
    private final JdbcTemplate jdbc;

    public BlogMediaUsage(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void sync(UUID postId, String html, UUID featuredMediaId, Instant now) {
        var urls = new HashSet<String>();
        for (var image : Jsoup.parse(html).select("img[src]")) {
            String url = image.attr("src");
            urls.add(url);
            // ImageKit query transformations still refer to the uploaded original.
            urls.add(url.split("[?#]", 2)[0]);
        }
        var desired = new HashSet<UUID>();
        if (!urls.isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(urls.size(), "?"));
            desired.addAll(jdbc.queryForList("SELECT id FROM media WHERE url IN (" + placeholders + ")",
                    UUID.class, urls.toArray()));
        }
        if (featuredMediaId != null) desired.add(featuredMediaId);
        var affected = new TreeSet<UUID>(desired);
        affected.addAll(jdbc.queryForList("SELECT media_id FROM post_media WHERE post_id=?", UUID.class, postId));
        // Lock in the same order across articles sharing more than one image.
        for (UUID id : affected) {
            var states = jdbc.queryForList("SELECT lifecycle_status FROM media WHERE id=? FOR NO KEY UPDATE", String.class, id);
            if (desired.contains(id) && (states.isEmpty() || !states.getFirst().equals("ACTIVE"))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Referenced media is unavailable: " + id);
            }
        }
        jdbc.update("DELETE FROM post_media WHERE post_id=?", postId);
        for (UUID id : desired) jdbc.update("INSERT INTO post_media(post_id,media_id) VALUES (?,?)", postId, id);
        for (UUID id : affected) {
            if (desired.contains(id)) {
                jdbc.update("UPDATE media SET unused_since=NULL,next_cleanup_at=NULL,last_cleanup_error=NULL WHERE id=?", id);
            } else {
                jdbc.update("""
                        UPDATE media SET unused_since=COALESCE(unused_since,?),next_cleanup_at=NULL
                        WHERE id=? AND NOT EXISTS (SELECT 1 FROM post_media WHERE media_id=?)
                        """, Timestamp.from(now), id, id);
            }
        }
    }
}

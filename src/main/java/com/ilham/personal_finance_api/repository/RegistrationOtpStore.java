package com.ilham.personal_finance_api.repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import tools.jackson.databind.ObjectMapper;

/**
 * Semua state OTP registrasi di Redis. Setiap key punya TTL sehingga
 * state yang ditinggalkan akan hilang sendiri.
 */
@Repository
public class RegistrationOtpStore {

    private static final String PREFIX = "reg:";

    // INCR + PEXPIRE secara atomik: TTL hanya dipasang saat counter pertama kali dibuat
    private static final RedisScript<Long> INCREMENT_WITH_TTL = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]) "
                    + "if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
                    + "return count",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RegistrationOtpStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public static String pendingKey(String email) {
        return PREFIX + "pending:" + email;
    }

    public static String attemptsKey(String email) {
        return PREFIX + "attempts:" + email;
    }

    public static String sendsKey(String email) {
        return PREFIX + "sends:" + email;
    }

    public static String cooldownKey(String email) {
        return PREFIX + "resend-cd:" + email;
    }

    public static String lockKey(String email) {
        return PREFIX + "lock:" + email;
    }

    public void savePending(PendingRegistration pending, Duration ttl) {
        String json = objectMapper.writeValueAsString(pending);
        redisTemplate.opsForValue().set(pendingKey(pending.email()), json, ttl);
    }

    public Optional<PendingRegistration> findPending(String email) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(pendingKey(email)))
                .map(json -> objectMapper.readValue(json, PendingRegistration.class));
    }

    public long incrementAttempts(String email, Duration window) {
        return increment(attemptsKey(email), window);
    }

    public long incrementSends(String email, Duration window) {
        return increment(sendsKey(email), window);
    }

    public void startCooldown(String email, Duration cooldown) {
        redisTemplate.opsForValue().set(cooldownKey(email), "1", cooldown);
    }

    /** Sisa cooldown resend dalam detik, 0 jika tidak ada. */
    public long cooldownRemainingSeconds(String email) {
        return remainingSeconds(cooldownKey(email));
    }

    /** Sisa waktu kunci dalam detik, 0 jika tidak terkunci. */
    public long lockRemainingSeconds(String email) {
        return remainingSeconds(lockKey(email));
    }

    /** Kunci email dan buang semua state registrasi yang sedang berjalan. */
    public void lock(String email, Duration duration) {
        clear(email);
        redisTemplate.opsForValue().set(lockKey(email), "1", duration);
    }

    /** Hapus semua state registrasi kecuali kunci. */
    public void clear(String email) {
        redisTemplate.delete(List.of(
                pendingKey(email), attemptsKey(email), sendsKey(email), cooldownKey(email)));
    }

    private long increment(String key, Duration window) {
        Long count = redisTemplate.execute(
                INCREMENT_WITH_TTL, List.of(key), String.valueOf(window.toMillis()));
        return count == null ? 0 : count;
    }

    private long remainingSeconds(String key) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        // -2 = key tidak ada, -1 = tanpa TTL (seharusnya tidak terjadi)
        return ttl == null || ttl < 0 ? 0 : Math.max(ttl, 1);
    }
}

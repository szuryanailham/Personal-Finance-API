package com.ilham.personal_finance_api.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Aturan OTP registrasi (prefix {@code app.otp}).
 *
 * @param length         jumlah digit OTP
 * @param ttl            masa berlaku OTP dan data registrasi pending
 * @param maxAttempts    batas salah input OTP sebelum dikunci
 * @param maxSends       batas pengiriman OTP (sign-up + resend) sebelum dikunci
 * @param lockDuration   lama kunci setelah batas terlampaui
 * @param resendCooldown jeda minimum antar pengiriman OTP
 */
@ConfigurationProperties("app.otp")
public record OtpProperties(
        int length,
        Duration ttl,
        int maxAttempts,
        int maxSends,
        Duration lockDuration,
        Duration resendCooldown) {
}

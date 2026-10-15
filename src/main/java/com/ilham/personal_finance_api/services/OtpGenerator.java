package com.ilham.personal_finance_api.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

import com.ilham.personal_finance_api.config.OtpProperties;

@Component
public class OtpGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final int length;

    public OtpGenerator(OtpProperties properties) {
        this.length = properties.length();
    }

    public String generate() {
        StringBuilder otp = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            otp.append(RANDOM.nextInt(10));
        }
        return otp.toString();
    }

    public String hash(String otp) {
        return HexFormat.of().formatHex(sha256(otp));
    }

    // Perbandingan constant-time untuk mencegah timing attack
    public boolean matches(String otp, String expectedHash) {
        byte[] expected = HexFormat.of().parseHex(expectedHash);
        return MessageDigest.isEqual(sha256(otp), expected);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}

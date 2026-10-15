package com.ilham.personal_finance_api.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ilham.personal_finance_api.config.OtpProperties;

class OtpGeneratorTest {

    private final OtpGenerator generator = new OtpGenerator(new OtpProperties(
            6, Duration.ofMinutes(5), 3, 3, Duration.ofMinutes(5), Duration.ofSeconds(60)));

    @Test
    void generatesNumericOtpWithConfiguredLength() {
        for (int i = 0; i < 100; i++) {
            String otp = generator.generate();
            assertTrue(otp.matches("\\d{6}"), "OTP must be 6 digits but was " + otp);
        }
    }

    @Test
    void generatesDifferentOtpsAcrossCalls() {
        Set<String> otps = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            otps.add(generator.generate());
        }
        assertTrue(otps.size() > 1);
    }

    @Test
    void hashIsDeterministicAndDoesNotExposeOtp() {
        String hash = generator.hash("123456");

        assertEquals(hash, generator.hash("123456"));
        assertNotEquals("123456", hash);
    }

    @Test
    void matchesOnlyTheOriginalOtp() {
        String hash = generator.hash("123456");

        assertTrue(generator.matches("123456", hash));
        assertFalse(generator.matches("654321", hash));
    }
}

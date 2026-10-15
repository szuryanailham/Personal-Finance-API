package com.ilham.personal_finance_api.repository;

/**
 * Data registrasi yang menunggu verifikasi OTP, disimpan di Redis.
 * Password dan OTP hanya disimpan dalam bentuk hash.
 */
public record PendingRegistration(
        String firstName,
        String lastName,
        String email,
        String passwordHash,
        String otpHash) {

    public PendingRegistration withOtpHash(String newOtpHash) {
        return new PendingRegistration(firstName, lastName, email, passwordHash, newOtpHash);
    }
}

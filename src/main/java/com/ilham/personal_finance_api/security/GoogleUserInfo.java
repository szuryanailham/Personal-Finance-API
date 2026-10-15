package com.ilham.personal_finance_api.security;

/**
 * Data user dari ID token Google yang sudah terverifikasi.
 */
public record GoogleUserInfo(String email, String firstName, String lastName) {
}

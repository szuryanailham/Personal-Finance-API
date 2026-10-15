package com.ilham.personal_finance_api.security;

/**
 * Memverifikasi ID token dari Google Sign-In.
 */
public interface GoogleTokenVerifier {

    /**
     * @throws org.springframework.web.server.ResponseStatusException 401 jika token tidak valid
     */
    GoogleUserInfo verify(String idToken);
}

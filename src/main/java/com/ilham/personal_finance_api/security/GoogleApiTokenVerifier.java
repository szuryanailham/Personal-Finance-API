package com.ilham.personal_finance_api.security;

import java.io.IOException;
import java.security.GeneralSecurityException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.ilham.personal_finance_api.config.GoogleAuthProperties;

// Verifikasi signature (public key Google, di-cache), issuer, audience, dan expiry ID token
@Component
public class GoogleApiTokenVerifier implements GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleApiTokenVerifier.class);
    private static final String INVALID_TOKEN = "Invalid Google token";

    private final GoogleIdTokenVerifier verifier;

    public GoogleApiTokenVerifier(GoogleAuthProperties properties) {
        if (properties.clientIds().isEmpty()) {
            log.warn("app.google.client-ids is empty; all Google logins will be rejected");
        }
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(properties.clientIds())
                .build();
    }

    @Override
    public GoogleUserInfo verify(String idToken) {
        GoogleIdToken token = parseAndVerify(idToken);
        GoogleIdToken.Payload payload = token.getPayload();

        if (!Boolean.TRUE.equals(payload.getEmailVerified()) || payload.getEmail() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google email is not verified");
        }

        String email = payload.getEmail();
        return new GoogleUserInfo(
                email,
                firstNameOf(payload, email),
                (String) payload.getOrDefault("family_name", ""));
    }

    private GoogleIdToken parseAndVerify(String idToken) {
        try {
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_TOKEN);
            }
            return token;
        } catch (IllegalArgumentException e) {
            // Token bukan JWT yang valid secara format
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_TOKEN);
        } catch (GeneralSecurityException | IOException e) {
            log.error("Failed to verify Google ID token", e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to verify Google token");
        }
    }

    // Akun Google bisa tidak punya given_name; fallback ke name lalu bagian lokal email
    private static String firstNameOf(GoogleIdToken.Payload payload, String email) {
        Object givenName = payload.get("given_name");
        if (givenName instanceof String s && !s.isBlank()) {
            return s;
        }
        Object name = payload.get("name");
        if (name instanceof String s && !s.isBlank()) {
            return s;
        }
        return email.substring(0, email.indexOf('@'));
    }
}

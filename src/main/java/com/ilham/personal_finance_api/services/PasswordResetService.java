package com.ilham.personal_finance_api.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ilham.personal_finance_api.dto.ForgotPasswordRequest;
import com.ilham.personal_finance_api.dto.ResetPasswordRequest;
import com.ilham.personal_finance_api.entity.AuthProvider;
import com.ilham.personal_finance_api.repository.UserRepository;
import com.ilham.personal_finance_api.security.BCrypt;

@Service
public class PasswordResetService {
    private static final Duration TTL = Duration.ofMinutes(15);
    private final SecureRandom random = new SecureRandom();
    private final UserRepository users;
    private final PasswordResetMailSender mail;
    private final ValidationService validation;
    private final Clock clock;

    public PasswordResetService(UserRepository users, PasswordResetMailSender mail,
            ValidationService validation, Clock clock) {
        this.users = users;
        this.mail = mail;
        this.validation = validation;
        this.clock = clock;
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        validation.validate(request);
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        var user = users.findForPasswordResetByEmail(email).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Email is not registered"));
        if (user.getAuthProvider() != AuthProvider.PASSWORD) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Use Google account recovery to reset your Google password");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        user.setPasswordResetTokenHash(hash(token));
        user.setPasswordResetExpiresAt(clock.instant().plus(TTL).toEpochMilli());
        users.saveAndFlush(user);
        try {
            mail.send(user.getEmail(), user.getFirstName(), token);
        } catch (MailException e) {
            // Do not include SMTP details: they may contain the reset message.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Failed to send password reset email, please try again later");
        }
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        validation.validate(request);
        if (request.getNewPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must not exceed 72 UTF-8 bytes");
        }
        String hash = hash(request.getToken());
        var user = users.findForPasswordResetByHash(hash).orElseThrow(PasswordResetService::invalidToken);
        if (user.getAuthProvider() != AuthProvider.PASSWORD
                || !hash.equals(user.getPasswordResetTokenHash())
                || user.getPasswordResetExpiresAt() == null
                || user.getPasswordResetExpiresAt() <= clock.millis()) {
            throw invalidToken();
        }
        user.setPassword(BCrypt.hashpw(request.getNewPassword(), BCrypt.gensalt()));
        user.setPasswordResetTokenHash(null);
        user.setPasswordResetExpiresAt(null);
        user.setToken(null);
        user.setTokenExpiredAt(null);
        users.save(user);
    }

    private static ResponseStatusException invalidToken() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired password reset token");
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }
}

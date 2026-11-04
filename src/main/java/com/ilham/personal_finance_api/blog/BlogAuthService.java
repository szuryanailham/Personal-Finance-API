package com.ilham.personal_finance_api.blog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.ilham.personal_finance_api.security.BCrypt;
import static com.ilham.personal_finance_api.blog.BlogAuthModels.*;

@Service
@ConditionalOnProperty(name = "app.blog.enabled", havingValue = "true")
public class BlogAuthService {
    private final BlogStore store;
    private final Clock clock;
    private final String registrationKey;
    private final SecureRandom random = new SecureRandom();
    private static final Duration SESSION_TTL = Duration.ofHours(8);
    private static final String DUMMY_HASH = BCrypt.hashpw("dummy-login-password", BCrypt.gensalt(12));

    public BlogAuthService(BlogStore store, Clock clock,
            @Value("${app.blog.registration-key:}") String registrationKey) {
        this.store = store;
        this.clock = clock;
        this.registrationKey = registrationKey;
    }

    public UserResponse register(RegisterRequest request, String key) {
        if (registrationKey.isBlank() || key == null || !MessageDigest.isEqual(
                registrationKey.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Blog registration is not permitted");
        }
        passwordBytes(request.password());
        try {
            return store.create(request.displayName().strip(), normalize(request.email()),
                    BCrypt.hashpw(request.password(), BCrypt.gensalt(12)));
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
    }

    public LoginResponse login(LoginRequest request) {
        passwordBytes(request.password());
        var account = store.byEmail(normalize(request.email()));
        String passwordHash = account.isPresent() ? account.get().passwordHash() : DUMMY_HASH;
        boolean valid = BCrypt.checkpw(request.password(), passwordHash);
        if (!valid || account.isEmpty() || !"ACTIVE".equals(account.get().status())) throw unauthorized();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var now = clock.instant();
        var expires = now.plus(SESSION_TTL);
        store.issueSession(account.get().id(), hash(token), now, expires);
        return new LoginResponse(token, "Bearer", expires, store.profile(account.get()));
    }

    public UserResponse me(String authorization) {
        return store.profile(store.session(hash(bearer(authorization)), clock.instant()).orElseThrow(BlogAuthService::unauthorized));
    }

    public void logout(String authorization) {
        String token = bearer(authorization);
        me(authorization);
        store.revoke(hash(token));
    }

    private static String bearer(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) throw unauthorized();
        String token = authorization.substring(7);
        if (!token.matches("[A-Za-z0-9_-]{43}")) throw unauthorized();
        return token;
    }

    private static void passwordBytes(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must not exceed 72 UTF-8 bytes");
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials or session");
    }
}

package com.ilham.personal_finance_api.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ilham.personal_finance_api.AbstractIntegrationTest;
import com.ilham.personal_finance_api.entity.*;
import com.ilham.personal_finance_api.repository.UserRepository;
import com.ilham.personal_finance_api.security.BCrypt;
import com.ilham.personal_finance_api.services.*;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetControllerTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @MockitoBean PasswordResetMailSender mail;
    @MockitoBean OtpMailSender otpMail;
    @MockitoBean Clock clock;
    String email;
    final Instant now = Instant.parse("2026-11-01T00:00:00Z");

    @BeforeEach void setup() {
        email = UUID.randomUUID() + "@example.com";
        when(clock.instant()).thenReturn(now);
        when(clock.millis()).thenReturn(now.toEpochMilli());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    User account() {
        User user = new User();
        user.setEmail(email); user.setFirstName("Reset"); user.setLastName("Test");
        user.setPassword(BCrypt.hashpw("old password", BCrypt.gensalt()));
        user.setToken(UUID.randomUUID().toString());
        user.setTokenExpiredAt(System.currentTimeMillis() + 600000);
        return users.saveAndFlush(user);
    }

    org.springframework.test.web.servlet.ResultActions postJson(String path, Map<String,String> body) throws Exception {
        return mvc.perform(post("/api/auth/" + path).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    String issue() throws Exception {
        postJson("forgot-password", Map.of("email", email.toUpperCase(Locale.ROOT))).andExpect(status().isOk());
        var token = ArgumentCaptor.forClass(String.class);
        verify(mail, atLeastOnce()).send(eq(email), anyString(), token.capture());
        return token.getValue();
    }

    @Test void issuanceReplacementRollbackAndResetRevokesSession() throws Exception {
        User original = account();
        String first = issue();
        assertEquals(32, Base64.getUrlDecoder().decode(first).length);
        User stored = users.findByEmail(email).orElseThrow();
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(first.getBytes(StandardCharsets.UTF_8)));
        assertEquals(hash, stored.getPasswordResetTokenHash());
        assertEquals(now.plusSeconds(900).toEpochMilli(), stored.getPasswordResetExpiresAt());
        String second = issue(); assertNotEquals(first, second);
        postJson("reset-password", Map.of("token", first, "newPassword", "new password")).andExpect(status().isBadRequest());
        String previousHash = users.findByEmail(email).orElseThrow().getPasswordResetTokenHash();
        doThrow(new MailSendException("private mail contents")).when(mail).send(anyString(), anyString(), anyString());
        postJson("forgot-password", Map.of("email", email)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errors").value("Failed to send password reset email, please try again later"));
        assertEquals(previousHash, users.findByEmail(email).orElseThrow().getPasswordResetTokenHash());
        postJson("reset-password", Map.of("token", second, "newPassword", " new password ")).andExpect(status().isOk());
        stored = users.findByEmail(email).orElseThrow();
        assertTrue(BCrypt.checkpw(" new password ", stored.getPassword()));
        assertNull(stored.getPasswordResetTokenHash()); assertNull(stored.getPasswordResetExpiresAt());
        assertNull(stored.getToken()); assertNull(stored.getTokenExpiredAt());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + original.getToken())).andExpect(status().isUnauthorized());
        postJson("login", Map.of("email", email, "password", "old password")).andExpect(status().isUnauthorized());
        postJson("login", Map.of("email", email, "password", " new password ")).andExpect(status().isOk());
        postJson("reset-password", Map.of("token", second, "newPassword", "another")).andExpect(status().isBadRequest());
    }

    @Test void rejectsInvalidInputsUnknownGoogleAndExpiredTokens() throws Exception {
        postJson("forgot-password", Map.of("email", email)).andExpect(status().isNotFound());
        for (String invalid : List.of("", "bad", "x".repeat(101) + "@example.com"))
            postJson("forgot-password", Map.of("email", invalid)).andExpect(status().isBadRequest());
        User user = account(); user.setAuthProvider(AuthProvider.GOOGLE); users.saveAndFlush(user);
        postJson("forgot-password", Map.of("email", email)).andExpect(status().isBadRequest());
        user.setAuthProvider(AuthProvider.PASSWORD); users.saveAndFlush(user);
        String token = issue();
        for (String invalid : List.of("", "   ", "x".repeat(101), "x".repeat(73), "界".repeat(25)))
            postJson("reset-password", Map.of("token", token, "newPassword", invalid)).andExpect(status().isBadRequest());
        postJson("reset-password", Map.of("token", "unknown", "newPassword", "valid")).andExpect(status().isBadRequest());
        when(clock.millis()).thenReturn(now.plusSeconds(900).toEpochMilli());
        postJson("reset-password", Map.of("token", token, "newPassword", "valid")).andExpect(status().isBadRequest());
    }

    @Test void pendingRegistrationBecomesEligibleOnlyAfterOtpVerification() throws Exception {
        postJson("sign-up", Map.of("email", email, "firstName", "Test", "lastName", "User", "password", "password"))
                .andExpect(status().isAccepted());
        postJson("forgot-password", Map.of("email", email)).andExpect(status().isNotFound());
        var otp = ArgumentCaptor.forClass(String.class);
        verify(otpMail).send(eq(email), anyString(), otp.capture(), any());
        postJson("sign-up/verify", Map.of("email", email, "otp", otp.getValue())).andExpect(status().isCreated());
        issue();
    }

    @Test void acceptsExactly72Utf8BytesAndRejectsBlankToken() throws Exception {
        account(); String token = issue();
        postJson("reset-password", Map.of("token", " ", "newPassword", "valid")).andExpect(status().isBadRequest());
        postJson("reset-password", Map.of("newPassword", "valid")).andExpect(status().isBadRequest());
        String password = "界".repeat(24);
        postJson("reset-password", Map.of("token", token, "newPassword", password)).andExpect(status().isOk());
        assertTrue(BCrypt.checkpw(password, users.findByEmail(email).orElseThrow().getPassword()));
    }

    @Test void concurrentRedemptionHasExactlyOneSuccess() throws Exception {
        account(); String token = issue();
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        Callable<Integer> redeem = () -> {
            ready.countDown(); start.await();
            return postJson("reset-password", Map.of("token", token, "newPassword", "new password"))
                    .andReturn().getResponse().getStatus();
        };
        try {
            var a = executor.submit(redeem); var b = executor.submit(redeem);
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            assertEquals(List.of(200, 400), java.util.stream.Stream.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)).sorted().toList());
        } finally { start.countDown(); executor.shutdownNow(); }
    }
}

package com.ilham.personal_finance_api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ilham.personal_finance_api.config.OtpProperties;
import com.ilham.personal_finance_api.dto.GoogleLoginRequest;
import com.ilham.personal_finance_api.dto.RegisterUserRequest;
import com.ilham.personal_finance_api.entity.AuthProvider;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.exception.DataAlreadyExistedException;
import com.ilham.personal_finance_api.repository.*;
import com.ilham.personal_finance_api.security.*;

class AuthProviderTest {
    private final UserRepository users = mock(UserRepository.class);
    private final GoogleTokenVerifier verifier = mock(GoogleTokenVerifier.class);
    private final AccountService accounts = mock(AccountService.class);

    private AuthService auth() {
        AuthService service = new AuthService();
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "validationService", mock(ValidationService.class));
        ReflectionTestUtils.setField(service, "googleTokenVerifier", verifier);
        ReflectionTestUtils.setField(service, "accountService", accounts);
        when(verifier.verify("token")).thenReturn(new GoogleUserInfo("USER@example.com", "User", "Test"));
        return service;
    }

    private GoogleLoginRequest request() {
        GoogleLoginRequest request = new GoogleLoginRequest();
        request.setIdToken("token");
        return request;
    }

    @Test
    void googleAuthenticatesLegacyAccountWithoutChangingCredentialsOrProfile() {
        AuthService service = auth();
        User existing = new User();
        existing.setFirstName("Original");
        existing.setPassword("existing-password-hash");
        existing.setToken("old-token");
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(existing));

        var response = service.loginWithGoogle(request());

        assertNotNull(response.getToken());
        assertNotEquals("old-token", response.getToken());
        assertEquals(existing.getToken(), response.getToken());
        assertTrue(response.getExpiredAt() > System.currentTimeMillis());
        assertEquals(AuthProvider.PASSWORD, existing.getAuthProvider());
        assertEquals("existing-password-hash", existing.getPassword());
        assertEquals("Original", existing.getFirstName());
        verify(users).save(existing);
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(accounts);
    }

    @Test
    void invalidGoogleTokenCannotAuthenticateExistingAccount() {
        AuthService service = auth();
        when(verifier.verify("token")).thenThrow(new IllegalArgumentException("invalid token"));
        assertThrows(IllegalArgumentException.class, () -> service.loginWithGoogle(request()));
        verifyNoInteractions(users, accounts);
    }

    @Test
    void returningGoogleAccountCanSignIn() {
        AuthService service = auth();
        User existing = new User();
        existing.setAuthProvider(AuthProvider.GOOGLE);
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(existing));
        assertNotNull(service.loginWithGoogle(request()).getToken());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void newGoogleAccountRecordsProviderAndNormalizedEmail() {
        AuthService service = auth();
        when(users.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(users.saveAndFlush(any(User.class))).thenAnswer(call -> call.getArgument(0));
        assertNotNull(service.loginWithGoogle(request()).getToken());
        verify(users).saveAndFlush(argThat(user -> user.getAuthProvider() == AuthProvider.GOOGLE
                && user.getEmail().equals("user@example.com")));
    }

    @Test
    void manualRegistrationRejectsExistingGoogleEmailBeforeSendingOtp() {
        RegistrationOtpStore store = mock(RegistrationOtpStore.class);
        OtpMailSender mail = mock(OtpMailSender.class);
        RegistrationService service = new RegistrationService(users, mock(UserService.class),
                mock(ValidationService.class), store, mock(OtpGenerator.class), mail,
                new OtpProperties(6, Duration.ofMinutes(5), 3, 3, Duration.ofMinutes(5), Duration.ofSeconds(60)));
        when(users.existsByEmail("user@example.com")).thenReturn(true);
        RegisterUserRequest request = new RegisterUserRequest();
        request.setEmail("USER@example.com");
        assertThrows(DataAlreadyExistedException.class, () -> service.register(request));
        verifyNoInteractions(mail);
    }

    @Test
    void otpCompletionRejectsEmailRegisteredWhileOtpWasPending() {
        UserService service = new UserService();
        ReflectionTestUtils.setField(service, "userRepository", users);
        when(users.existsByEmail("user@example.com")).thenReturn(true);
        assertThrows(DataAlreadyExistedException.class, () -> service.createVerifiedUser(
                new PendingRegistration("User", "Test", "user@example.com", "hash", "otpHash")));
        verify(users, never()).saveAndFlush(any());
    }
}

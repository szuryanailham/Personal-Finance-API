package com.ilham.personal_finance_api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.ilham.personal_finance_api.AbstractIntegrationTest;
import com.ilham.personal_finance_api.dto.RegisterUserRequest;
import com.ilham.personal_finance_api.dto.WebResponse;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.repository.CategoryRepository;
import com.ilham.personal_finance_api.repository.RegistrationOtpStore;
import com.ilham.personal_finance_api.repository.TransactionRepository;
import com.ilham.personal_finance_api.repository.UserRepository;
import com.ilham.personal_finance_api.security.BCrypt;
import com.ilham.personal_finance_api.services.OtpMailSender;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
public class UserControllerTest extends AbstractIntegrationTest {

    private static final String EMAIL = "test@gmail.com";
    private static final String PASSWORD = "test12345";

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OtpMailSender otpMailSender;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
        redisTemplate.delete(redisTemplate.keys("reg:*"));
    }

    // ---------- sign-up ----------

    @Test
    void testRegisterSuccess() throws Exception {
        signUp(EMAIL)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.data.resendAvailableInSeconds").value(60));

        verify(otpMailSender).send(eq(EMAIL), anyString(), anyString(), any());
        assertFalse(userRepository.existsByEmail(EMAIL), "user must not be created before OTP is verified");

        Long ttl = redisTemplate.getExpire(RegistrationOtpStore.pendingKey(EMAIL), TimeUnit.SECONDS);
        assertTrue(ttl > 0 && ttl <= 300);
    }

    @Test
    void testRegisterDoesNotStoreOtpInPlainText() throws Exception {
        signUp(EMAIL).andExpect(status().isAccepted());

        String pending = redisTemplate.opsForValue().get(RegistrationOtpStore.pendingKey(EMAIL));
        assertNotNull(pending);
        assertFalse(pending.contains(capturedOtp(EMAIL)));
        assertFalse(pending.contains(PASSWORD));
    }

    @Test
    void testRegisterBadRequest() throws Exception {
        RegisterUserRequest request = new RegisterUserRequest();
        request.setFirstName("");
        request.setLastName("");
        request.setPassword("");

        mockMvc.perform(
                post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpectAll(status().isBadRequest())
                .andDo(result -> {
                    WebResponse<String> response = objectMapper.readValue(
                            result.getResponse().getContentAsString(),
                            new TypeReference<WebResponse<String>>() {});
                    assertNotNull(response.getErrors());
                });
    }

    @Test
    void testRegisterDuplicate() throws Exception {
        User user = new User();
        user.setFirstName("test");
        user.setLastName("test");
        user.setEmail(EMAIL);
        user.setPassword("rahasia");
        userRepository.save(user);

        signUp(EMAIL)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors").isNotEmpty());

        verify(otpMailSender, never()).send(anyString(), anyString(), anyString(), any());
    }

    @Test
    void testRegisterAgainWithinCooldownIsRejected() throws Exception {
        signUp(EMAIL).andExpect(status().isAccepted());

        signUp(EMAIL)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void testRegisterReturnsServiceUnavailableWhenMailFails() throws Exception {
        doThrow(new MailSendException("smtp down")).when(otpMailSender).send(anyString(), anyString(), anyString(), any());

        signUp(EMAIL).andExpect(status().isServiceUnavailable());

        assertFalse(redisTemplate.hasKey(RegistrationOtpStore.pendingKey(EMAIL)));
    }

    // ---------- verify ----------

    @Test
    void testVerifySuccessCreatesUser() throws Exception {
        signUp(EMAIL).andExpect(status().isAccepted());

        verifyOtp(EMAIL, capturedOtp(EMAIL))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.firstName").value("test"));

        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertTrue(BCrypt.checkpw(PASSWORD, user.getPassword()));
        assertTrue(redisTemplate.keys("reg:*").isEmpty(), "all registration keys must be cleared");
    }

    @Test
    void testVerifyNormalizesEmail() throws Exception {
        signUp("Test@Gmail.com").andExpect(status().isAccepted());

        verifyOtp(EMAIL, capturedOtp(EMAIL)).andExpect(status().isCreated());

        assertTrue(userRepository.existsByEmail(EMAIL));
    }

    @Test
    void testVerifyWrongOtpReturnsRemainingAttempts() throws Exception {
        signUp(EMAIL).andExpect(status().isAccepted());

        verifyOtp(EMAIL, wrongOtp(capturedOtp(EMAIL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").value("Invalid OTP. 2 attempt(s) remaining"));
    }

    @Test
    void testVerifyLocksAfterThreeFailedAttempts() throws Exception {
        signUp(EMAIL).andExpect(status().isAccepted());
        String otp = capturedOtp(EMAIL);

        verifyOtp(EMAIL, wrongOtp(otp)).andExpect(status().isBadRequest());
        verifyOtp(EMAIL, wrongOtp(otp)).andExpect(status().isBadRequest());
        verifyOtp(EMAIL, wrongOtp(otp))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        Long lockTtl = redisTemplate.getExpire(RegistrationOtpStore.lockKey(EMAIL), TimeUnit.SECONDS);
        assertTrue(lockTtl > 290 && lockTtl <= 300, "lock must last 5 minutes but was " + lockTtl);

        // OTP yang benar pun ditolak selama terkunci
        verifyOtp(EMAIL, otp).andExpect(status().isTooManyRequests());
        signUp(EMAIL).andExpect(status().isTooManyRequests());
        assertFalse(userRepository.existsByEmail(EMAIL));
    }

    @Test
    void testRegisterAgainAfterLockExpires() throws Exception {
        signUp(EMAIL).andExpect(status().isAccepted());
        String otp = capturedOtp(EMAIL);
        for (int i = 0; i < 3; i++) {
            verifyOtp(EMAIL, wrongOtp(otp));
        }

        // simulasi lock 5 menit sudah habis
        redisTemplate.delete(RegistrationOtpStore.lockKey(EMAIL));

        signUp(EMAIL).andExpect(status().isAccepted());
        verifyOtp(EMAIL, capturedOtp(EMAIL)).andExpect(status().isCreated());
    }

    @Test
    void testVerifyExpiredOtp() throws Exception {
        signUp(EMAIL).andExpect(status().isAccepted());
        String otp = capturedOtp(EMAIL);

        // simulasi OTP sudah kedaluwarsa
        redisTemplate.delete(RegistrationOtpStore.pendingKey(EMAIL));

        verifyOtp(EMAIL, otp).andExpect(status().isBadRequest());
    }

    @Test
    void testVerifyInvalidOtpFormat() throws Exception {
        verifyOtp(EMAIL, "12ab").andExpect(status().isBadRequest());
    }

    @Test
    void testVerifyWhenEmailRegisteredMeanwhile() throws Exception {
        signUp(EMAIL).andExpect(status().isAccepted());
        String otp = capturedOtp(EMAIL);

        User user = new User();
        user.setFirstName("other");
        user.setLastName("other");
        user.setEmail(EMAIL);
        user.setPassword("rahasia");
        userRepository.save(user);

        verifyOtp(EMAIL, otp).andExpect(status().isConflict());
    }

    // ---------- resend ----------

    @Test
    void testResendBeforeCooldownIsRejected() throws Exception {
        signUp(EMAIL).andExpect(status().isAccepted());

        resend(EMAIL)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void testResendAfterCooldownReplacesOtp() throws Exception {
        signUp(EMAIL).andExpect(status().isAccepted());
        String oldOtp = capturedOtp(EMAIL);

        redisTemplate.delete(RegistrationOtpStore.cooldownKey(EMAIL));
        resend(EMAIL).andExpect(status().isAccepted());
        String newOtp = capturedOtp(EMAIL);

        if (!oldOtp.equals(newOtp)) {
            verifyOtp(EMAIL, oldOtp).andExpect(status().isBadRequest());
        }
        verifyOtp(EMAIL, newOtp).andExpect(status().isCreated());
    }

    @Test
    void testResendLimitTriggersLock() throws Exception {
        signUp(EMAIL).andExpect(status().isAccepted());

        for (int i = 0; i < 2; i++) {
            redisTemplate.delete(RegistrationOtpStore.cooldownKey(EMAIL));
            resend(EMAIL).andExpect(status().isAccepted());
        }

        redisTemplate.delete(RegistrationOtpStore.cooldownKey(EMAIL));
        resend(EMAIL)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        assertTrue(redisTemplate.hasKey(RegistrationOtpStore.lockKey(EMAIL)));
    }

    @Test
    void testResendWithoutPendingRegistration() throws Exception {
        resend(EMAIL).andExpect(status().isBadRequest());
    }

    // ---------- current user ----------

    @Test
    void testGetCurrentUserSuccess() throws Exception {
        User user = new User();
        user.setFirstName("Ilham");
        user.setLastName("Suryana");
        user.setEmail(EMAIL);
        user.setPassword(BCrypt.hashpw(PASSWORD, BCrypt.gensalt()));
        user.setToken("test");
        user.setTokenExpiredAt(System.currentTimeMillis() + 1000000);
        userRepository.save(user);

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.data.firstName").value("Ilham"))
                .andExpect(jsonPath("$.data.lastName").value("Suryana"))
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.token").doesNotExist());
    }

    @Test
    void testGetCurrentUserWithoutToken() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testGetCurrentUserInvalidToken() throws Exception {
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer salah"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private ResultActions signUp(String email) throws Exception {
        RegisterUserRequest request = new RegisterUserRequest();
        request.setFirstName("test");
        request.setLastName("test");
        request.setEmail(email);
        request.setPassword(PASSWORD);
        return postJson("/api/auth/sign-up", request);
    }

    private ResultActions verifyOtp(String email, String otp) throws Exception {
        return postJson("/api/auth/sign-up/verify", Map.of("email", email, "otp", otp));
    }

    private ResultActions resend(String email) throws Exception {
        return postJson("/api/auth/sign-up/resend", Map.of("email", email));
    }

    private ResultActions postJson(String path, Object body) throws Exception {
        return mockMvc.perform(
                post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)));
    }

    /** OTP terakhir yang dikirim ke email tersebut. */
    private String capturedOtp(String email) {
        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        verify(otpMailSender, atLeastOnce()).send(eq(email), anyString(), otpCaptor.capture(), any());
        List<String> otps = otpCaptor.getAllValues();
        return otps.get(otps.size() - 1);
    }

    private static String wrongOtp(String otp) {
        String wrong = otp.equals("000000") ? "111111" : "000000";
        assertEquals(6, wrong.length());
        return wrong;
    }
}

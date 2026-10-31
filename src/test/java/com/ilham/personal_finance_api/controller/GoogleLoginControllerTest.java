package com.ilham.personal_finance_api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.server.ResponseStatusException;

import com.ilham.personal_finance_api.AbstractIntegrationTest;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.repository.CategoryRepository;
import com.ilham.personal_finance_api.repository.TransactionRepository;
import com.ilham.personal_finance_api.repository.UserRepository;
import com.ilham.personal_finance_api.security.BCrypt;
import com.ilham.personal_finance_api.security.GoogleTokenVerifier;
import com.ilham.personal_finance_api.security.GoogleUserInfo;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
public class GoogleLoginControllerTest extends AbstractIntegrationTest {

    private static final String EMAIL = "google-user@gmail.com";
    private static final String ID_TOKEN = "valid-google-id-token";

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GoogleTokenVerifier googleTokenVerifier;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void testGoogleLoginCreatesNewUser() throws Exception {
        when(googleTokenVerifier.verify(ID_TOKEN)).thenReturn(new GoogleUserInfo(EMAIL, "Budi", "Santoso"));

        googleLogin(ID_TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.expiredAt").isNumber());

        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertEquals("Budi", user.getFirstName());
        assertEquals("Santoso", user.getLastName());
        assertTrue(user.getToken() != null && !user.getToken().isBlank());
    }

    @Test
    void testGoogleLoginReusesLegacyAccountAndPreservesPasswordLogin() throws Exception {
        String passwordHash = BCrypt.hashpw("test12345", BCrypt.gensalt());
        User existing = new User();
        existing.setFirstName("Existing");
        existing.setLastName("User");
        existing.setEmail(EMAIL);
        existing.setPassword(passwordHash);
        userRepository.save(existing);

        when(googleTokenVerifier.verify(ID_TOKEN)).thenReturn(new GoogleUserInfo(EMAIL, "Budi", "Santoso"));

        googleLogin(ID_TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty());

        assertEquals(1, userRepository.count());
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertEquals("Existing", user.getFirstName(), "existing profile must not be overwritten");
        assertEquals(passwordHash, user.getPassword(), "existing password must stay usable");
        assertEquals(existing.getId(), user.getId());
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + user.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(EMAIL));
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", EMAIL, "password", "test12345"))))
                .andExpect(status().isOk());
    }

    @Test
    void testGoogleLoginTokenAuthenticatesApi() throws Exception {
        when(googleTokenVerifier.verify(ID_TOKEN)).thenReturn(new GoogleUserInfo(EMAIL, "Budi", ""));

        googleLogin(ID_TOKEN).andExpect(status().isOk());

        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + user.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(EMAIL));
    }

    @Test
    void testGoogleLoginInvalidToken() throws Exception {
        when(googleTokenVerifier.verify(anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google token"));

        googleLogin("forged-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors").value("Invalid Google token"));

        assertFalse(userRepository.existsByEmail(EMAIL));
    }

    @Test
    void testGoogleLoginBlankToken() throws Exception {
        googleLogin("")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").value("idToken is required"));

        verify(googleTokenVerifier, never()).verify(anyString());
    }

    private ResultActions googleLogin(String idToken) throws Exception {
        return mockMvc.perform(
                post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("idToken", idToken))));
    }
}

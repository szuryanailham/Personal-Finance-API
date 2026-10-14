package com.ilham.personal_finance_api.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.ilham.personal_finance_api.AbstractIntegrationTest;
import com.ilham.personal_finance_api.dto.TransactionType;
import com.ilham.personal_finance_api.entity.Category;
import com.ilham.personal_finance_api.entity.Transaction;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.repository.CategoryRepository;
import com.ilham.personal_finance_api.repository.TransactionRepository;
import com.ilham.personal_finance_api.repository.UserRepository;
import com.ilham.personal_finance_api.security.BCrypt;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
public class BulkDeleteTransactionControllerTest extends AbstractIntegrationTest {

private static final String BULK_URL = "/api/transaction/bulk";

@Autowired
private MockMvc mockMvc;

@Autowired
private ObjectMapper objectMapper;

@Autowired
private TransactionRepository transactionRepository;

@Autowired
private UserRepository userRepository;

@Autowired
private CategoryRepository categoryRepository;

private User user;

private User otherUser;

private Category gift;

@BeforeEach
void setUp() {
    transactionRepository.deleteAll();
    categoryRepository.deleteAll();
    userRepository.deleteAll();

    user = createUser("test@gmail.com", "test");
    otherUser = createUser("testb@gmail.com", "testB");
    gift = createCategory(user);
}

// [HELPER]

private User createUser(String email, String token) {
    User newUser = new User();
    newUser.setFirstName("test");
    newUser.setLastName("test");
    newUser.setEmail(email);
    newUser.setPassword(BCrypt.hashpw("test", BCrypt.gensalt()));
    newUser.setToken(token);
    newUser.setTokenExpiredAt(System.currentTimeMillis() + 1000000);
    return userRepository.save(newUser);
}

private Category createCategory(User owner) {
    Category newCategory = new Category();
    newCategory.setName("Gift");
    newCategory.setType(TransactionType.EXPENSE.name());
    newCategory.setUser(owner);
    return categoryRepository.save(newCategory);
}

private Transaction createTransaction(String code, User owner, boolean isDeleted) {
    Transaction transaction = new Transaction();
    transaction.setTransactionCode(code);
    transaction.setTransactionName("Hadiah");
    transaction.setCategory(gift);
    transaction.setUser(owner);
    transaction.setAmount(new BigDecimal("50000"));
    transaction.setTransactionDate(LocalDateTime.of(2026, 9, 26, 0, 0));
    transaction.setDeleted(isDeleted);
    return transactionRepository.save(transaction);
}

private boolean isDeleted(String code) {
    return transactionRepository.findAll().stream()
        .filter(transaction -> transaction.getTransactionCode().equals(code))
        .findFirst()
        .orElseThrow()
        .isDeleted();
}

private ResultActions bulkDelete(String token, Object codes) throws Exception {
    return mockMvc.perform(delete(BULK_URL)
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("transactionCodes", codes))));
}

// [TEST] success

@Test
void bulkDeleteAllActiveTransactions() throws Exception {
    createTransaction("TRX-001", user, false);
    createTransaction("TRX-002", user, false);

    bulkDelete("test", List.of("TRX-001", "TRX-002"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("2 transactions deleted successfully"))
        .andExpect(jsonPath("$.data.deletedCount").value(2))
        .andExpect(jsonPath("$.data.deletedCodes.length()").value(2))
        .andExpect(jsonPath("$.data.skippedCodes.length()").value(0));

    assertTrue(isDeleted("TRX-001"));
    assertTrue(isDeleted("TRX-002"));
}

@Test
void bulkDeleteSkipsNotFoundAndAlreadyDeletedCodes() throws Exception {
    createTransaction("TRX-001", user, false);
    createTransaction("TRX-002", user, true);

    bulkDelete("test", List.of("TRX-001", "TRX-002", "TRX-404"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.deletedCount").value(1))
        .andExpect(jsonPath("$.data.deletedCodes[0]").value("TRX-001"))
        .andExpect(jsonPath("$.data.skippedCodes.length()").value(2))
        .andExpect(jsonPath("$.data.skippedCodes[0]").value("TRX-002"))
        .andExpect(jsonPath("$.data.skippedCodes[1]").value("TRX-404"));

    assertTrue(isDeleted("TRX-001"));
}

@Test
void bulkDeleteSkipsOtherUsersTransactions() throws Exception {
    createTransaction("TRX-001", otherUser, false);

    bulkDelete("test", List.of("TRX-001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.deletedCount").value(0))
        .andExpect(jsonPath("$.data.skippedCodes[0]").value("TRX-001"));

    assertFalse(isDeleted("TRX-001"));
}

@Test
void bulkDeleteCountsDuplicateCodesOnce() throws Exception {
    createTransaction("TRX-001", user, false);

    bulkDelete("test", List.of("TRX-001", "TRX-001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.deletedCount").value(1))
        .andExpect(jsonPath("$.data.deletedCodes.length()").value(1));
}

// [TEST] failure

@Test
void bulkDeleteRejectsEmptyList() throws Exception {
    bulkDelete("test", List.of()).andExpect(status().isBadRequest());
}

@Test
void bulkDeleteRejectsBlankCode() throws Exception {
    bulkDelete("test", List.of(" ")).andExpect(status().isBadRequest());
}

@Test
void bulkDeleteRequiresAuthentication() throws Exception {
    createTransaction("TRX-001", user, false);

    bulkDelete("invalid-token", List.of("TRX-001")).andExpect(status().isUnauthorized());

    assertFalse(isDeleted("TRX-001"));
}
}

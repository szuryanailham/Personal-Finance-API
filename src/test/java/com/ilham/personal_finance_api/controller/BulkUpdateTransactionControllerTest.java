package com.ilham.personal_finance_api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
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
public class BulkUpdateTransactionControllerTest extends AbstractIntegrationTest {

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

private Category salary;

@BeforeEach
void setUp() {
    transactionRepository.deleteAll();
    categoryRepository.deleteAll();
    userRepository.deleteAll();

    user = createUser("test@gmail.com", "test");
    otherUser = createUser("testb@gmail.com", "testB");
    gift = createCategory("Gift", TransactionType.EXPENSE, user, false);
    salary = createCategory("Salary", TransactionType.INCOME, user, false);
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

private Category createCategory(String name, TransactionType type, User owner, boolean isDeleted) {
    Category newCategory = new Category();
    newCategory.setName(name);
    newCategory.setType(type.name());
    newCategory.setUser(owner);
    newCategory.setDeleted(isDeleted);
    return categoryRepository.save(newCategory);
}

private Transaction createTransaction(String code, User owner, boolean isDeleted) {
    Transaction transaction = new Transaction();
    transaction.setTransactionCode(code);
    transaction.setTransactionName("Hadiah");
    transaction.setDescription("Ulang tahun");
    transaction.setCategory(gift);
    transaction.setUser(owner);
    transaction.setAmount(new BigDecimal("50000"));
    transaction.setTransactionDate(LocalDateTime.of(2026, 9, 26, 0, 0));
    transaction.setDeleted(isDeleted);
    return transactionRepository.save(transaction);
}

private Transaction findByCode(String code) {
    return transactionRepository.findAll().stream()
        .filter(transaction -> transaction.getTransactionCode().equals(code))
        .findFirst()
        .orElseThrow();
}

private ResultActions bulkUpdate(String token, Map<String, Object> body) throws Exception {
    return mockMvc.perform(patch(BULK_URL)
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body)));
}

private Map<String, Object> request(List<String> codes, Map<String, Object> fields) {
    Map<String, Object> body = new HashMap<>(fields);
    body.put("transactionCodes", codes);
    return body;
}

// [TEST] success

@Test
void bulkUpdateAppliesSameFieldsToAllCodes() throws Exception {
    createTransaction("TRX-001", user, false);
    createTransaction("TRX-002", user, false);

    bulkUpdate("test", request(List.of("TRX-001", "TRX-002"), Map.of(
        "transactionName", "Gaji",
        "categoryId", salary.getId().toString(),
        "date", "2026-10-01",
        "description", "Gaji Oktober"
    )))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("2 transactions updated successfully"))
        .andExpect(jsonPath("$.updatedCount").value(2))
        .andExpect(jsonPath("$.errors").isEmpty())
        .andExpect(jsonPath("$.paging").isEmpty())
        .andExpect(jsonPath("$.data").doesNotExist());

    for (String code : List.of("TRX-001", "TRX-002")) {
        Transaction transaction = findByCode(code);
        assertEquals("Gaji", transaction.getTransactionName());
        assertEquals("Gaji Oktober", transaction.getDescription());
        assertEquals(salary.getId(), transaction.getCategory().getId());
        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0), transaction.getTransactionDate());
        assertEquals(0, new BigDecimal("50000").compareTo(transaction.getAmount()));
    }
}

@Test
void bulkUpdateChangesOnlyProvidedFields() throws Exception {
    createTransaction("TRX-001", user, false);

    bulkUpdate("test", request(List.of("TRX-001"), Map.of("description", "Baru")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.updatedCount").value(1));

    Transaction transaction = findByCode("TRX-001");
    assertEquals("Baru", transaction.getDescription());
    assertEquals("Hadiah", transaction.getTransactionName());
    assertEquals(gift.getId(), transaction.getCategory().getId());
    assertEquals(LocalDateTime.of(2026, 9, 26, 0, 0), transaction.getTransactionDate());
}

@Test
void bulkUpdateDeduplicatesCodes() throws Exception {
    createTransaction("TRX-001", user, false);

    bulkUpdate("test", request(List.of("TRX-001", "TRX-001"), Map.of("transactionName", "Baru")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.updatedCount").value(1));
}

// [TEST] failure (all-or-nothing)

@Test
void bulkUpdateRejectsWhenAnyCodeNotFound() throws Exception {
    createTransaction("TRX-001", user, false);

    bulkUpdate("test", request(List.of("TRX-001", "TRX-404"), Map.of("transactionName", "Baru")))
        .andExpect(status().isNotFound());

    assertEquals("Hadiah", findByCode("TRX-001").getTransactionName());
}

@Test
void bulkUpdateRejectsDeletedTransaction() throws Exception {
    createTransaction("TRX-001", user, true);

    bulkUpdate("test", request(List.of("TRX-001"), Map.of("transactionName", "Baru")))
        .andExpect(status().isNotFound());
}

@Test
void bulkUpdateRejectsOtherUsersTransaction() throws Exception {
    createTransaction("TRX-001", otherUser, false);

    bulkUpdate("test", request(List.of("TRX-001"), Map.of("transactionName", "Baru")))
        .andExpect(status().isNotFound());

    assertEquals("Hadiah", findByCode("TRX-001").getTransactionName());
}

@Test
void bulkUpdateRejectsDeletedCategory() throws Exception {
    createTransaction("TRX-001", user, false);
    Category deleted = createCategory("Old", TransactionType.EXPENSE, user, true);

    bulkUpdate("test", request(List.of("TRX-001"), Map.of("categoryId", deleted.getId().toString())))
        .andExpect(status().isNotFound());
}

@Test
void bulkUpdateRejectsOtherUsersCategory() throws Exception {
    createTransaction("TRX-001", user, false);
    Category foreign = createCategory("Foreign", TransactionType.EXPENSE, otherUser, false);

    bulkUpdate("test", request(List.of("TRX-001"), Map.of("categoryId", foreign.getId().toString())))
        .andExpect(status().isNotFound());

    assertEquals(gift.getId(), findByCode("TRX-001").getCategory().getId());
}

@Test
void bulkUpdateRejectsWhenNoFieldProvided() throws Exception {
    createTransaction("TRX-001", user, false);

    bulkUpdate("test", request(List.of("TRX-001"), Map.of()))
        .andExpect(status().isBadRequest());
}

@Test
void bulkUpdateRejectsEmptyCodes() throws Exception {
    bulkUpdate("test", request(List.of(), Map.of("transactionName", "Baru")))
        .andExpect(status().isBadRequest());
}

@Test
void bulkUpdateRejectsBlankCode() throws Exception {
    bulkUpdate("test", request(List.of(" "), Map.of("transactionName", "Baru")))
        .andExpect(status().isBadRequest());
}

@Test
void bulkUpdateRequiresAuthentication() throws Exception {
    createTransaction("TRX-001", user, false);

    bulkUpdate("invalid-token", request(List.of("TRX-001"), Map.of("transactionName", "Baru")))
        .andExpect(status().isUnauthorized());

    assertEquals("Hadiah", findByCode("TRX-001").getTransactionName());
}
}

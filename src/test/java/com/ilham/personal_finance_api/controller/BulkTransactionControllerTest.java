package com.ilham.personal_finance_api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
public class BulkTransactionControllerTest extends AbstractIntegrationTest {

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
    gift = createCategory("Gift", TransactionType.EXPENSE, user, false);
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

// item sesuai bentuk baris hasil preview import
private Map<String, Object> item(String name, UUID categoryId, String date, Object amount) {
    Map<String, Object> item = new HashMap<>();
    item.put("name", name);
    item.put("category", "Gift");
    item.put("categoryId", categoryId);
    item.put("categoryType", "EXPENSE");
    item.put("date", date);
    item.put("amount", amount);
    return item;
}

private Map<String, Object> validItem() {
    return item("Hadiah wisuda", gift.getId(), "2026-09-26", 500000);
}

private ResultActions bulkSave(List<Map<String, Object>> items) throws Exception {
    return mockMvc.perform(post(BULK_URL)
        .header("Authorization", "Bearer test")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("transactions", items))));
}

// [TEST] success

@Test
void bulkSaveAllTransactions() throws Exception {
    bulkSave(List.of(validItem(), validItem(), validItem()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("3 transactions saved successfully"))
        .andExpect(jsonPath("$.data.length()").value(3))
        .andExpect(jsonPath("$.data[0].transactionName").value("Hadiah wisuda"))
        .andExpect(jsonPath("$.data[0].amount").value(500000))
        .andExpect(jsonPath("$.data[0].date").value("2026-09-26"))
        .andExpect(jsonPath("$.data[0].category.id").value(gift.getId().toString()))
        .andExpect(jsonPath("$.data[0].category.type").value("EXPENSE"));

    List<Transaction> saved = transactionRepository.findAll();
    assertEquals(3, saved.size());
    assertEquals(3, saved.stream().map(Transaction::getTransactionCode).distinct().count());
}

@Test
void bulkSaveGeneratesSequentialCodesAfterExistingOnes() throws Exception {
    bulkSave(List.of(validItem())).andExpect(status().isOk());
    String firstCode = transactionRepository.findAll().get(0).getTransactionCode();
    String prefix = firstCode.substring(0, firstCode.lastIndexOf('-') + 1);

    bulkSave(List.of(validItem(), validItem()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].transactionCode").value(prefix + "002"))
        .andExpect(jsonPath("$.data[1].transactionCode").value(prefix + "003"));
}

// [TEST] category validation

@Test
void bulkSaveRejectsUnknownCategoryAndSavesNothing() throws Exception {
    UUID unknownId = UUID.randomUUID();

    bulkSave(List.of(validItem(), item("Kopi", unknownId, "2026-09-26", 35000)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errors").value("Category not found: " + unknownId));

    assertEquals(0, transactionRepository.count());
}

@Test
void bulkSaveRejectsCategoryOfOtherUser() throws Exception {
    Category otherCategory = createCategory("Gift", TransactionType.EXPENSE, otherUser, false);

    bulkSave(List.of(item("Kopi", otherCategory.getId(), "2026-09-26", 35000)))
        .andExpect(status().isNotFound());

    assertEquals(0, transactionRepository.count());
}

@Test
void bulkSaveRejectsDeletedCategory() throws Exception {
    Category deleted = createCategory("Old", TransactionType.EXPENSE, user, true);

    bulkSave(List.of(item("Kopi", deleted.getId(), "2026-09-26", 35000)))
        .andExpect(status().isNotFound());

    assertEquals(0, transactionRepository.count());
}

// [TEST] request validation

@Test
void bulkSaveRejectsEmptyList() throws Exception {
    bulkSave(List.of())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors").value("Transactions must not be empty"));
}

@Test
void bulkSaveRejectsNonPositiveAmount() throws Exception {
    bulkSave(List.of(validItem(), item("Kopi", gift.getId(), "2026-09-26", 0)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors").value("Amount must be greater than 0"));

    assertEquals(0, transactionRepository.count());
}

@Test
void bulkSaveRejectsBlankName() throws Exception {
    bulkSave(List.of(item(" ", gift.getId(), "2026-09-26", 35000)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors").value("Name is required"));
}

@Test
void bulkSaveRejectsMissingDate() throws Exception {
    bulkSave(List.of(item("Kopi", gift.getId(), null, 35000)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors").value("Date is required"));
}

@Test
void bulkSaveUnauthorized() throws Exception {
    mockMvc.perform(post(BULK_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("transactions", List.of(validItem())))))
        .andExpect(status().isUnauthorized());
}
}

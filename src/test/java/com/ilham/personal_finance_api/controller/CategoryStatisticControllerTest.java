package com.ilham.personal_finance_api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.ilham.personal_finance_api.AbstractIntegrationTest;
import com.ilham.personal_finance_api.dto.CategoryStatisticResponse;
import com.ilham.personal_finance_api.dto.CategoryStatisticResponse.CategoryStatisticItemResponse;
import com.ilham.personal_finance_api.dto.TransactionType;
import com.ilham.personal_finance_api.dto.WebResponse;
import com.ilham.personal_finance_api.entity.Category;
import com.ilham.personal_finance_api.entity.Transaction;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.repository.CategoryRepository;
import com.ilham.personal_finance_api.repository.TransactionRepository;
import com.ilham.personal_finance_api.repository.UserRepository;
import com.ilham.personal_finance_api.security.BCrypt;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
public class CategoryStatisticControllerTest extends AbstractIntegrationTest {

@TestConfiguration
static class FixedClockConfiguration {
    // Senin, 12 Oktober 2026 -> default bulan ini 1..31 Oktober
    @Bean
    @Primary
    Clock fixedClock() {
        ZoneId zone = ZoneId.of("Asia/Jakarta");
        return Clock.fixed(LocalDateTime.of(2026, 10, 12, 10, 0).atZone(zone).toInstant(), zone);
    }
}

private static final String STATISTIC_URL = "/api/categories/statistic";

@Autowired
private TransactionRepository transactionRepository;

@Autowired
private ObjectMapper objectMapper;

@Autowired
private MockMvc mockMvc;

@Autowired
private UserRepository userRepository;

@Autowired
private CategoryRepository categoryRepository;

private Category makanan;

private Category transport;

@BeforeEach
void setUp() {
    transactionRepository.deleteAll();
    categoryRepository.deleteAll();
    userRepository.deleteAll();

    User user = createUser("test@gmail.com", "test");
    User otherUser = createUser("testb@gmail.com", "testB");
    createUser("empty@gmail.com", "empty");

    Category gaji = createCategory("Gaji", TransactionType.INCOME, user);
    makanan = createCategory("Makanan", TransactionType.EXPENSE, user);
    transport = createCategory("Transport", TransactionType.EXPENSE, user);
    Category makananB = createCategory("Makanan B", TransactionType.EXPENSE, otherUser);

    // Tahun lalu
    createTransaction(user, makanan, "1000000", LocalDateTime.of(2025, 12, 31, 23, 59, 59), false);
    // Semester pertama 2026
    createTransaction(user, transport, "200000", LocalDateTime.of(2026, 1, 1, 0, 0), false);
    // Di luar Oktober
    createTransaction(user, transport, "30000", LocalDateTime.of(2026, 9, 30, 23, 59, 59), false);
    createTransaction(user, makanan, "80000", LocalDateTime.of(2026, 11, 1, 0, 0), false);
    // Oktober
    createTransaction(user, makanan, "400000", LocalDateTime.of(2026, 10, 11, 23, 59, 59), false);
    createTransaction(user, makanan, "150000", LocalDateTime.of(2026, 10, 13, 9, 0), false);
    createTransaction(user, transport, "70000", LocalDateTime.of(2026, 10, 19, 0, 0), false);
    createTransaction(user, makanan, "250000", LocalDateTime.of(2026, 10, 31, 23, 59, 59), false);
    createTransaction(user, makanan, "100000", LocalDateTime.of(2026, 10, 12, 0, 0), false);
    createTransaction(user, transport, "50000", LocalDateTime.of(2026, 10, 12, 23, 59, 59), false);
    // Income, terhapus & user lain (tidak boleh dihitung)
    createTransaction(user, gaji, "3000000", LocalDateTime.of(2026, 10, 12, 8, 0), false);
    createTransaction(user, makanan, "999000", LocalDateTime.of(2026, 10, 12, 12, 0), true);
    createTransaction(otherUser, makananB, "7000000", LocalDateTime.of(2026, 10, 12, 9, 0), false);
}

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

private Category createCategory(String name, TransactionType type, User owner) {
    Category newCategory = new Category();
    newCategory.setName(name);
    newCategory.setType(type.name());
    newCategory.setUser(owner);
    return categoryRepository.save(newCategory);
}

private void createTransaction(User owner, Category category, String amount, LocalDateTime date, boolean isDeleted) {
    Transaction newTransaction = new Transaction();
    newTransaction.setTransactionName("test");
    newTransaction.setTransactionCode("TRX-" + UUID.randomUUID());
    newTransaction.setCategory(category);
    newTransaction.setUser(owner);
    newTransaction.setAmount(new BigDecimal(amount));
    newTransaction.setDescription("this is for test");
    newTransaction.setTransactionDate(date);
    newTransaction.setDeleted(isDeleted);
    transactionRepository.save(newTransaction);
}

private MockHttpServletRequestBuilder statisticRequest(String period, String startDate, String endDate, String token) {
    MockHttpServletRequestBuilder request = get(STATISTIC_URL)
        .accept(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer " + token);
    if (period != null) {
        request = request.param("period", period);
    }
    if (startDate != null) {
        request = request.param("startDate", startDate);
    }
    if (endDate != null) {
        request = request.param("endDate", endDate);
    }
    return request;
}

private CategoryStatisticResponse requestStatistic(String period, String startDate, String endDate, String token) throws Exception {
    MvcResult result = mockMvc.perform(statisticRequest(period, startDate, endDate, token))
        .andExpect(status().isOk())
        .andReturn();

    WebResponse<CategoryStatisticResponse> response = objectMapper.readValue(
        result.getResponse().getContentAsString(),
        new TypeReference<WebResponse<CategoryStatisticResponse>>() {}
    );
    assertNull(response.getErrors());
    assertEquals("Expense statistics retrieved successfully", response.getMessage());
    assertNotNull(response.getData());
    return response.getData();
}

private void assertBadRequest(String period, String startDate, String endDate, String expectedError) throws Exception {
    MvcResult result = mockMvc.perform(statisticRequest(period, startDate, endDate, "test"))
        .andExpect(status().isBadRequest())
        .andReturn();

    WebResponse<Object> response = readError(result);
    assertNull(response.getData());
    assertEquals(expectedError, response.getErrors());
}

private WebResponse<Object> readError(MvcResult result) throws Exception {
    return objectMapper.readValue(
        result.getResponse().getContentAsString(),
        new TypeReference<WebResponse<Object>>() {}
    );
}

private void assertAmount(String expected, BigDecimal actual) {
    assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
}

private void assertPeriod(CategoryStatisticResponse data, LocalDate startDate, LocalDate endDate) {
    assertEquals(startDate, data.getPeriod().getStartDate());
    assertEquals(endDate, data.getPeriod().getEndDate());
}

private void assertItem(CategoryStatisticItemResponse item, Category category, String amount, String percentage, long count) {
    assertEquals(category.getId(), item.getCategoryId());
    assertEquals(category.getName(), item.getCategoryName());
    assertAmount(amount, item.getTotalAmount());
    assertAmount(percentage, item.getPercentage());
    assertEquals(count, item.getTransactionCount());
}


// TC-01
@Test
void testStatisticDefaultsToMonthlyExpenseOnly() throws Exception {
    CategoryStatisticResponse data = requestStatistic(null, null, null, "test");

    assertPeriod(data, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
    // 09-30 23:59:59, 11-01 00:00, income, terhapus & user lain tidak masuk
    assertAmount("1020000", data.getTotalExpense());

    List<CategoryStatisticItemResponse> categories = data.getCategories();
    assertEquals(2, categories.size());
    assertItem(categories.get(0), makanan, "900000", "88.24", 4);
    assertItem(categories.get(1), transport, "120000", "11.76", 2);
}

// TC-02
@Test
void testStatisticMonthlyExplicitIgnoresDates() throws Exception {
    CategoryStatisticResponse data = requestStatistic("monthly", "2026-01-01", "2026-01-31", "test");

    assertPeriod(data, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
    assertAmount("1020000", data.getTotalExpense());
}

// TC-03
@Test
void testStatisticYearly() throws Exception {
    CategoryStatisticResponse data = requestStatistic("YEARLY", null, null, "test");

    assertPeriod(data, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
    // 2025-12-31 23:59:59 tidak masuk
    assertAmount("1330000", data.getTotalExpense());

    List<CategoryStatisticItemResponse> categories = data.getCategories();
    assertEquals(2, categories.size());
    assertItem(categories.get(0), makanan, "980000", "73.68", 5);
    assertItem(categories.get(1), transport, "350000", "26.32", 4);
}

// TC-04
@Test
void testStatisticWeeklyIsLastSevenDaysIncludingToday() throws Exception {
    CategoryStatisticResponse data = requestStatistic("WEEKLY", null, null, "test");

    assertPeriod(data, LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 12));
    // 10-11 & 10-12 masuk, 10-13 tidak masuk
    assertAmount("550000", data.getTotalExpense());

    List<CategoryStatisticItemResponse> categories = data.getCategories();
    assertEquals(2, categories.size());
    assertItem(categories.get(0), makanan, "500000", "90.91", 2);
    assertItem(categories.get(1), transport, "50000", "9.09", 1);
}

// TC-05
@Test
void testStatisticToday() throws Exception {
    CategoryStatisticResponse data = requestStatistic("TODAY", null, null, "test");

    assertPeriod(data, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12));
    // 10-12 00:00 dan 10-12 23:59:59 masuk
    assertAmount("150000", data.getTotalExpense());

    List<CategoryStatisticItemResponse> categories = data.getCategories();
    assertEquals(2, categories.size());
    assertItem(categories.get(0), makanan, "100000", "66.67", 1);
    assertItem(categories.get(1), transport, "50000", "33.33", 1);
}

// TC-06
@Test
void testStatisticCustomRangeIncludesStartBoundary() throws Exception {
    CategoryStatisticResponse data = requestStatistic("CUSTOM", "2026-01-01", "2026-06-30", "test");

    assertPeriod(data, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
    // 2025-12-31 23:59:59 tidak masuk, 2026-01-01 00:00 masuk
    assertAmount("200000", data.getTotalExpense());

    List<CategoryStatisticItemResponse> categories = data.getCategories();
    assertEquals(1, categories.size());
    assertItem(categories.get(0), transport, "200000", "100", 1);
}

// TC-07
@Test
void testStatisticCustomRangeIncludesWholeEndDate() throws Exception {
    CategoryStatisticResponse data = requestStatistic("CUSTOM", "2026-10-01", "2026-10-11", "test");

    // 10-11 23:59:59 masuk, 10-12 00:00 tidak masuk
    assertAmount("400000", data.getTotalExpense());

    List<CategoryStatisticItemResponse> categories = data.getCategories();
    assertEquals(1, categories.size());
    assertItem(categories.get(0), makanan, "400000", "100", 1);
}

// TC-08
@Test
void testStatisticEmptyWhenNoTransactions() throws Exception {
    CategoryStatisticResponse data = requestStatistic(null, null, null, "empty");

    assertPeriod(data, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
    assertAmount("0", data.getTotalExpense());
    assertTrue(data.getCategories().isEmpty());
}

// TC-09
@Test
void testStatisticBadRequestCustomWithoutDates() throws Exception {
    assertBadRequest("CUSTOM", null, null, "startDate and endDate are required for CUSTOM period");
}

// TC-10
@Test
void testStatisticBadRequestCustomOnlyStartDate() throws Exception {
    assertBadRequest("CUSTOM", "2026-01-01", null, "startDate and endDate are required for CUSTOM period");
}

// TC-11
@Test
void testStatisticBadRequestCustomOnlyEndDate() throws Exception {
    assertBadRequest("CUSTOM", null, "2026-06-30", "startDate and endDate are required for CUSTOM period");
}

// TC-12
@Test
void testStatisticBadRequestStartAfterEnd() throws Exception {
    assertBadRequest("CUSTOM", "2026-06-30", "2026-01-01", "startDate must be before or equal to endDate");
}

// TC-13
@Test
void testStatisticBadRequestInvalidDateFormat() throws Exception {
    assertBadRequest("CUSTOM", "2026-13-01", "2026-06-30", "Parameter 'startDate' has invalid value: 2026-13-01");
}

// TC-14
@Test
void testStatisticBadRequestInvalidPeriod() throws Exception {
    assertBadRequest("DAILY", null, null, "Invalid period: DAILY");
}

// TC-15
@Test
void testStatisticUnauthorizedWithoutHeader() throws Exception {
    mockMvc.perform(
        get(STATISTIC_URL)
            .accept(MediaType.APPLICATION_JSON)
    )
    .andExpect(status().isUnauthorized())
    .andDo(result -> {
        WebResponse<Object> response = readError(result);
        assertNull(response.getData());
        assertNotNull(response.getErrors());
    });
}
}

package com.ilham.personal_finance_api.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

import com.ilham.personal_finance_api.AbstractIntegrationTest;
import com.ilham.personal_finance_api.dto.TransactionStatisticResponse;
import com.ilham.personal_finance_api.dto.TransactionStatisticResponse.TransactionPeriodItemResponse;
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
public class TransactionStatisticControllerTest extends AbstractIntegrationTest {

@TestConfiguration
static class FixedClockConfiguration {
    @Bean
    @Primary
    Clock fixedClock() {
        ZoneId zone = ZoneId.of("Asia/Jakarta");
        return Clock.fixed(LocalDateTime.of(2026, 9, 24, 10, 0).atZone(zone).toInstant(), zone);
    }
}

private static final String STATISTIC_URL = "/api/transaction/statistic";
private static final int DAYS_IN_SEPTEMBER = 30;
private static final int DAYS_IN_WEEK = 7;

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

private User user;

private Category gaji;

private Category makanan;

private Category investasi;

@BeforeEach
void setUp() {
    transactionRepository.deleteAll();
    categoryRepository.deleteAll();
    userRepository.deleteAll();

    user = createUser("test@gmail.com", "test");
    User otherUser = createUser("testb@gmail.com", "testB");

    gaji = createCategory("Gaji", TransactionType.INCOME, user);
    makanan = createCategory("Makanan", TransactionType.EXPENSE, user);
    investasi = createCategory("Investasi", TransactionType.SAVING, user);
    Category gajiB = createCategory("Gaji B", TransactionType.INCOME, otherUser);

    // Di luar bulan (batas bawah & atas)
    createTransaction(user, makanan, "300000", LocalDateTime.of(2026, 8, 31, 23, 59, 59), false);
    createTransaction(user, gaji, "5000000", LocalDateTime.of(2026, 10, 1, 0, 0), false);
    // September
    createTransaction(user, gaji, "12000000", LocalDateTime.of(2026, 9, 1, 0, 0), false);
    createTransaction(user, investasi, "1500000", LocalDateTime.of(2026, 9, 3, 8, 0), false);
    createTransaction(user, gaji, "200000", LocalDateTime.of(2026, 9, 5, 9, 0), false);
    createTransaction(user, makanan, "500000", LocalDateTime.of(2026, 9, 5, 12, 0), false);
    createTransaction(user, makanan, "250000", LocalDateTime.of(2026, 9, 5, 18, 0), false);
    createTransaction(user, makanan, "999000", LocalDateTime.of(2026, 9, 12, 10, 0), true);
    createTransaction(user, makanan, "400000", LocalDateTime.of(2026, 9, 17, 23, 59, 59), false);
    createTransaction(user, gaji, "1000000", LocalDateTime.of(2026, 9, 18, 0, 0), false);
    createTransaction(user, makanan, "150000", LocalDateTime.of(2026, 9, 20, 13, 0), false);
    createTransaction(user, gaji, "50000", LocalDateTime.of(2026, 9, 24, 23, 59, 59), false);
    createTransaction(user, gaji, "100000", LocalDateTime.of(2026, 9, 30, 23, 59, 59), false);
    // User lain
    createTransaction(otherUser, gajiB, "7000000", LocalDateTime.of(2026, 9, 20, 9, 0), false);
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

private TransactionStatisticResponse readData(MvcResult result) throws Exception {
    WebResponse<TransactionStatisticResponse> response = objectMapper.readValue(
        result.getResponse().getContentAsString(),
        new TypeReference<WebResponse<TransactionStatisticResponse>>() {}
    );
    assertNull(response.getErrors());
    assertNotNull(response.getData());
    return response.getData();
}

private TransactionPeriodItemResponse itemAt(TransactionStatisticResponse data, LocalDate date) {
    return data.getItems().stream()
        .filter(item -> date.equals(item.getDate()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Item tanggal " + date + " tidak ada"));
}

private void assertItem(TransactionStatisticResponse data, LocalDate date, String income, String expense) {
    TransactionPeriodItemResponse item = itemAt(data, date);
    assertEquals(0, new BigDecimal(income).compareTo(item.getIncome()), "income " + date);
    assertEquals(0, new BigDecimal(expense).compareTo(item.getExpense()), "expense " + date);
}

private BigDecimal totalIncome(List<TransactionPeriodItemResponse> items) {
    return items.stream().map(TransactionPeriodItemResponse::getIncome).reduce(BigDecimal.ZERO, BigDecimal::add);
}

private BigDecimal totalExpense(List<TransactionPeriodItemResponse> items) {
    return items.stream().map(TransactionPeriodItemResponse::getExpense).reduce(BigDecimal.ZERO, BigDecimal::add);
}

private void assertConsecutiveDates(List<TransactionPeriodItemResponse> items, LocalDate startDate) {
    for (int i = 0; i < items.size(); i++) {
        assertEquals(startDate.plusDays(i), items.get(i).getDate(), "urutan tanggal index " + i);
    }
}


// TC-01
@Test
void testGetStatisticMonthlyMetadata() throws Exception {
    mockMvc.perform(
        get(STATISTIC_URL)
            .param("periode", "Monthly")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        TransactionStatisticResponse data = readData(result);
        assertEquals("Monthly", data.getPeriod());
        assertEquals(LocalDate.of(2026, 9, 1), data.getStartDate());
        assertEquals(LocalDate.of(2026, 9, 30), data.getEndDate());
        assertEquals(DAYS_IN_SEPTEMBER, data.getItems().size());
    });
}

// TC-02
@Test
void testGetStatisticMonthlyItemsOrderedAndComplete() throws Exception {
    mockMvc.perform(
        get(STATISTIC_URL)
            .param("periode", "Monthly")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        TransactionStatisticResponse data = readData(result);
        // satu item per hari, urut naik, tanpa tanggal bolong atau duplikat
        assertConsecutiveDates(data.getItems(), LocalDate.of(2026, 9, 1));
    });
}

// TC-03
@Test
void testGetStatisticMonthlyTotals() throws Exception {
    mockMvc.perform(
        get(STATISTIC_URL)
            .param("periode", "Monthly")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        TransactionStatisticResponse data = readData(result);
        // income: 12.000.000 + 200.000 + 1.000.000 + 50.000 + 100.000
        assertEquals(0, new BigDecimal("13350000").compareTo(totalIncome(data.getItems())));
        // expense: 750.000 + 400.000 + 150.000
        assertEquals(0, new BigDecimal("1300000").compareTo(totalExpense(data.getItems())));
    });
}

// TC-04
@Test
void testGetStatisticMonthlyBoundary() throws Exception {
    mockMvc.perform(
        get(STATISTIC_URL)
            .param("periode", "Monthly")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        TransactionStatisticResponse data = readData(result);
        // 09-01 00:00 dan 09-30 23:59:59 masuk; 08-31 dan 10-01 tidak muncul
        assertItem(data, LocalDate.of(2026, 9, 1), "12000000", "0");
        assertItem(data, LocalDate.of(2026, 9, 30), "100000", "0");
    });
}

// TC-05
@Test
void testGetStatisticAggregateSameDay() throws Exception {
    mockMvc.perform(
        get(STATISTIC_URL)
            .param("periode", "Monthly")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        TransactionStatisticResponse data = readData(result);
        // 3 transaksi tgl 5 (beda jam) digabung jadi 1 item: expense 500.000 + 250.000
        assertItem(data, LocalDate.of(2026, 9, 5), "200000", "750000");
    });
}

// TC-06
@Test
void testGetStatisticExcludeSaving() throws Exception {
    mockMvc.perform(
        get(STATISTIC_URL)
            .param("periode", "Monthly")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        TransactionStatisticResponse data = readData(result);
        // saving 1.500.000 tidak dihitung sebagai income maupun expense
        assertItem(data, LocalDate.of(2026, 9, 3), "0", "0");
    });
}

// TC-07
@Test
void testGetStatisticExcludeSoftDeletedTransaction() throws Exception {
    mockMvc.perform(
        get(STATISTIC_URL)
            .param("periode", "Monthly")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        TransactionStatisticResponse data = readData(result);
        assertItem(data, LocalDate.of(2026, 9, 12), "0", "0");
    });
}

// TC-08
@Test
void testGetStatisticExcludeOtherUserTransaction() throws Exception {
    mockMvc.perform(
        get(STATISTIC_URL)
            .param("periode", "Monthly")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        TransactionStatisticResponse data = readData(result);
        // income 7.000.000 milik user B di tanggal yang sama tidak ikut
        assertItem(data, LocalDate.of(2026, 9, 20), "0", "150000");
    });
}

// TC-09
@Test
void testGetStatisticFillMissingDatesWithZero() throws Exception {
    mockMvc.perform(
        get(STATISTIC_URL)
            .param("periode", "Monthly")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        TransactionStatisticResponse data = readData(result);
        assertItem(data, LocalDate.of(2026, 9, 2), "0", "0");
        assertItem(data, LocalDate.of(2026, 9, 25), "0", "0");
        data.getItems().forEach(item -> {
            assertNotNull(item.getIncome(), "income null di " + item.getDate());
            assertNotNull(item.getExpense(), "expense null di " + item.getDate());
        });
    });
}

// TC-10
@Test
void testGetStatisticWeekly() throws Exception {
    mockMvc.perform(
        get(STATISTIC_URL)
            .param("periode", "Weekly")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        TransactionStatisticResponse data = readData(result);
        assertEquals("Weekly", data.getPeriod());
        assertEquals(LocalDate.of(2026, 9, 18), data.getStartDate());
        assertEquals(LocalDate.of(2026, 9, 24), data.getEndDate());
        assertEquals(DAYS_IN_WEEK, data.getItems().size());
        assertConsecutiveDates(data.getItems(), LocalDate.of(2026, 9, 18));
        // income 1.000.000 + 50.000, expense 150.000
        assertEquals(0, new BigDecimal("1050000").compareTo(totalIncome(data.getItems())));
        assertEquals(0, new BigDecimal("150000").compareTo(totalExpense(data.getItems())));
    });
}

// TC-11
@Test
void testGetStatisticWeeklyBoundary() throws Exception {
    mockMvc.perform(
        get(STATISTIC_URL)
            .param("periode", "Weekly")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        TransactionStatisticResponse data = readData(result);
        // 09-17 23:59:59 (400.000) tidak masuk; 09-18 00:00 dan 09-24 23:59:59 masuk
        assertItem(data, LocalDate.of(2026, 9, 18), "1000000", "0");
        assertItem(data, LocalDate.of(2026, 9, 24), "50000", "0");
    });
}

// TC-12
@Test
void testGetStatisticDefaultMonthlyWithoutParam() throws Exception {
    mockMvc.perform(
        get(STATISTIC_URL)
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        TransactionStatisticResponse data = readData(result);
        assertEquals("Monthly", data.getPeriod());
        assertEquals(LocalDate.of(2026, 9, 1), data.getStartDate());
        assertEquals(DAYS_IN_SEPTEMBER, data.getItems().size());
    });
}

// TC-13
@Test
void testGetStatisticDefaultMonthlyWithEmptyParam() throws Exception {
    mockMvc.perform(
        get(STATISTIC_URL)
            .param("periode", "")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        TransactionStatisticResponse data = readData(result);
        assertEquals("Monthly", data.getPeriod());
        assertEquals(DAYS_IN_SEPTEMBER, data.getItems().size());
    });
}

// TC-14
@Test
void testGetStatisticNoTransaction() throws Exception {
    transactionRepository.deleteAll();

    mockMvc.perform(
        get(STATISTIC_URL)
            .param("periode", "Monthly")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        TransactionStatisticResponse data = readData(result);
        assertEquals(DAYS_IN_SEPTEMBER, data.getItems().size());
        assertEquals(0, BigDecimal.ZERO.compareTo(totalIncome(data.getItems())));
        assertEquals(0, BigDecimal.ZERO.compareTo(totalExpense(data.getItems())));
    });
}

// TC-15
@Test
void testGetStatisticDecimalAmount() throws Exception {
    transactionRepository.deleteAll();
    createTransaction(user, gaji, "100.50", LocalDateTime.of(2026, 9, 10, 9, 0), false);
    createTransaction(user, gaji, "200.25", LocalDateTime.of(2026, 9, 10, 15, 0), false);
    createTransaction(user, makanan, "10.10", LocalDateTime.of(2026, 9, 10, 20, 0), false);

    mockMvc.perform(
        get(STATISTIC_URL)
            .param("periode", "Monthly")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        TransactionStatisticResponse data = readData(result);
        assertItem(data, LocalDate.of(2026, 9, 10), "300.75", "10.10");
    });
}

// TC-16
@Test
void testGetStatisticJsonFormat() throws Exception {
    mockMvc.perform(
        get(STATISTIC_URL)
            .param("periode", "Weekly")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.errors").doesNotExist())
    .andExpect(jsonPath("$.data.period").value("Weekly"))
    .andExpect(jsonPath("$.data.startDate").value("2026-09-18"))
    .andExpect(jsonPath("$.data.endDate").value("2026-09-24"))
    .andExpect(jsonPath("$.data.items", hasSize(DAYS_IN_WEEK)))
    .andExpect(jsonPath("$.data.items[0].date").value("2026-09-18"))
    .andExpect(jsonPath("$.data.items[0].income").isNumber())
    .andExpect(jsonPath("$.data.items[0].expense").isNumber())
    .andExpect(jsonPath("$.data.items[6].date").value("2026-09-24"));
}

// TC-17
@Test
void testGetStatisticBadRequestInvalidPeriode() throws Exception {
    mockMvc.perform(
        get(STATISTIC_URL)
            .param("periode", "Yearly")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isBadRequest())
    .andDo(result -> {
        WebResponse<String> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<String>>() {}
        );
        assertEquals("periode is not valid", response.getErrors());
        assertNull(response.getData());
    });
}

// TC-18
@Test
void testGetStatisticBadRequestLowercasePeriode() throws Exception {
    // periode case-sensitive: hanya "Monthly" / "Weekly"
    mockMvc.perform(
        get(STATISTIC_URL)
            .param("periode", "monthly")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isBadRequest())
    .andDo(result -> {
        WebResponse<String> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<String>>() {}
        );
        assertEquals("periode is not valid", response.getErrors());
    });
}

// TC-19
@Test
void testGetStatisticUnauthorizedWithoutHeader() throws Exception {
    mockMvc.perform(
        get(STATISTIC_URL)
            .param("periode", "Monthly")
            .accept(MediaType.APPLICATION_JSON)
    )
    .andExpect(status().isUnauthorized())
    .andDo(result -> {
        WebResponse<String> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<String>>() {}
        );
        assertEquals("Unauthorized", response.getErrors());
    });
}

// TC-20
@Test
void testGetStatisticUnauthorizedInvalidToken() throws Exception {
    mockMvc.perform(
        get(STATISTIC_URL)
            .param("periode", "Monthly")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer invalid-token")
    )
    .andExpect(status().isUnauthorized())
    .andDo(result -> {
        WebResponse<String> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<String>>() {}
        );
        assertEquals("Unauthorized", response.getErrors());
    });
}

}

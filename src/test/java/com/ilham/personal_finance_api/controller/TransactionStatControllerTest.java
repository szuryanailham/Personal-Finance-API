package com.ilham.personal_finance_api.controller;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.ilham.personal_finance_api.AbstractIntegrationTest;
import com.ilham.personal_finance_api.dto.TransactionStateResponse;
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
public class TransactionStatControllerTest extends AbstractIntegrationTest {

// "Hari ini" dikunci ke 2026-09-24 (Asia/Jakarta) agar periode YEARLY/MONTHLY/WEEKLY deterministik
@TestConfiguration
static class FixedClockConfiguration {
    @Bean
    @Primary
    Clock fixedClock() {
        ZoneId zone = ZoneId.of("Asia/Jakarta");
        return Clock.fixed(LocalDateTime.of(2026, 9, 24, 10, 0).atZone(zone).toInstant(), zone);
    }
}

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

private Category tagihan;

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
    tagihan = createCategory("Tagihan", TransactionType.EXPENSE, user);
    investasi = createCategory("Investasi", TransactionType.SAVING, user);
    Category gajiB = createCategory("Gaji B", TransactionType.INCOME, otherUser);

    // Agustus
    createTransaction(user, gaji, "10000000", LocalDateTime.of(2026, 8, 1, 9, 0), false);
    createTransaction(user, tagihan, "2000000", LocalDateTime.of(2026, 8, 15, 12, 0), false);
    createTransaction(user, investasi, "1000000", LocalDateTime.of(2026, 8, 20, 10, 0), false);
    createTransaction(user, makanan, "300000", LocalDateTime.of(2026, 8, 31, 23, 59, 59), false);
    // September
    createTransaction(user, gaji, "12000000", LocalDateTime.of(2026, 9, 1, 0, 0), false);
    createTransaction(user, investasi, "1500000", LocalDateTime.of(2026, 9, 3, 8, 0), false);
    createTransaction(user, makanan, "500000", LocalDateTime.of(2026, 9, 5, 12, 0), false);
    createTransaction(user, tagihan, "1500000", LocalDateTime.of(2026, 9, 10, 10, 0), false);
    createTransaction(user, makanan, "999000", LocalDateTime.of(2026, 9, 12, 10, 0), true);
    createTransaction(user, gaji, "100000", LocalDateTime.of(2026, 9, 30, 23, 59, 59), false);
    // Oktober
    createTransaction(user, gaji, "5000000", LocalDateTime.of(2026, 10, 1, 0, 0), false);
    // User lain
    createTransaction(otherUser, gajiB, "7000000", LocalDateTime.of(2026, 9, 2, 9, 0), false);
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

// params: pasangan name, value
private MockHttpServletRequestBuilder statRequest(String... params) {
    MockHttpServletRequestBuilder request = get("/api/transaction/stat")
        .accept(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer test");
    for (int i = 0; i < params.length; i += 2) {
        request.param(params[i], params[i + 1]);
    }
    return request;
}

private TransactionStateResponse getStatOk(String... params) throws Exception {
    String content = mockMvc.perform(statRequest(params))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    WebResponse<TransactionStateResponse> response = objectMapper.readValue(
        content,
        new TypeReference<WebResponse<TransactionStateResponse>>() {}
    );
    assertNull(response.getErrors());
    assertNotNull(response.getData());
    return response.getData();
}

private String getStatError(ResultMatcher expectedStatus, MockHttpServletRequestBuilder request) throws Exception {
    String content = mockMvc.perform(request)
        .andExpect(expectedStatus)
        .andReturn().getResponse().getContentAsString();

    WebResponse<String> response = objectMapper.readValue(
        content,
        new TypeReference<WebResponse<String>>() {}
    );
    assertNotNull(response.getErrors());
    return response.getErrors();
}

private void assertStatistic(TransactionStateResponse.Statistic statistic, String amount, String changePercentage) {
    assertEquals(0, new BigDecimal(amount).compareTo(statistic.getAmount()), "amount");
    assertEquals(0, new BigDecimal(changePercentage).compareTo(statistic.getChangePercentage()), "changePercentage");
}

// Sep 2026 vs Agu 2026
private void assertSeptemberVsAugust(TransactionStateResponse data) {
    assertStatistic(data.getTotalIncome(), "12100000", "21.00");
    assertStatistic(data.getTotalExpense(), "2000000", "-13.04");
    assertStatistic(data.getTotalSaving(), "1500000", "50.00");
    assertStatistic(data.getTotalBalance(), "8600000", "28.36");
}


// ===================== type: default / MONTHLY =====================

@Test
void testGetStatDefaultTypeIsMonthly() throws Exception {
    assertSeptemberVsAugust(getStatOk());
}

@Test
void testGetStatDefaultTypeWithBlankType() throws Exception {
    assertSeptemberVsAugust(getStatOk("type", ""));
}

@Test
void testGetStatMonthly() throws Exception {
    assertSeptemberVsAugust(getStatOk("type", "MONTHLY"));
}

@Test
void testGetStatTypeCaseInsensitive() throws Exception {
    assertSeptemberVsAugust(getStatOk("type", "monthly"));
    assertSeptemberVsAugust(getStatOk("type", " Monthly "));
}

@Test
void testGetStatMonthlyIgnoresStartDateAndEndDate() throws Exception {
    // startDate/endDate hanya dipakai untuk CUSTOM
    assertSeptemberVsAugust(getStatOk(
        "type", "MONTHLY",
        "startDate", "2026-12-01",
        "endDate", "2026-12-31"
    ));
}

@Test
void testGetStatWithoutTypeIgnoresStartDateAndEndDate() throws Exception {
    assertSeptemberVsAugust(getStatOk("startDate", "2026-12-01", "endDate", "2026-12-31"));
}

@Test
void testGetStatMonthlyPeriodBoundary() throws Exception {
    TransactionStateResponse data = getStatOk("type", "MONTHLY");

    // 09-01 00:00 & 09-30 23:59:59 masuk, 10-01 00:00 tidak masuk
    assertStatistic(data.getTotalIncome(), "12100000", "21.00");
    // 08-31 23:59:59 tidak masuk September, tapi masuk pembanding Agustus (2.300.000)
    assertStatistic(data.getTotalExpense(), "2000000", "-13.04");
}

@Test
void testGetStatExcludeSoftDeletedTransaction() throws Exception {
    // transaksi 999.000 yang di-soft delete tidak ikut dihitung
    assertStatistic(getStatOk().getTotalExpense(), "2000000", "-13.04");
}

@Test
void testGetStatExcludeOtherUserTransaction() throws Exception {
    // income 7.000.000 milik user B tidak ikut dihitung
    assertStatistic(getStatOk().getTotalIncome(), "12100000", "21.00");
}

@Test
void testGetStatSavingReducesBalance() throws Exception {
    // 12.100.000 - 2.000.000 - 1.500.000
    assertStatistic(getStatOk().getTotalBalance(), "8600000", "28.36");
}

@Test
void testGetStatNegativePercentage() throws Exception {
    transactionRepository.deleteAll();
    createTransaction(user, investasi, "1000000", LocalDateTime.of(2026, 8, 10, 10, 0), false);
    createTransaction(user, investasi, "750000", LocalDateTime.of(2026, 9, 10, 10, 0), false);

    assertStatistic(getStatOk().getTotalSaving(), "750000", "-25.00");
}

@Test
void testGetStatPercentageRoundDown() throws Exception {
    transactionRepository.deleteAll();
    createTransaction(user, gaji, "300000", LocalDateTime.of(2026, 8, 10, 10, 0), false);
    createTransaction(user, gaji, "400000", LocalDateTime.of(2026, 9, 10, 10, 0), false);

    assertStatistic(getStatOk().getTotalIncome(), "400000", "33.33");
}

@Test
void testGetStatPercentageRoundUp() throws Exception {
    transactionRepository.deleteAll();
    createTransaction(user, gaji, "600000", LocalDateTime.of(2026, 8, 10, 10, 0), false);
    createTransaction(user, gaji, "700000", LocalDateTime.of(2026, 9, 10, 10, 0), false);

    assertStatistic(getStatOk().getTotalIncome(), "700000", "16.67");
}

@Test
void testGetStatNegativeBalanceShownAsDeficit() throws Exception {
    transactionRepository.deleteAll();
    createTransaction(user, gaji, "1000000", LocalDateTime.of(2026, 9, 10, 10, 0), false);
    createTransaction(user, tagihan, "3000000", LocalDateTime.of(2026, 9, 11, 10, 0), false);

    // balance defisit -2.000.000 tetap ditampilkan negatif, bulan lalu kosong
    assertStatistic(getStatOk().getTotalBalance(), "-2000000", "-100.00");
}

@Test
void testGetStatPreviousBalanceNegative() throws Exception {
    transactionRepository.deleteAll();
    createTransaction(user, gaji, "1000000", LocalDateTime.of(2026, 8, 10, 10, 0), false);
    createTransaction(user, tagihan, "3000000", LocalDateTime.of(2026, 8, 11, 10, 0), false);
    createTransaction(user, gaji, "1000000", LocalDateTime.of(2026, 9, 10, 10, 0), false);

    // (1.000.000 - (-2.000.000)) / |-2.000.000|
    assertStatistic(getStatOk().getTotalBalance(), "1000000", "150.00");
}

@Test
void testGetStatDecimalAmount() throws Exception {
    transactionRepository.deleteAll();
    createTransaction(user, gaji, "100.50", LocalDateTime.of(2026, 9, 10, 10, 0), false);
    createTransaction(user, gaji, "200.25", LocalDateTime.of(2026, 9, 11, 10, 0), false);

    assertStatistic(getStatOk().getTotalIncome(), "300.75", "100.00");
}

@Test
void testGetStatCategoryTypeChangeAffectsHistory() throws Exception {
    investasi.setType(TransactionType.EXPENSE.name());
    categoryRepository.save(investasi);

    TransactionStateResponse data = getStatOk();

    // transaksi Investasi 1.500.000 (Sep) & 1.000.000 (Agu) pindah dari saving ke expense
    assertStatistic(data.getTotalExpense(), "3500000", "6.06");
    assertStatistic(data.getTotalSaving(), "0", "0.00");
}

@Test
void testGetStatMonthlyNoTransaction() throws Exception {
    transactionRepository.deleteAll();

    TransactionStateResponse data = getStatOk();

    assertStatistic(data.getTotalIncome(), "0", "0.00");
    assertStatistic(data.getTotalExpense(), "0", "0.00");
    assertStatistic(data.getTotalSaving(), "0", "0.00");
    assertStatistic(data.getTotalBalance(), "0", "0.00");
}


// ===================== type: YEARLY =====================

@Test
void testGetStatYearlyPreviousYearEmpty() throws Exception {
    TransactionStateResponse data = getStatOk("type", "YEARLY");

    // seluruh 2026 (termasuk Oktober), dibanding 2025 yang kosong
    assertStatistic(data.getTotalIncome(), "27100000", "100.00");
    assertStatistic(data.getTotalExpense(), "4300000", "100.00");
    assertStatistic(data.getTotalSaving(), "2500000", "100.00");
    assertStatistic(data.getTotalBalance(), "20300000", "100.00");
}

@Test
void testGetStatYearlyComparedToPreviousYear() throws Exception {
    createTransaction(user, gaji, "20000000", LocalDateTime.of(2025, 12, 31, 23, 59, 59), false);
    createTransaction(user, tagihan, "5000000", LocalDateTime.of(2025, 1, 1, 0, 0), false);
    createTransaction(user, investasi, "2500000", LocalDateTime.of(2025, 6, 1, 10, 0), false);

    TransactionStateResponse data = getStatOk("type", "YEARLY");

    assertStatistic(data.getTotalIncome(), "27100000", "35.50");
    assertStatistic(data.getTotalExpense(), "4300000", "-14.00");
    assertStatistic(data.getTotalSaving(), "2500000", "0.00");
    // 20.300.000 vs 12.500.000
    assertStatistic(data.getTotalBalance(), "20300000", "62.40");
}

@Test
void testGetStatYearlyBoundary() throws Exception {
    transactionRepository.deleteAll();
    createTransaction(user, gaji, "1000000", LocalDateTime.of(2026, 1, 1, 0, 0), false);
    createTransaction(user, gaji, "2000000", LocalDateTime.of(2026, 12, 31, 23, 59, 59), false);
    createTransaction(user, gaji, "9000000", LocalDateTime.of(2027, 1, 1, 0, 0), false);
    createTransaction(user, gaji, "1500000", LocalDateTime.of(2025, 1, 1, 0, 0), false);
    createTransaction(user, gaji, "7000000", LocalDateTime.of(2024, 12, 31, 23, 59, 59), false);

    // 2026: 3.000.000, 2025: 1.500.000
    assertStatistic(getStatOk("type", "YEARLY").getTotalIncome(), "3000000", "100.00");
}


// ===================== type: WEEKLY =====================

@Test
void testGetStatWeekly() throws Exception {
    transactionRepository.deleteAll();
    // minggu ini: 09-18 00:00 s/d 09-24 23:59:59
    createTransaction(user, gaji, "1000000", LocalDateTime.of(2026, 9, 18, 0, 0), false);
    createTransaction(user, investasi, "300000", LocalDateTime.of(2026, 9, 20, 10, 0), false);
    createTransaction(user, makanan, "200000", LocalDateTime.of(2026, 9, 24, 23, 59, 59), false);
    // minggu lalu: 09-11 00:00 s/d 09-17 23:59:59
    createTransaction(user, tagihan, "400000", LocalDateTime.of(2026, 9, 11, 0, 0), false);
    createTransaction(user, gaji, "800000", LocalDateTime.of(2026, 9, 17, 23, 59, 59), false);
    // di luar kedua periode
    createTransaction(user, gaji, "9999999", LocalDateTime.of(2026, 9, 25, 0, 0), false);
    createTransaction(user, tagihan, "5000000", LocalDateTime.of(2026, 9, 10, 23, 59, 59), false);

    TransactionStateResponse data = getStatOk("type", "WEEKLY");

    assertStatistic(data.getTotalIncome(), "1000000", "25.00");
    assertStatistic(data.getTotalExpense(), "200000", "-50.00");
    assertStatistic(data.getTotalSaving(), "300000", "100.00");
    assertStatistic(data.getTotalBalance(), "500000", "25.00");
}

@Test
void testGetStatWeeklyNoTransactionInFixture() throws Exception {
    // fixture tidak punya transaksi aktif di 09-11 s/d 09-24 (999.000 di 09-12 soft delete)
    TransactionStateResponse data = getStatOk("type", "weekly");

    assertStatistic(data.getTotalIncome(), "0", "0.00");
    assertStatistic(data.getTotalExpense(), "0", "0.00");
    assertStatistic(data.getTotalSaving(), "0", "0.00");
    assertStatistic(data.getTotalBalance(), "0", "0.00");
}


// ===================== type: CUSTOM =====================

@Test
void testGetStatCustomFullMonth() throws Exception {
    TransactionStateResponse data = getStatOk(
        "type", "CUSTOM",
        "startDate", "2026-09-01",
        "endDate", "2026-09-30"
    );

    // pembanding = 30 hari sebelumnya (08-02 s/d 08-31), gaji 08-01 tidak ikut
    assertStatistic(data.getTotalIncome(), "12100000", "100.00");
    assertStatistic(data.getTotalExpense(), "2000000", "-13.04");
    assertStatistic(data.getTotalSaving(), "1500000", "50.00");
    // (8.600.000 - (-3.300.000)) / 3.300.000
    assertStatistic(data.getTotalBalance(), "8600000", "360.61");
}

@Test
void testGetStatCustomSingleDay() throws Exception {
    TransactionStateResponse data = getStatOk(
        "type", "CUSTOM",
        "startDate", "2026-09-05",
        "endDate", "2026-09-05"
    );

    // pembanding = 09-04
    assertStatistic(data.getTotalIncome(), "0", "0.00");
    assertStatistic(data.getTotalExpense(), "500000", "100.00");
    assertStatistic(data.getTotalSaving(), "0", "0.00");
    // defisit -500.000 dari 0
    assertStatistic(data.getTotalBalance(), "-500000", "-100.00");
}

@Test
void testGetStatCustomArbitraryPeriod() throws Exception {
    TransactionStateResponse data = getStatOk(
        "type", "CUSTOM",
        "startDate", "2026-09-10",
        "endDate", "2026-09-20"
    );

    // 11 hari, pembanding = 08-30 s/d 09-09
    assertStatistic(data.getTotalIncome(), "0", "-100.00");
    assertStatistic(data.getTotalExpense(), "1500000", "87.50");
    assertStatistic(data.getTotalSaving(), "0", "-100.00");
    // (-1.500.000 - 9.700.000) / 9.700.000
    assertStatistic(data.getTotalBalance(), "-1500000", "-115.46");
}

@Test
void testGetStatCustomCrossMonthPeriod() throws Exception {
    TransactionStateResponse data = getStatOk(
        "type", "CUSTOM",
        "startDate", "2026-08-15",
        "endDate", "2026-09-15"
    );

    // 32 hari, pembanding = 07-14 s/d 08-14 (hanya gaji 08-01)
    assertStatistic(data.getTotalIncome(), "12000000", "20.00");
    assertStatistic(data.getTotalExpense(), "4300000", "100.00");
    assertStatistic(data.getTotalSaving(), "2500000", "100.00");
    assertStatistic(data.getTotalBalance(), "5200000", "-48.00");
}

@Test
void testGetStatCustomPreviousPeriodEmpty() throws Exception {
    TransactionStateResponse data = getStatOk(
        "type", "CUSTOM",
        "startDate", "2026-08-01",
        "endDate", "2026-08-31"
    );

    assertStatistic(data.getTotalIncome(), "10000000", "100.00");
    assertStatistic(data.getTotalExpense(), "2300000", "100.00");
    assertStatistic(data.getTotalSaving(), "1000000", "100.00");
    assertStatistic(data.getTotalBalance(), "6700000", "100.00");
}

@Test
void testGetStatCustomCurrentPeriodEmpty() throws Exception {
    TransactionStateResponse data = getStatOk(
        "type", "CUSTOM",
        "startDate", "2026-10-02",
        "endDate", "2026-10-31"
    );

    // pembanding = 09-02 s/d 10-01
    assertStatistic(data.getTotalIncome(), "0", "-100.00");
    assertStatistic(data.getTotalExpense(), "0", "-100.00");
    assertStatistic(data.getTotalSaving(), "0", "-100.00");
    assertStatistic(data.getTotalBalance(), "0", "-100.00");
}

@Test
void testGetStatCustomEmptyPeriod() throws Exception {
    TransactionStateResponse data = getStatOk(
        "type", "CUSTOM",
        "startDate", "2026-12-01",
        "endDate", "2026-12-31"
    );

    assertStatistic(data.getTotalIncome(), "0", "0.00");
    assertStatistic(data.getTotalExpense(), "0", "0.00");
    assertStatistic(data.getTotalSaving(), "0", "0.00");
    assertStatistic(data.getTotalBalance(), "0", "0.00");
}

@Test
void testGetStatCustomCrossYear() throws Exception {
    createTransaction(user, gaji, "1000000", LocalDateTime.of(2026, 12, 15, 10, 0), false);
    createTransaction(user, gaji, "1500000", LocalDateTime.of(2027, 1, 15, 10, 0), false);

    TransactionStateResponse data = getStatOk(
        "type", "CUSTOM",
        "startDate", "2027-01-01",
        "endDate", "2027-01-31"
    );

    // pembanding = 2026-12-01 s/d 2026-12-31
    assertStatistic(data.getTotalIncome(), "1500000", "50.00");
}


// ===================== validasi =====================

@Test
void testGetStatBadRequestInvalidType() throws Exception {
    String errors = getStatError(status().isBadRequest(), statRequest("type", "DAILY"));
    assertEquals("Invalid filter type: DAILY", errors);
}

@Test
void testGetStatBadRequestCustomWithoutDates() throws Exception {
    String errors = getStatError(status().isBadRequest(), statRequest("type", "CUSTOM"));
    assertEquals("startDate and endDate are required for CUSTOM type", errors);
}

@Test
void testGetStatBadRequestCustomWithoutEndDate() throws Exception {
    String errors = getStatError(
        status().isBadRequest(),
        statRequest("type", "CUSTOM", "startDate", "2026-09-01")
    );
    assertEquals("startDate and endDate are required for CUSTOM type", errors);
}

@Test
void testGetStatBadRequestCustomWithoutStartDate() throws Exception {
    String errors = getStatError(
        status().isBadRequest(),
        statRequest("type", "CUSTOM", "endDate", "2026-09-30")
    );
    assertEquals("startDate and endDate are required for CUSTOM type", errors);
}

@Test
void testGetStatBadRequestCustomEndDateBeforeStartDate() throws Exception {
    String errors = getStatError(
        status().isBadRequest(),
        statRequest("type", "CUSTOM", "startDate", "2026-09-30", "endDate", "2026-09-01")
    );
    assertEquals("endDate must not be before startDate", errors);
}

@Test
void testGetStatBadRequestInvalidDateFormat() throws Exception {
    getStatError(status().isBadRequest(), statRequest("type", "CUSTOM", "startDate", "01-09-2026"));
}

@Test
void testGetStatBadRequestInvalidMonth() throws Exception {
    getStatError(status().isBadRequest(), statRequest("type", "CUSTOM", "startDate", "2026-13-01"));
}

@Test
void testGetStatBadRequestNonDateValue() throws Exception {
    getStatError(status().isBadRequest(), statRequest("type", "CUSTOM", "startDate", "abc"));
}


// ===================== autentikasi =====================

@Test
void testGetStatUnauthorizedWithoutHeader() throws Exception {
    String errors = getStatError(
        status().isUnauthorized(),
        get("/api/transaction/stat").accept(MediaType.APPLICATION_JSON)
    );
    assertEquals("Unauthorized", errors);
}

@Test
void testGetStatUnauthorizedInvalidToken() throws Exception {
    String errors = getStatError(
        status().isUnauthorized(),
        get("/api/transaction/stat").accept(MediaType.APPLICATION_JSON).header("Authorization", "Bearer invalid-token")
    );
    assertEquals("Unauthorized", errors);
}

@Test
void testGetStatUnauthorizedBlankToken() throws Exception {
    String errors = getStatError(
        status().isUnauthorized(),
        get("/api/transaction/stat").accept(MediaType.APPLICATION_JSON).header("Authorization", "Bearer ")
    );
    assertEquals("Unauthorized", errors);
}

@Test
void testGetStatUnauthorizedWithoutBearerPrefix() throws Exception {
    String errors = getStatError(
        status().isUnauthorized(),
        get("/api/transaction/stat").accept(MediaType.APPLICATION_JSON).header("Authorization", "test")
    );
    assertEquals("Unauthorized", errors);
}

}

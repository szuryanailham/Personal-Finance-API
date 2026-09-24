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

// "Hari ini" dikunci ke 2026-09-24 (Asia/Jakarta) agar default bulan berjalan deterministik
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


// TC-01
@Test
void testGetStatSuccess() throws Exception {
    mockMvc.perform(
        get("/api/transaction/stat")
            .param("startDate", "2026-09-01")
            .param("endDate", "2026-09-30")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        WebResponse<TransactionStateResponse> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<TransactionStateResponse>>() {}
        );
        assertNull(response.getErrors());
        assertNotNull(response.getData());

        assertEquals(0, new BigDecimal("12100000").compareTo(response.getData().getTotalIncome().getAmount()));
        assertEquals(0, new BigDecimal("21.00").compareTo(response.getData().getTotalIncome().getChangePercentage()));
        assertEquals(0, new BigDecimal("2000000").compareTo(response.getData().getTotalExpense().getAmount()));
        assertEquals(0, new BigDecimal("-13.04").compareTo(response.getData().getTotalExpense().getChangePercentage()));
        assertEquals(0, new BigDecimal("1500000").compareTo(response.getData().getTotalSaving().getAmount()));
        assertEquals(0, new BigDecimal("50.00").compareTo(response.getData().getTotalSaving().getChangePercentage()));
        assertEquals(0, new BigDecimal("10100000").compareTo(response.getData().getTotalBalance().getAmount()));
        assertEquals(0, new BigDecimal("31.17").compareTo(response.getData().getTotalBalance().getChangePercentage()));
    });
}

// TC-02
@Test
void testGetStatDefaultCurrentMonth() throws Exception {
    mockMvc.perform(
        get("/api/transaction/stat")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        WebResponse<TransactionStateResponse> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<TransactionStateResponse>>() {}
        );
        assertNull(response.getErrors());
        assertNotNull(response.getData());

        assertEquals(0, new BigDecimal("12100000").compareTo(response.getData().getTotalIncome().getAmount()));
        assertEquals(0, new BigDecimal("21.00").compareTo(response.getData().getTotalIncome().getChangePercentage()));
        assertEquals(0, new BigDecimal("2000000").compareTo(response.getData().getTotalExpense().getAmount()));
        assertEquals(0, new BigDecimal("-13.04").compareTo(response.getData().getTotalExpense().getChangePercentage()));
        assertEquals(0, new BigDecimal("1500000").compareTo(response.getData().getTotalSaving().getAmount()));
        assertEquals(0, new BigDecimal("50.00").compareTo(response.getData().getTotalSaving().getChangePercentage()));
        assertEquals(0, new BigDecimal("10100000").compareTo(response.getData().getTotalBalance().getAmount()));
        assertEquals(0, new BigDecimal("31.17").compareTo(response.getData().getTotalBalance().getChangePercentage()));
    });
}

// TC-03
@Test
void testGetStatExcludeSoftDeletedTransaction() throws Exception {
    mockMvc.perform(
        get("/api/transaction/stat")
            .param("startDate", "2026-09-01")
            .param("endDate", "2026-09-30")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        WebResponse<TransactionStateResponse> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<TransactionStateResponse>>() {}
        );
        assertNull(response.getErrors());
        // transaksi 999.000 yang di-soft delete tidak ikut dihitung
        assertEquals(0, new BigDecimal("2000000").compareTo(response.getData().getTotalExpense().getAmount()));
    });
}


@Test
void testGetStatExcludeOtherUserTransaction() throws Exception {
    mockMvc.perform(
        get("/api/transaction/stat")
            .param("startDate", "2026-09-01")
            .param("endDate", "2026-09-30")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        WebResponse<TransactionStateResponse> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<TransactionStateResponse>>() {}
        );
        assertNull(response.getErrors());
        // income 7.000.000 milik user B tidak ikut dihitung
        assertEquals(0, new BigDecimal("12100000").compareTo(response.getData().getTotalIncome().getAmount()));
    });
}


@Test
void testGetStatPeriodBoundary() throws Exception {
    mockMvc.perform(
        get("/api/transaction/stat")
            .param("startDate", "2026-09-01")
            .param("endDate", "2026-09-30")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        WebResponse<TransactionStateResponse> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<TransactionStateResponse>>() {}
        );
        assertNull(response.getErrors());
        assertEquals(0, new BigDecimal("12100000").compareTo(response.getData().getTotalIncome().getAmount()));
    });
}

// TC-08
@Test
void testGetStatLastDayOfPreviousMonthInComparison() throws Exception {
    mockMvc.perform(
        get("/api/transaction/stat")
            .param("startDate", "2026-09-01")
            .param("endDate", "2026-09-30")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        WebResponse<TransactionStateResponse> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<TransactionStateResponse>>() {}
        );
        assertNull(response.getErrors());
        // 300.000 (08-31 23:59:59) tidak masuk September, tapi masuk pembanding Agustus (2.300.000)
        assertEquals(0, new BigDecimal("2000000").compareTo(response.getData().getTotalExpense().getAmount()));
        assertEquals(0, new BigDecimal("-13.04").compareTo(response.getData().getTotalExpense().getChangePercentage()));
    });
}

// TC-09
@Test
void testGetStatSingleDayPeriod() throws Exception {
    mockMvc.perform(
        get("/api/transaction/stat")
            .param("startDate", "2026-09-05")
            .param("endDate", "2026-09-05")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        WebResponse<TransactionStateResponse> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<TransactionStateResponse>>() {}
        );
        assertNull(response.getErrors());
        assertEquals(0, new BigDecimal("500000").compareTo(response.getData().getTotalExpense().getAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getData().getTotalIncome().getAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getData().getTotalSaving().getAmount()));
        assertEquals(0, new BigDecimal("-500000").compareTo(response.getData().getTotalBalance().getAmount()));
    });
}

// TC-10
@Test
void testGetStatEmptyPeriod() throws Exception {
    mockMvc.perform(
        get("/api/transaction/stat")
            .param("startDate", "2026-12-01")
            .param("endDate", "2026-12-31")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        WebResponse<TransactionStateResponse> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<TransactionStateResponse>>() {}
        );
        assertNull(response.getErrors());
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getData().getTotalIncome().getAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getData().getTotalIncome().getChangePercentage()));
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getData().getTotalExpense().getAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getData().getTotalExpense().getChangePercentage()));
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getData().getTotalSaving().getAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getData().getTotalSaving().getChangePercentage()));
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getData().getTotalBalance().getAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getData().getTotalBalance().getChangePercentage()));
    });
}

// TC-11
@Test
void testGetStatPreviousMonthEmpty() throws Exception {
    mockMvc.perform(
        get("/api/transaction/stat")
            .param("startDate", "2026-08-01")
            .param("endDate", "2026-08-31")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        WebResponse<TransactionStateResponse> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<TransactionStateResponse>>() {}
        );
        assertNull(response.getErrors());
        assertEquals(0, new BigDecimal("100.00").compareTo(response.getData().getTotalIncome().getChangePercentage()));
        assertEquals(0, new BigDecimal("100.00").compareTo(response.getData().getTotalExpense().getChangePercentage()));
        assertEquals(0, new BigDecimal("100.00").compareTo(response.getData().getTotalSaving().getChangePercentage()));
        assertEquals(0, new BigDecimal("100.00").compareTo(response.getData().getTotalBalance().getChangePercentage()));
    });
}

// TC-12
@Test
void testGetStatCurrentMonthEmpty() throws Exception {
    mockMvc.perform(
        get("/api/transaction/stat")
            .param("startDate", "2026-11-01")
            .param("endDate", "2026-11-30")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        WebResponse<TransactionStateResponse> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<TransactionStateResponse>>() {}
        );
        assertNull(response.getErrors());
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getData().getTotalIncome().getAmount()));
        assertEquals(0, new BigDecimal("-100.00").compareTo(response.getData().getTotalIncome().getChangePercentage()));
        assertEquals(0, new BigDecimal("-100.00").compareTo(response.getData().getTotalBalance().getChangePercentage()));
    });
}

// TC-13
@Test
void testGetStatNegativePercentage() throws Exception {
    transactionRepository.deleteAll();
    createTransaction(user, investasi, "1000000", LocalDateTime.of(2026, 8, 10, 10, 0), false);
    createTransaction(user, investasi, "750000", LocalDateTime.of(2026, 9, 10, 10, 0), false);

    mockMvc.perform(
        get("/api/transaction/stat")
            .param("startDate", "2026-09-01")
            .param("endDate", "2026-09-30")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        WebResponse<TransactionStateResponse> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<TransactionStateResponse>>() {}
        );
        assertNull(response.getErrors());
        assertEquals(0, new BigDecimal("-25.00").compareTo(response.getData().getTotalSaving().getChangePercentage()));
    });
}

@Test
void testGetStatPercentageRoundDown() throws Exception {
    transactionRepository.deleteAll();
    createTransaction(user, gaji, "300000", LocalDateTime.of(2026, 8, 10, 10, 0), false);
    createTransaction(user, gaji, "400000", LocalDateTime.of(2026, 9, 10, 10, 0), false);

    mockMvc.perform(
        get("/api/transaction/stat")
            .param("startDate", "2026-09-01")
            .param("endDate", "2026-09-30")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        WebResponse<TransactionStateResponse> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<TransactionStateResponse>>() {}
        );
        assertNull(response.getErrors());
        assertEquals(0, new BigDecimal("33.33").compareTo(response.getData().getTotalIncome().getChangePercentage()));
    });
}

@Test
void testGetStatPercentageRoundUp() throws Exception {
    transactionRepository.deleteAll();
    createTransaction(user, gaji, "600000", LocalDateTime.of(2026, 8, 10, 10, 0), false);
    createTransaction(user, gaji, "700000", LocalDateTime.of(2026, 9, 10, 10, 0), false);

    mockMvc.perform(
        get("/api/transaction/stat")
            .param("startDate", "2026-09-01")
            .param("endDate", "2026-09-30")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        WebResponse<TransactionStateResponse> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<TransactionStateResponse>>() {}
        );
        assertNull(response.getErrors());
        assertEquals(0, new BigDecimal("16.67").compareTo(response.getData().getTotalIncome().getChangePercentage()));
    });
}


@Test
void testGetStatNegativeBalance() throws Exception {
    transactionRepository.deleteAll();
    createTransaction(user, gaji, "1000000", LocalDateTime.of(2026, 9, 10, 10, 0), false);
    createTransaction(user, tagihan, "3000000", LocalDateTime.of(2026, 9, 11, 10, 0), false);

    mockMvc.perform(
        get("/api/transaction/stat")
            .param("startDate", "2026-09-01")
            .param("endDate", "2026-09-30")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        WebResponse<TransactionStateResponse> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<TransactionStateResponse>>() {}
        );
        assertNull(response.getErrors());
        assertEquals(0, new BigDecimal("-2000000").compareTo(response.getData().getTotalBalance().getAmount()));
    });
}

@Test
void testGetStatPreviousBalanceNegative() throws Exception {
    transactionRepository.deleteAll();
    createTransaction(user, gaji, "1000000", LocalDateTime.of(2026, 8, 10, 10, 0), false);
    createTransaction(user, tagihan, "3000000", LocalDateTime.of(2026, 8, 11, 10, 0), false);
    createTransaction(user, gaji, "1000000", LocalDateTime.of(2026, 9, 10, 10, 0), false);

    mockMvc.perform(
        get("/api/transaction/stat")
            .param("startDate", "2026-09-01")
            .param("endDate", "2026-09-30")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        WebResponse<TransactionStateResponse> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<TransactionStateResponse>>() {}
        );
        assertNull(response.getErrors());
        assertEquals(0, new BigDecimal("1000000").compareTo(response.getData().getTotalBalance().getAmount()));
        assertEquals(0, new BigDecimal("150.00").compareTo(response.getData().getTotalBalance().getChangePercentage()));
    });
}


@Test
void testGetStatSavingNotReduceBalance() throws Exception {
    mockMvc.perform(
        get("/api/transaction/stat")
            .param("startDate", "2026-09-01")
            .param("endDate", "2026-09-30")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        WebResponse<TransactionStateResponse> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<TransactionStateResponse>>() {}
        );
        assertNull(response.getErrors());
        // 12.100.000 - 2.000.000, saving 1.500.000 tidak dikurangi
        assertEquals(0, new BigDecimal("10100000").compareTo(response.getData().getTotalBalance().getAmount()));
    });
}


@Test
void testGetStatDecimalAmount() throws Exception {
    transactionRepository.deleteAll();
    createTransaction(user, gaji, "100.50", LocalDateTime.of(2026, 9, 10, 10, 0), false);
    createTransaction(user, gaji, "200.25", LocalDateTime.of(2026, 9, 11, 10, 0), false);

    mockMvc.perform(
        get("/api/transaction/stat")
            .param("startDate", "2026-09-01")
            .param("endDate", "2026-09-30")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        WebResponse<TransactionStateResponse> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<TransactionStateResponse>>() {}
        );
        assertNull(response.getErrors());
        assertEquals(0, new BigDecimal("300.75").compareTo(response.getData().getTotalIncome().getAmount()));
    });
}


@Test
void testGetStatCategoryTypeChangeAffectsHistory() throws Exception {
    investasi.setType(TransactionType.EXPENSE.name());
    categoryRepository.save(investasi);

    mockMvc.perform(
        get("/api/transaction/stat")
            .param("startDate", "2026-09-01")
            .param("endDate", "2026-09-30")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        WebResponse<TransactionStateResponse> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<TransactionStateResponse>>() {}
        );
        assertNull(response.getErrors());
        // transaksi Investasi 1.500.000 pindah dari saving ke expense
        assertEquals(0, new BigDecimal("3500000").compareTo(response.getData().getTotalExpense().getAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getData().getTotalSaving().getAmount()));
    });
}


@Test
void testGetStatCustomPeriodComparedToFullPreviousMonth() throws Exception {
    mockMvc.perform(
        get("/api/transaction/stat")
            .param("startDate", "2026-09-10")
            .param("endDate", "2026-09-20")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        WebResponse<TransactionStateResponse> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<TransactionStateResponse>>() {}
        );
        assertNull(response.getErrors());
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getData().getTotalIncome().getAmount()));
        assertEquals(0, new BigDecimal("-100.00").compareTo(response.getData().getTotalIncome().getChangePercentage()));
        assertEquals(0, new BigDecimal("1500000").compareTo(response.getData().getTotalExpense().getAmount()));
        assertEquals(0, new BigDecimal("-34.78").compareTo(response.getData().getTotalExpense().getChangePercentage()));
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getData().getTotalSaving().getAmount()));
        assertEquals(0, new BigDecimal("-100.00").compareTo(response.getData().getTotalSaving().getChangePercentage()));
        assertEquals(0, new BigDecimal("-1500000").compareTo(response.getData().getTotalBalance().getAmount()));
        assertEquals(0, new BigDecimal("-119.48").compareTo(response.getData().getTotalBalance().getChangePercentage()));
    });
}


@Test
void testGetStatCrossMonthPeriod() throws Exception {
    mockMvc.perform(
        get("/api/transaction/stat")
            .param("startDate", "2026-08-15")
            .param("endDate", "2026-09-15")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        WebResponse<TransactionStateResponse> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<TransactionStateResponse>>() {}
        );
        assertNull(response.getErrors());
        assertEquals(0, new BigDecimal("12000000").compareTo(response.getData().getTotalIncome().getAmount()));
        assertEquals(0, new BigDecimal("4300000").compareTo(response.getData().getTotalExpense().getAmount()));
        assertEquals(0, new BigDecimal("2500000").compareTo(response.getData().getTotalSaving().getAmount()));
        // pembanding = Juli (kosong), bukan Agustus
        assertEquals(0, new BigDecimal("100.00").compareTo(response.getData().getTotalIncome().getChangePercentage()));
        assertEquals(0, new BigDecimal("100.00").compareTo(response.getData().getTotalExpense().getChangePercentage()));
        assertEquals(0, new BigDecimal("100.00").compareTo(response.getData().getTotalSaving().getChangePercentage()));
        assertEquals(0, new BigDecimal("100.00").compareTo(response.getData().getTotalBalance().getChangePercentage()));
    });
}

@Test
void testGetStatJanuaryComparedToPreviousYearDecember() throws Exception {
    createTransaction(user, gaji, "1000000", LocalDateTime.of(2026, 12, 15, 10, 0), false);
    createTransaction(user, gaji, "1500000", LocalDateTime.of(2027, 1, 15, 10, 0), false);

    mockMvc.perform(
        get("/api/transaction/stat")
            .param("startDate", "2027-01-01")
            .param("endDate", "2027-01-31")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        WebResponse<TransactionStateResponse> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<TransactionStateResponse>>() {}
        );
        assertNull(response.getErrors());
        assertEquals(0, new BigDecimal("1500000").compareTo(response.getData().getTotalIncome().getAmount()));
        assertEquals(0, new BigDecimal("50.00").compareTo(response.getData().getTotalIncome().getChangePercentage()));
    });
}

@Test
void testGetStatBadRequestEndDateBeforeStartDate() throws Exception {
    mockMvc.perform(
        get("/api/transaction/stat")
            .param("startDate", "2026-09-30")
            .param("endDate", "2026-09-01")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isBadRequest())
    .andDo(result -> {
        WebResponse<String> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<String>>() {}
        );
        assertEquals("endDate must be after startDate", response.getErrors());
    });
}


@Test
void testGetStatBadRequestOnlyStartDateAfterCurrentMonth() throws Exception {
    mockMvc.perform(
        get("/api/transaction/stat")
            .param("startDate", "2026-11-01")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isBadRequest())
    .andDo(result -> {
        WebResponse<String> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<String>>() {}
        );
        assertEquals("endDate must be after startDate", response.getErrors());
    });
}

@Test
void testGetStatBadRequestOnlyEndDateBeforeCurrentMonth() throws Exception {
    mockMvc.perform(
        get("/api/transaction/stat")
            .param("endDate", "2026-08-15")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isBadRequest())
    .andDo(result -> {
        WebResponse<String> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<String>>() {}
        );
        assertNotNull(response.getErrors());
    });
}

@Test
void testGetStatOnlyStartDateSuccess() throws Exception {
    mockMvc.perform(
        get("/api/transaction/stat")
            .param("startDate", "2026-09-10")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        WebResponse<TransactionStateResponse> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<TransactionStateResponse>>() {}
        );
        assertNull(response.getErrors());
        // periode 10-30 Sep
        assertEquals(0, new BigDecimal("100000").compareTo(response.getData().getTotalIncome().getAmount()));
        assertEquals(0, new BigDecimal("1500000").compareTo(response.getData().getTotalExpense().getAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getData().getTotalSaving().getAmount()));
    });
}

@Test
void testGetStatBadRequestInvalidDateFormat() throws Exception {
    mockMvc.perform(
        get("/api/transaction/stat")
            .param("startDate", "01-09-2026")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isBadRequest())
    .andDo(result -> {
        WebResponse<String> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<String>>() {}
        );
        assertNotNull(response.getErrors());
    });
}

@Test
void testGetStatBadRequestInvalidMonth() throws Exception {
    mockMvc.perform(
        get("/api/transaction/stat")
            .param("startDate", "2026-13-01")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isBadRequest())
    .andDo(result -> {
        WebResponse<String> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<String>>() {}
        );
        assertNotNull(response.getErrors());
    });
}


@Test
void testGetStatBadRequestNonDateValue() throws Exception {
    mockMvc.perform(
        get("/api/transaction/stat")
            .param("startDate", "abc")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isBadRequest())
    .andDo(result -> {
        WebResponse<String> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<String>>() {}
        );
        assertNotNull(response.getErrors());
    });
}

@Test
void testGetStatUnauthorizedWithoutHeader() throws Exception {
    mockMvc.perform(
        get("/api/transaction/stat")
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

@Test
void testGetStatUnauthorizedInvalidToken() throws Exception {
    mockMvc.perform(
        get("/api/transaction/stat")
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

@Test
void testGetStatUnauthorizedBlankToken() throws Exception {
    mockMvc.perform(
        get("/api/transaction/stat")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer ")
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

@Test
void testGetStatUnauthorizedWithoutBearerPrefix() throws Exception {
    mockMvc.perform(
        get("/api/transaction/stat")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "test")
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

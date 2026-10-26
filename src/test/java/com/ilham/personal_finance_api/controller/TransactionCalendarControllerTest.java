package com.ilham.personal_finance_api.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.ilham.personal_finance_api.AbstractIntegrationTest;
import com.ilham.personal_finance_api.dto.CalendarDayResponse;
import com.ilham.personal_finance_api.dto.CalendarTypeSummaryResponse;
import com.ilham.personal_finance_api.dto.TransactionCalendarResponse;
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
public class TransactionCalendarControllerTest extends AbstractIntegrationTest {

private static final String CALENDAR_URL = "/api/transaction/calendar";
private static final String INVALID_MONTH_MESSAGE = "month harus berformat YYYY-MM";
private static final List<TransactionType> TYPE_ORDER =
    List.of(TransactionType.INCOME, TransactionType.EXPENSE, TransactionType.SAVING);

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

    // Di luar Oktober (batas bawah & atas)
    createTransaction(user, makanan, "300000", LocalDateTime.of(2026, 9, 30, 23, 59, 59), false);
    createTransaction(user, gaji, "7000000", LocalDateTime.of(2026, 11, 1, 0, 0), false);
    // 10-01: hanya EXPENSE
    createTransaction(user, makanan, "100000", LocalDateTime.of(2026, 10, 1, 0, 0), false);
    createTransaction(user, makanan, "145000", LocalDateTime.of(2026, 10, 1, 12, 0), false);
    // 10-05: INCOME + EXPENSE yang sudah dihapus
    createTransaction(user, gaji, "500000", LocalDateTime.of(2026, 10, 5, 9, 0), false);
    createTransaction(user, makanan, "50000", LocalDateTime.of(2026, 10, 5, 10, 0), true);
    // 10-12: satu-satunya transaksi sudah dihapus -> tanggal tidak muncul
    createTransaction(user, makanan, "999000", LocalDateTime.of(2026, 10, 12, 10, 0), true);
    // 10-25: ketiga type
    createTransaction(user, gaji, "8000000", LocalDateTime.of(2026, 10, 25, 8, 0), false);
    createTransaction(user, makanan, "120000", LocalDateTime.of(2026, 10, 25, 12, 0), false);
    createTransaction(user, investasi, "500000", LocalDateTime.of(2026, 10, 25, 13, 0), false);
    // 10-31 23:59:59 masih Oktober
    createTransaction(user, investasi, "1000000", LocalDateTime.of(2026, 10, 31, 23, 59, 59), false);
    // User lain
    createTransaction(otherUser, gajiB, "9000000", LocalDateTime.of(2026, 10, 25, 9, 0), false);
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

private ResultActions performCalendar(String month) throws Exception {
    return mockMvc.perform(
        get(CALENDAR_URL)
            .param("month", month)
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    );
}

private TransactionCalendarResponse readData(MvcResult result) throws Exception {
    WebResponse<TransactionCalendarResponse> response = objectMapper.readValue(
        result.getResponse().getContentAsString(),
        new TypeReference<WebResponse<TransactionCalendarResponse>>() {}
    );
    assertNull(response.getErrors());
    assertNotNull(response.getData());
    return response.getData();
}

private CalendarDayResponse dayAt(TransactionCalendarResponse data, LocalDate date) {
    return data.days().stream()
        .filter(day -> date.equals(day.date()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Tanggal " + date + " tidak ada"));
}

// summaries / totals harus INCOME, EXPENSE, SAVING dengan amount & count yang diharapkan
private void assertSummaries(List<CalendarTypeSummaryResponse> summaries,
        String income, long incomeCount, String expense, long expenseCount, String saving, long savingCount) {
    assertEquals(TYPE_ORDER, summaries.stream().map(summary -> summary.type()).toList());
    assertSummary(summaries.get(0), income, incomeCount);
    assertSummary(summaries.get(1), expense, expenseCount);
    assertSummary(summaries.get(2), saving, savingCount);
}

private void assertSummary(CalendarTypeSummaryResponse summary, String amount, long count) {
    assertEquals(0, new BigDecimal(amount).compareTo(summary.amount()), "amount " + summary.type());
    assertEquals(count, summary.count(), "count " + summary.type());
}


// TC-01
@Test
void testGetCalendarMetadataSerializedAsIsoStrings() throws Exception {
    performCalendar("2026-10")
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.month").value("2026-10"))
    .andExpect(jsonPath("$.data.startDate").value("2026-10-01"))
    .andExpect(jsonPath("$.data.endDate").value("2026-10-31"))
    .andExpect(jsonPath("$.data.days[0].date").value("2026-10-01"))
    .andExpect(jsonPath("$.data.totals[0].type").value("INCOME"))
    .andExpect(jsonPath("$.message").doesNotExist())
    .andExpect(jsonPath("$.errors").doesNotExist())
    .andExpect(jsonPath("$.paging").doesNotExist());
}

// TC-02
@Test
void testGetCalendarTotals() throws Exception {
    performCalendar("2026-10")
    .andExpect(status().isOk())
    .andDo(result -> {
        TransactionCalendarResponse data = readData(result);
        assertEquals(YearMonth.of(2026, 10), data.month());
        // income: 500.000 + 8.000.000; expense: 100.000 + 145.000 + 120.000; saving: 500.000 + 1.000.000
        assertSummaries(data.totals(), "8500000", 2, "365000", 3, "1500000", 2);
    });
}

// TC-03
@Test
void testGetCalendarDaysOnlyDatesWithTransactionsAscending() throws Exception {
    performCalendar("2026-10")
    .andExpect(status().isOk())
    .andDo(result -> {
        TransactionCalendarResponse data = readData(result);
        // 10-12 tidak muncul karena satu-satunya transaksinya sudah dihapus
        assertEquals(
            List.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 31)),
            data.days().stream().map(day -> day.date()).toList()
        );
    });
}

// TC-04
@Test
void testGetCalendarDayWithSingleTypeReturnsAllThreeTypes() throws Exception {
    performCalendar("2026-10")
    .andExpect(status().isOk())
    .andDo(result -> {
        TransactionCalendarResponse data = readData(result);
        assertSummaries(dayAt(data, LocalDate.of(2026, 10, 1)).summaries(), "0", 0, "245000", 2, "0", 0);
    });
}

// TC-05
@Test
void testGetCalendarDayWithAllTypes() throws Exception {
    performCalendar("2026-10")
    .andExpect(status().isOk())
    .andDo(result -> {
        TransactionCalendarResponse data = readData(result);
        // transaksi user lain di tanggal yang sama tidak ikut
        assertSummaries(dayAt(data, LocalDate.of(2026, 10, 25)).summaries(), "8000000", 1, "120000", 1, "500000", 1);
    });
}

// TC-06
@Test
void testGetCalendarExcludesSoftDeletedTransactions() throws Exception {
    performCalendar("2026-10")
    .andExpect(status().isOk())
    .andDo(result -> {
        TransactionCalendarResponse data = readData(result);
        // expense 50.000 di 10-05 sudah dihapus
        assertSummaries(dayAt(data, LocalDate.of(2026, 10, 5)).summaries(), "500000", 1, "0", 0, "0", 0);
    });
}

// TC-07
@Test
void testGetCalendarMonthBoundary() throws Exception {
    performCalendar("2026-10")
    .andExpect(status().isOk())
    .andDo(result -> {
        TransactionCalendarResponse data = readData(result);
        // 10-01 00:00 dan 10-31 23:59:59 masuk; 09-30 23:59:59 dan 11-01 00:00 tidak
        assertSummaries(dayAt(data, LocalDate.of(2026, 10, 31)).summaries(), "0", 0, "0", 0, "1000000", 1);
        assertEquals(LocalDate.of(2026, 10, 1), data.days().get(0).date());
    });
}

// TC-08
@Test
void testGetCalendarEmptyMonth() throws Exception {
    performCalendar("2026-12")
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.days", hasSize(0)))
    .andDo(result -> {
        TransactionCalendarResponse data = readData(result);
        assertEquals(LocalDate.of(2026, 12, 1), data.startDate());
        assertEquals(LocalDate.of(2026, 12, 31), data.endDate());
        assertSummaries(data.totals(), "0", 0, "0", 0, "0", 0);
    });
}

// TC-09
@Test
void testGetCalendarLeapYearFebruary() throws Exception {
    createTransaction(user, gaji, "100000", LocalDateTime.of(2028, 2, 29, 23, 0), false);
    createTransaction(user, gaji, "200000", LocalDateTime.of(2028, 3, 1, 0, 0), false);

    performCalendar("2028-02")
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.endDate").value("2028-02-29"))
    .andDo(result -> {
        TransactionCalendarResponse data = readData(result);
        assertEquals(1, data.days().size());
        assertSummaries(dayAt(data, LocalDate.of(2028, 2, 29)).summaries(), "100000", 1, "0", 0, "0", 0);
        assertSummaries(data.totals(), "100000", 1, "0", 0, "0", 0);
    });
}

// TC-10
@Test
void testGetCalendarNonLeapYearFebruary() throws Exception {
    performCalendar("2027-02")
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.startDate").value("2027-02-01"))
    .andExpect(jsonPath("$.data.endDate").value("2027-02-28"));
}

// TC-11
@ParameterizedTest
@ValueSource(strings = {"2026-13", "2026-00", "2026-1", "10-2026", "2026/10", "2026-10-01", "abc", "202610"})
void testGetCalendarInvalidMonthFormat(String month) throws Exception {
    performCalendar(month)
    .andExpect(status().isBadRequest())
    .andExpect(jsonPath("$.data").doesNotExist())
    .andExpect(jsonPath("$.message").doesNotExist())
    .andExpect(jsonPath("$.errors").value(INVALID_MONTH_MESSAGE))
    .andExpect(jsonPath("$.paging").doesNotExist());
}

// TC-12
@Test
void testGetCalendarMissingMonth() throws Exception {
    mockMvc.perform(
        get(CALENDAR_URL)
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isBadRequest())
    .andExpect(jsonPath("$.data").doesNotExist())
    .andExpect(jsonPath("$.errors").value("month wajib diisi"));
}

// TC-13
@Test
void testGetCalendarBlankMonth() throws Exception {
    performCalendar(" ")
    .andExpect(status().isBadRequest())
    .andExpect(jsonPath("$.errors").value("month wajib diisi"));
}

// TC-14
@Test
void testGetCalendarOtherUserOnlySeesOwnTransactions() throws Exception {
    mockMvc.perform(
        get(CALENDAR_URL)
            .param("month", "2026-10")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer testB")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        TransactionCalendarResponse data = readData(result);
        assertEquals(1, data.days().size());
        assertSummaries(data.totals(), "9000000", 1, "0", 0, "0", 0);
    });
}

// TC-15
@Test
void testGetCalendarWithoutToken() throws Exception {
    mockMvc.perform(
        get(CALENDAR_URL)
            .param("month", "2026-10")
            .accept(MediaType.APPLICATION_JSON)
    )
    .andExpect(status().isUnauthorized());
}

// TC-16
@Test
void testGetCalendarInvalidToken() throws Exception {
    mockMvc.perform(
        get(CALENDAR_URL)
            .param("month", "2026-10")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer salah")
    )
    .andExpect(status().isUnauthorized());
}
}

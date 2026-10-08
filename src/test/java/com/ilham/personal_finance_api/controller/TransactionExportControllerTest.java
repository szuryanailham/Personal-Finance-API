package com.ilham.personal_finance_api.controller;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.ilham.personal_finance_api.AbstractIntegrationTest;
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

// batas baris diperkecil agar skenario "melebihi batas" tidak perlu ribuan data
@SpringBootTest(properties = "app.export.max-rows=60")
@AutoConfigureMockMvc
public class TransactionExportControllerTest extends AbstractIntegrationTest {

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

private static final String EXPORT_URL = "/api/transaction/export";
private static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
private static final int MAX_ROWS = 60;
private static final int SEEDED_ACTIVE_ROWS = 8;
private static final int TEMPLATE_FORMULA_ROWS = 50;

// posisi 0-based di template
private static final int SUMMARY_VALUE_COLUMN = 1;
private static final int SUMMARY_START_DATE_ROW = 5;
private static final int SUMMARY_END_DATE_ROW = 6;
private static final int SUMMARY_COUNT_ROW = 7;
private static final int SUMMARY_INCOME_ROW = 8;
private static final int SUMMARY_EXPENSE_ROW = 9;
private static final int SUMMARY_SAVING_ROW = 10;
private static final int SUMMARY_BALANCE_ROW = 11;
private static final int CATEGORY_FIRST_ROW = 16;
private static final int DETAIL_FIRST_ROW = 2;

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

private Category makanan;

@BeforeEach
void setUp() {
    transactionRepository.deleteAll();
    categoryRepository.deleteAll();
    userRepository.deleteAll();

    user = createUser("test@gmail.com", "test");
    User otherUser = createUser("testb@gmail.com", "testB");

    Category gaji = createCategory("Gaji", TransactionType.INCOME, user);
    makanan = createCategory("Makanan", TransactionType.EXPENSE, user);
    Category investasi = createCategory("Investasi", TransactionType.SAVING, user);
    Category gajiB = createCategory("Gaji B", TransactionType.INCOME, otherUser);

    // tahun lalu
    createTransaction(user, gaji, "Gaji Des 2025", "8000000", LocalDateTime.of(2025, 12, 31, 23, 59, 59), false);
    // Agustus
    createTransaction(user, gaji, "Gaji Agu", "10000000", LocalDateTime.of(2026, 8, 1, 9, 0), false);
    createTransaction(user, makanan, "Makan Agu", "300000", LocalDateTime.of(2026, 8, 31, 23, 59, 59), false);
    // September (bulan berjalan)
    createTransaction(user, gaji, "Gaji Sep", "12000000", LocalDateTime.of(2026, 9, 1, 0, 0), false);
    createTransaction(user, investasi, "Investasi Sep", "1500000", LocalDateTime.of(2026, 9, 3, 8, 0), false);
    createTransaction(user, makanan, "Makan Sep", "500000", LocalDateTime.of(2026, 9, 5, 12, 0), false, null);
    createTransaction(user, makanan, "Makan Dihapus", "999000", LocalDateTime.of(2026, 9, 12, 10, 0), true);
    // Oktober
    createTransaction(user, gaji, "Gaji Okt", "5000000", LocalDateTime.of(2026, 10, 1, 0, 0), false);
    // tahun depan
    createTransaction(user, gaji, "Gaji Jan 2027", "1000000", LocalDateTime.of(2027, 1, 1, 0, 0), false);
    // user lain
    createTransaction(otherUser, gajiB, "Gaji B", "7000000", LocalDateTime.of(2026, 9, 2, 9, 0), false);
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

private void createTransaction(User owner, Category category, String name, String amount, LocalDateTime date, boolean isDeleted) {
    createTransaction(owner, category, name, amount, date, isDeleted, "this is for test");
}

private void createTransaction(User owner, Category category, String name, String amount, LocalDateTime date,
        boolean isDeleted, String description) {
    Transaction newTransaction = new Transaction();
    newTransaction.setTransactionName(name);
    newTransaction.setTransactionCode("TRX-" + UUID.randomUUID());
    newTransaction.setCategory(category);
    newTransaction.setUser(owner);
    newTransaction.setAmount(new BigDecimal(amount));
    newTransaction.setDescription(description);
    newTransaction.setTransactionDate(date);
    newTransaction.setDeleted(isDeleted);
    transactionRepository.save(newTransaction);
}

private void createManyTransactions(int count) {
    for (int i = 0; i < count; i++) {
        createTransaction(user, makanan, "Bulk " + i, "1000", LocalDateTime.of(2026, 9, 20, 10, 0), false);
    }
}

// params: pasangan name, value
private MockHttpServletRequestBuilder exportRequest(String... params) {
    MockHttpServletRequestBuilder request = get(EXPORT_URL).header("Authorization", "Bearer test");
    for (int i = 0; i < params.length; i += 2) {
        request.param(params[i], params[i + 1]);
    }
    return request;
}

private XSSFWorkbook exportOk(String expectedFilename, String... params) throws Exception {
    byte[] content = mockMvc.perform(exportRequest(params))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", XLSX_CONTENT_TYPE))
        .andExpect(header().string("Content-Disposition", "attachment; filename=\"" + expectedFilename + "\""))
        .andReturn().getResponse().getContentAsByteArray();
    return new XSSFWorkbook(new ByteArrayInputStream(content));
}

private String exportError(ResultMatcher expectedStatus, MockHttpServletRequestBuilder request) throws Exception {
    String content = mockMvc.perform(request)
        .andExpect(expectedStatus)
        .andReturn().getResponse().getContentAsString();

    WebResponse<String> response = objectMapper.readValue(content, new TypeReference<WebResponse<String>>() {});
    assertNotNull(response.getErrors());
    return response.getErrors();
}

private List<List<String>> readRows(Sheet sheet, int firstRowIndex, int columnCount) {
    DataFormatter formatter = new DataFormatter();
    FormulaEvaluator evaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
    List<List<String>> rows = new ArrayList<>();
    for (int r = firstRowIndex; r <= sheet.getLastRowNum(); r++) {
        Row row = sheet.getRow(r);
        List<String> values = new ArrayList<>();
        for (int c = 0; c < columnCount; c++) {
            Cell cell = row == null ? null : row.getCell(c);
            values.add(cell == null ? "" : formatter.formatCellValue(cell, evaluator));
        }
        if (values.stream().allMatch(String::isBlank)) {
            break;
        }
        rows.add(values);
    }
    return rows;
}

private List<List<String>> detailRows(XSSFWorkbook workbook) {
    return readRows(workbook.getSheet("Transaction"), DETAIL_FIRST_ROW, 7);
}

private List<String> detailNames(XSSFWorkbook workbook) {
    return detailRows(workbook).stream().map(row -> row.get(1)).toList();
}

private List<List<String>> categoryRows(XSSFWorkbook workbook) {
    return readRows(workbook.getSheet("Summary"), CATEGORY_FIRST_ROW, 5);
}

private Cell summaryCell(XSSFWorkbook workbook, int rowIndex) {
    return workbook.getSheet("Summary").getRow(rowIndex).getCell(SUMMARY_VALUE_COLUMN);
}

private String summaryText(XSSFWorkbook workbook, int rowIndex) {
    return new DataFormatter().formatCellValue(summaryCell(workbook, rowIndex));
}

private void assertSummary(XSSFWorkbook workbook, String startDate, String endDate,
        int count, String income, String expense, String saving, String balance) {
    assertEquals(startDate, summaryText(workbook, SUMMARY_START_DATE_ROW), "start date");
    assertEquals(endDate, summaryText(workbook, SUMMARY_END_DATE_ROW), "end date");
    assertEquals(count, (int) summaryCell(workbook, SUMMARY_COUNT_ROW).getNumericCellValue(), "count");
    assertAmount(income, SUMMARY_INCOME_ROW, workbook, "income");
    assertAmount(expense, SUMMARY_EXPENSE_ROW, workbook, "expense");
    assertAmount(saving, SUMMARY_SAVING_ROW, workbook, "saving");
    assertAmount(balance, SUMMARY_BALANCE_ROW, workbook, "balance");
}

private void assertAmount(String expected, int rowIndex, XSSFWorkbook workbook, String label) {
    assertEquals(0, new BigDecimal(expected).compareTo(
        BigDecimal.valueOf(summaryCell(workbook, rowIndex).getNumericCellValue())), label);
}


// ===================== period: default / ALL =====================

@Test
void exportDefaultsToAllActiveTransactionsOfUser() throws Exception {
    try (XSSFWorkbook workbook = exportOk("transactions-all-20260924.xlsx")) {
        assertEquals(List.of(
            "Gaji Des 2025", "Gaji Agu", "Makan Agu", "Gaji Sep", "Investasi Sep", "Makan Sep", "Gaji Okt", "Gaji Jan 2027"
        ), detailNames(workbook));

        assertSummary(workbook, "2025-12-31", "2027-01-01",
            8, "36000000", "800000", "1500000", "33700000");
    }
}

@Test
void exportWritesTransactionDetailColumns() throws Exception {
    try (XSSFWorkbook workbook = exportOk("transactions-all-20260924.xlsx", "period", "ALL")) {
        List<List<String>> rows = detailRows(workbook);
        assertEquals(List.of("4", "Gaji Sep", "Gaji", "INCOME", "2026-09-01", "12,000,000", "this is for test"), rows.get(3));
        // description null tidak boleh tertulis "null"
        assertEquals(List.of("6", "Makan Sep", "Makanan", "EXPENSE", "2026-09-05", "500,000", ""), rows.get(5));

        // date & amount ditulis sebagai angka/tanggal asli, bukan teks
        Row row = workbook.getSheet("Transaction").getRow(DETAIL_FIRST_ROW);
        assertEquals(CellType.NUMERIC, row.getCell(4).getCellType());
        assertEquals(CellType.NUMERIC, row.getCell(5).getCellType());
    }
}

@Test
void exportPeriodIsCaseInsensitive() throws Exception {
    try (XSSFWorkbook workbook = exportOk("transactions-monthly-20260924.xlsx", "period", "monthly")) {
        assertEquals(3, detailRows(workbook).size());
    }
}


// ===================== is_deleted =====================

@Test
void exportExcludesSoftDeletedTransactionsFromDetailAndTotals() throws Exception {
    try (XSSFWorkbook workbook = exportOk("transactions-all-20260924.xlsx")) {
        assertEquals(false, detailNames(workbook).contains("Makan Dihapus"));
        // Makanan aktif: 300.000 + 500.000; transaksi 999.000 yang dihapus tidak ikut
        assertEquals(List.of("1", "Makanan", "EXPENSE", "2", "800,000"), categoryRows(workbook).get(0));
        assertAmount("800000", SUMMARY_EXPENSE_ROW, workbook, "expense");
    }
}

@Test
void exportExcludesTransactionsOfOtherUser() throws Exception {
    try (XSSFWorkbook workbook = exportOk("transactions-all-20260924.xlsx")) {
        assertEquals(false, detailNames(workbook).contains("Gaji B"));
        assertEquals(false, categoryRows(workbook).stream().anyMatch(row -> row.get(1).equals("Gaji B")));
    }
}


// ===================== period: YEARLY / MONTHLY / WEEKLY / MANUAL =====================

@Test
void exportYearlyUsesCurrentCalendarYear() throws Exception {
    try (XSSFWorkbook workbook = exportOk("transactions-yearly-20260924.xlsx", "period", "YEARLY")) {
        assertEquals(List.of("Gaji Agu", "Makan Agu", "Gaji Sep", "Investasi Sep", "Makan Sep", "Gaji Okt"),
            detailNames(workbook));
        assertSummary(workbook, "2026-01-01", "2026-12-31",
            6, "27000000", "800000", "1500000", "24700000");
    }
}

@Test
void exportMonthlyUsesCurrentCalendarMonth() throws Exception {
    try (XSSFWorkbook workbook = exportOk("transactions-monthly-20260924.xlsx", "period", "MONTHLY")) {
        assertEquals(List.of("Gaji Sep", "Investasi Sep", "Makan Sep"), detailNames(workbook));
        assertSummary(workbook, "2026-09-01", "2026-09-30",
            3, "12000000", "500000", "1500000", "10000000");
    }
}

@Test
void exportWeeklyUsesLastSevenDaysUntilToday() throws Exception {
    // 2026-09-24 hari Kamis, jadi rentangnya Kamis lalu 2026-09-17 s.d. Kamis ini 2026-09-24 (inklusif)
    createTransaction(user, makanan, "Makan Sebelum Rentang", "100000", LocalDateTime.of(2026, 9, 16, 23, 59, 59), false);
    createTransaction(user, makanan, "Makan Kamis Lalu", "200000", LocalDateTime.of(2026, 9, 17, 0, 0), false);
    createTransaction(user, makanan, "Makan Kamis Ini", "300000", LocalDateTime.of(2026, 9, 24, 23, 59, 59), false);
    createTransaction(user, makanan, "Makan Besok", "400000", LocalDateTime.of(2026, 9, 25, 0, 0), false);

    try (XSSFWorkbook workbook = exportOk("transactions-weekly-20260924.xlsx", "period", "WEEKLY")) {
        assertEquals(List.of("Makan Kamis Lalu", "Makan Kamis Ini"), detailNames(workbook));
        assertSummary(workbook, "2026-09-17", "2026-09-24",
            2, "0", "500000", "0", "-500000");
    }
}

@Test
void exportManualIncludesWholeEndDate() throws Exception {
    try (XSSFWorkbook workbook = exportOk("transactions-manual-20260924.xlsx",
            "period", "MANUAL", "startDate", "2026-08-31", "endDate", "2026-09-01")) {
        assertEquals(List.of("Makan Agu", "Gaji Sep"), detailNames(workbook));
        assertSummary(workbook, "2026-08-31", "2026-09-01",
            2, "12000000", "300000", "0", "11700000");
    }
}

@Test
void exportManualWithoutDatesReturnsBadRequest() throws Exception {
    exportError(status().isBadRequest(), exportRequest("period", "MANUAL"));
    exportError(status().isBadRequest(), exportRequest("period", "MANUAL", "startDate", "2026-09-01"));
}

@Test
void exportManualWithEndBeforeStartReturnsBadRequest() throws Exception {
    exportError(status().isBadRequest(),
        exportRequest("period", "MANUAL", "startDate", "2026-09-10", "endDate", "2026-09-01"));
}

@Test
void exportInvalidPeriodReturnsBadRequest() throws Exception {
    exportError(status().isBadRequest(), exportRequest("period", "DAILY"));
}


// ===================== category summary =====================

@Test
void exportGroupsCategorySummaryByTypeThenName() throws Exception {
    try (XSSFWorkbook workbook = exportOk("transactions-all-20260924.xlsx")) {
        assertEquals(List.of(
            List.of("1", "Makanan", "EXPENSE", "2", "800,000"),
            List.of("2", "Gaji", "INCOME", "5", "36,000,000"),
            List.of("3", "Investasi", "SAVING", "1", "1,500,000")
        ), categoryRows(workbook));
    }
}


// ===================== data kosong =====================

@Test
void exportWithoutTransactionsReturnsEmptyWorkbook() throws Exception {
    transactionRepository.deleteAll();

    try (XSSFWorkbook workbook = exportOk("transactions-all-20260924.xlsx")) {
        assertEquals(List.of(), detailRows(workbook));
        assertEquals(List.of(), categoryRows(workbook));
        assertSummary(workbook, "-", "-", 0, "0", "0", "0", "0");
    }
}


// ===================== batas baris =====================

@Test
void exportBeyondTemplateRowsKeepsNumberFormula() throws Exception {
    int extra = TEMPLATE_FORMULA_ROWS - SEEDED_ACTIVE_ROWS + 3;
    createManyTransactions(extra);

    try (XSSFWorkbook workbook = exportOk("transactions-all-20260924.xlsx")) {
        List<List<String>> rows = detailRows(workbook);
        int total = SEEDED_ACTIVE_ROWS + extra;
        assertEquals(total, rows.size());
        assertEquals(String.valueOf(total), rows.get(total - 1).get(0));

        Row lastRow = workbook.getSheet("Transaction").getRow(DETAIL_FIRST_ROW + total - 1);
        assertEquals(CellType.FORMULA, lastRow.getCell(0).getCellType());
    }
}

@Test
void exportExactlyAtLimitSucceeds() throws Exception {
    createManyTransactions(MAX_ROWS - SEEDED_ACTIVE_ROWS);

    try (XSSFWorkbook workbook = exportOk("transactions-all-20260924.xlsx")) {
        assertEquals(MAX_ROWS, detailRows(workbook).size());
    }
}

@Test
void exportAboveLimitReturnsBadRequest() throws Exception {
    createManyTransactions(MAX_ROWS - SEEDED_ACTIVE_ROWS + 1);

    exportError(status().isBadRequest(), exportRequest());
}


// ===================== auth =====================

@Test
void exportUnauthorized() throws Exception {
    mockMvc.perform(get(EXPORT_URL)).andExpect(status().isUnauthorized());
    mockMvc.perform(get(EXPORT_URL).header("Authorization", "Bearer invalid-token"))
        .andExpect(status().isUnauthorized());
}
}

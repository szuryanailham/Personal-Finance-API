package com.ilham.personal_finance_api.controller;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.ilham.personal_finance_api.AbstractIntegrationTest;
import com.ilham.personal_finance_api.dto.TransactionType;
import com.ilham.personal_finance_api.entity.Category;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.repository.CategoryRepository;
import com.ilham.personal_finance_api.repository.TransactionRepository;
import com.ilham.personal_finance_api.repository.UserRepository;
import com.ilham.personal_finance_api.security.BCrypt;

@SpringBootTest
@AutoConfigureMockMvc
public class ImportControllerTest extends AbstractIntegrationTest {

private static final String IMPORT_URL = "/api/transaction/import";
private static final String TEMPLATE_URL = "/api/transaction/import/template";
private static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
private static final List<String> TRANSACTION_HEADERS = List.of("no", "name", "category", "date", "amount");
// formula kolom no sesuai template; baris formula-only harus dianggap kosong
private static final String TRANSACTION_NO_FORMULA = "IF(B%d=\"\",\"\",ROW()-2)";
private static final int TRAILING_FORMULA_ROWS = 3;

@Autowired
private MockMvc mockMvc;

@Autowired
private TransactionRepository transactionRepository;

@Autowired
private UserRepository userRepository;

@Autowired
private CategoryRepository categoryRepository;

private User user;

private User otherUser;

@BeforeEach
void setUp() {
    transactionRepository.deleteAll();
    categoryRepository.deleteAll();
    userRepository.deleteAll();

    user = createUser("test@gmail.com", "test");
    otherUser = createUser("testb@gmail.com", "testB");
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

private Category createCategory(String name, TransactionType type, User owner, boolean isDeleted) {
    Category newCategory = new Category();
    newCategory.setName(name);
    newCategory.setType(type.name());
    newCategory.setUser(owner);
    newCategory.setDeleted(isDeleted);
    return categoryRepository.save(newCategory);
}



// [HELPER] workbook builder

// Membangun file import di memori. Baris null = baris kosong.
private static class ImportFile {
    private final List<Object[]> transactions = new ArrayList<>();
    private List<String> transactionHeaders = TRANSACTION_HEADERS;
    private boolean withTransactionSheet = true;

    ImportFile transaction(Object... values) {
        transactions.add(values);
        return this;
    }

    ImportFile blankTransaction() {
        transactions.add(null);
        return this;
    }

    ImportFile transactionHeaders(String... headers) {
        transactionHeaders = Arrays.asList(headers);
        return this;
    }

    ImportFile withoutTransactionSheet() {
        withTransactionSheet = false;
        return this;
    }

    byte[] build() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));

            if (withTransactionSheet) {
                Sheet transaction = workbook.createSheet("Transaction");
                transaction.createRow(0).createCell(0).setCellValue("TEMPLATE TRANSACTION");
                writeRow(transaction, 1, 0, transactionHeaders.toArray(), dateStyle);
                writeNumberedRows(transaction, 2, transactions, TRANSACTION_NO_FORMULA, dateStyle);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    // kolom 0 = formula no, data mulai kolom 1; ditambah baris formula-only seperti template
    private static void writeNumberedRows(Sheet sheet, int firstRow, List<Object[]> rows, String noFormula, CellStyle dateStyle) {
        for (int r = 0; r < rows.size() + TRAILING_FORMULA_ROWS; r++) {
            int rowIndex = firstRow + r;
            Object[] values = r < rows.size() ? rows.get(r) : null;
            writeRow(sheet, rowIndex, 1, values == null ? new Object[0] : values, dateStyle)
                .createCell(0).setCellFormula(String.format(noFormula, rowIndex + 1));
        }
    }

    private static Row writeRow(Sheet sheet, int rowIndex, int firstColumn, Object[] values, CellStyle dateStyle) {
        Row row = sheet.createRow(rowIndex);
        for (int c = 0; c < values.length; c++) {
            Object value = values[c];
            if (value == null) {
                continue;
            }
            Cell cell = row.createCell(firstColumn + c);
            if (value instanceof Number number) {
                cell.setCellValue(number.doubleValue());
            } else if (value instanceof LocalDate date) {
                cell.setCellValue(date);
                cell.setCellStyle(dateStyle);
            } else {
                cell.setCellValue(value.toString());
            }
        }
        return row;
    }
}

private ResultActions upload(String filename, byte[] content) throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", filename, XLSX_CONTENT_TYPE, content);
    return mockMvc.perform(multipart(IMPORT_URL).file(file).header("Authorization", "Bearer test"));
}

private ResultActions upload(ImportFile importFile) throws Exception {
    return upload("import.xlsx", importFile.build());
}

// category transaksi dicocokkan dengan category aktif milik user di database
private ImportFile specExample() {
    createCategory("Gaji", TransactionType.INCOME, user, false);
    createCategory("Makan & Minum", TransactionType.EXPENSE, user, false);
    createCategory("Tabungan Darurat", TransactionType.SAVING, user, false);
    return new ImportFile()
        .transaction("Gaji Bulanan", "Gaji", "2026-09-25", 5000000)
        .transaction("Makan Siang", "Makan & Minum", "2026-09-26", 35000)
        .transaction("Transfer Dana Darurat", "Tabungan Darurat", "2026-09-28", 500000)
        .transaction("Beli Kopi", "makan minum", "26/09/2026", "Rp 35.000")
        .transaction(null, "Gaji", "2026-09-29", 0);
}


// [TEST] auth & file validation

@Test
void importUnauthorized() throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "import.xlsx", XLSX_CONTENT_TYPE, specExample().build());
    mockMvc.perform(multipart(IMPORT_URL).file(file)).andExpect(status().isUnauthorized());
    mockMvc.perform(multipart(IMPORT_URL).file(file).header("Authorization", "Bearer invalid-token"))
        .andExpect(status().isUnauthorized());
}

@Test
void importWithoutFileIsBadRequest() throws Exception {
    mockMvc.perform(multipart(IMPORT_URL).header("Authorization", "Bearer test"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors").value("File is required"));
}

@Test
void importRejectsNonXlsxExtension() throws Exception {
    upload("import.csv", specExample().build())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors").value("File must be .xlsx"));
}

@Test
void importRejectsCorruptXlsx() throws Exception {
    upload("import.xlsx", "not an excel file".getBytes())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors").value("Invalid xlsx file"));
}

@Test
void importRejectsMissingSheet() throws Exception {
    upload(specExample().withoutTransactionSheet())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors").value("Sheet 'Transaction' not found"));
    assertEquals(0, transactionRepository.count());
}

@Test
void importRejectsUnexpectedHeader() throws Exception {
    upload(specExample().transactionHeaders("name", "category", "date", "amount"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors").value(
            "Invalid header on sheet 'Transaction', expected [no, name, category, date, amount]"));
    assertEquals(0, transactionRepository.count());
}


// [TEST] processing

@Test
void importValidFileReturnsParsedRowsWithoutSaving() throws Exception {
    Category gaji = createCategory("Gaji", TransactionType.INCOME, user, false);
    Category makan = createCategory("Makan & Minum", TransactionType.EXPENSE, user, false);
    ImportFile file = new ImportFile()
        .transaction("Gaji Bulanan", "Gaji", "2026-09-25", 5000000)
        .transaction("Makan Siang", "Makan & Minum", "2026-09-26", 35000)
        .transaction("Makan Malam", "Makan & Minum", "2026-09-26", "42000");

    upload(file)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.message").value("Import file parsed successfully"))
        .andExpect(jsonPath("$.summary.totalRows").value(3))
        .andExpect(jsonPath("$.summary.valid").value(3))
        .andExpect(jsonPath("$.summary.invalid").value(0))
        .andExpect(jsonPath("$.summary.categories").doesNotExist())
        .andExpect(jsonPath("$.summary.transactions.totalRows").value(3))
        .andExpect(jsonPath("$.data.categories").doesNotExist())
        .andExpect(jsonPath("$.data.transactions[0].name").value("Gaji Bulanan"))
        .andExpect(jsonPath("$.data.transactions[0].category").value("Gaji"))
        .andExpect(jsonPath("$.data.transactions[0].categoryId").value(gaji.getId().toString()))
        .andExpect(jsonPath("$.data.transactions[0].categoryType").value("INCOME"))
        .andExpect(jsonPath("$.data.transactions[0].date").value("2026-09-25"))
        .andExpect(jsonPath("$.data.transactions[0].amount").value(5000000))
        .andExpect(jsonPath("$.data.transactions[0].valid").value(true))
        .andExpect(jsonPath("$.data.transactions[0].errors").isEmpty())
        .andExpect(jsonPath("$.data.transactions[1].categoryId").value(makan.getId().toString()))
        .andExpect(jsonPath("$.data.transactions[2].amount").value(42000));

    assertEquals(2, categoryRepository.count());
    assertEquals(0, transactionRepository.count());
}

@Test
void importDoesNotMatchDeletedOrOtherUsersCategories() throws Exception {
    createCategory("Lama", TransactionType.EXPENSE, user, true);
    createCategory("Bonus", TransactionType.INCOME, otherUser, false);

    upload(new ImportFile()
            .transaction("Belanja", "Lama", "2026-09-26", 10000)
            .transaction("Bonus Tahunan", "Bonus", "2026-09-26", 20000))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.invalid").value(2))
        .andExpect(jsonPath("$.data.transactions[0].valid").value(false))
        .andExpect(jsonPath("$.data.transactions[0].categoryId").doesNotExist())
        .andExpect(jsonPath("$.data.transactions[0].errors[0].error").value("Category not found"))
        .andExpect(jsonPath("$.data.transactions[1].errors[0].error").value("Category not found"));

    assertEquals(0, transactionRepository.count());
}

@Test
void importReturnsFieldErrorsForInvalidRows() throws Exception {
    upload(specExample())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.message").value("Import file parsed with some errors"))
        .andExpect(jsonPath("$.summary.totalRows").value(5))
        .andExpect(jsonPath("$.summary.valid").value(3))
        .andExpect(jsonPath("$.summary.invalid").value(2))
        .andExpect(jsonPath("$.summary.transactions.valid").value(3))
        .andExpect(jsonPath("$.summary.transactions.invalid").value(2))
        .andExpect(jsonPath("$.data.transactions[0].valid").value(true))
        .andExpect(jsonPath("$.data.transactions[3].valid").value(false))
        .andExpect(jsonPath("$.data.transactions[3].row").value(4))
        .andExpect(jsonPath("$.data.transactions[3].category").value("makan minum"))
        .andExpect(jsonPath("$.data.transactions[3].date").value("26/09/2026"))
        .andExpect(jsonPath("$.data.transactions[3].amount").value("Rp 35.000"))
        .andExpect(jsonPath("$.data.transactions[3].errors.length()").value(3))
        .andExpect(jsonPath("$.data.transactions[3].errors[0].fieldName").value("category"))
        .andExpect(jsonPath("$.data.transactions[3].errors[0].error").value("Category not found"))
        .andExpect(jsonPath("$.data.transactions[3].errors[1].fieldName").value("date"))
        .andExpect(jsonPath("$.data.transactions[3].errors[1].error").value("Invalid date format, expected yyyy-mm-dd"))
        .andExpect(jsonPath("$.data.transactions[3].errors[2].fieldName").value("amount"))
        .andExpect(jsonPath("$.data.transactions[3].errors[2].error").value("Amount must be a number without Rp, dots, or commas"))
        .andExpect(jsonPath("$.data.transactions[4].row").value(5))
        .andExpect(jsonPath("$.data.transactions[4].name").value(""))
        .andExpect(jsonPath("$.data.transactions[4].amount").value(0))
        .andExpect(jsonPath("$.data.transactions[4].errors.length()").value(2))
        .andExpect(jsonPath("$.data.transactions[4].errors[0].fieldName").value("name"))
        .andExpect(jsonPath("$.data.transactions[4].errors[0].error").value("Name is required"))
        .andExpect(jsonPath("$.data.transactions[4].errors[1].fieldName").value("amount"))
        .andExpect(jsonPath("$.data.transactions[4].errors[1].error").value("Amount must be greater than 0"));

    assertEquals(0, transactionRepository.count());
}

@Test
void importHandlesBlankRowsDateCellsAndInvalidDates() throws Exception {
    createCategory("Gaji", TransactionType.INCOME, user, false);
    ImportFile file = new ImportFile()
        .transaction("Gaji Bulanan", "Gaji", LocalDate.of(2026, 9, 25), 5000000)
        .blankTransaction()
        .transaction("Bonus", "Gaji", "2026-02-30", 100)
        .transaction("Kosong", null, null, null);

    upload(file)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.transactions.totalRows").value(3))
        .andExpect(jsonPath("$.summary.transactions.valid").value(1))
        .andExpect(jsonPath("$.data.transactions[0].date").value("2026-09-25"))
        .andExpect(jsonPath("$.data.transactions[0].errors").isEmpty())
        .andExpect(jsonPath("$.data.transactions[1].row").value(3))
        .andExpect(jsonPath("$.data.transactions[1].errors[0].error").value("Invalid date format, expected yyyy-mm-dd"))
        .andExpect(jsonPath("$.data.transactions[2].errors[0].error").value("Category is required"))
        .andExpect(jsonPath("$.data.transactions[2].errors[1].error").value("Date is required"))
        .andExpect(jsonPath("$.data.transactions[2].errors[2].error").value("Amount is required"));

    assertEquals(0, transactionRepository.count());
}

@Test
void importRejectsAmountTextWithSeparators() throws Exception {
    createCategory("Makan & Minum", TransactionType.EXPENSE, user, false);
    ImportFile file = new ImportFile()
        .transaction("Makan Siang", "Makan & Minum", "2026-09-26", "35.000")
        .transaction("Makan Malam", "Makan & Minum", "2026-09-26", "35,000")
        .transaction("Kopi", "Makan & Minum", "2026-09-26", "-5000");

    upload(file)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.transactions.invalid").value(3))
        .andExpect(jsonPath("$.data.transactions[0].amount").value("35.000"))
        .andExpect(jsonPath("$.data.transactions[0].errors[0].fieldName").value("amount"))
        .andExpect(jsonPath("$.data.transactions[0].errors[0].error")
            .value("Amount must be a number without Rp, dots, or commas"))
        .andExpect(jsonPath("$.data.transactions[1].errors[0].fieldName").value("amount"))
        .andExpect(jsonPath("$.data.transactions[2].errors[0].fieldName").value("amount"));

    assertEquals(0, transactionRepository.count());
}

@Test
void importDownloadedTemplateSucceeds() throws Exception {
    // baris contoh Transaction pada template memakai category Gaji dan Makan & Minum
    createCategory("Gaji", TransactionType.INCOME, user, false);
    createCategory("Makan & Minum", TransactionType.EXPENSE, user, false);

    byte[] template = mockMvc.perform(get(TEMPLATE_URL).header("Authorization", "Bearer test"))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsByteArray();

    upload("personal-finance-import-template.xlsx", template)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.transactions.totalRows").value(2))
        .andExpect(jsonPath("$.summary.invalid").value(0));

    assertEquals(0, transactionRepository.count());
}

}

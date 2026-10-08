package com.ilham.personal_finance_api.controller;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

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
public class ImportTemplateControllerTest extends AbstractIntegrationTest {

private static final String TEMPLATE_URL = "/api/transaction/import/template";
private static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

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

private void createCategory(String name, TransactionType type, User owner, boolean isDeleted) {
    Category newCategory = new Category();
    newCategory.setName(name);
    newCategory.setType(type.name());
    newCategory.setUser(owner);
    newCategory.setDeleted(isDeleted);
    categoryRepository.save(newCategory);
}

private XSSFWorkbook downloadTemplate() throws Exception {
    byte[] content = mockMvc.perform(get(TEMPLATE_URL).header("Authorization", "Bearer test"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", XLSX_CONTENT_TYPE))
        .andExpect(header().string("Content-Disposition",
            "attachment; filename=\"personal-finance-import-template.xlsx\""))
        .andReturn().getResponse().getContentAsByteArray();
    return new XSSFWorkbook(new ByteArrayInputStream(content));
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

@Test
void downloadTemplateUnauthorized() throws Exception {
    mockMvc.perform(get(TEMPLATE_URL)).andExpect(status().isUnauthorized());
    mockMvc.perform(get(TEMPLATE_URL).header("Authorization", "Bearer invalid-token"))
        .andExpect(status().isUnauthorized());
}

@Test
void downloadTemplateFillsTypeSheetFromEnum() throws Exception {
    try (XSSFWorkbook workbook = downloadTemplate()) {
        List<List<String>> types = readRows(workbook.getSheet("Type"), 1, 2);
        assertEquals(List.of(
            List.of("1", "EXPENSE"),
            List.of("2", "INCOME"),
            List.of("3", "SAVING")
        ), types);
    }
}

@Test
void downloadTemplateFillsCategorySheetWithActiveUserCategories() throws Exception {
    createCategory("Makanan", TransactionType.EXPENSE, user, false);
    createCategory("Gaji", TransactionType.INCOME, user, false);
    createCategory("Lama", TransactionType.EXPENSE, user, true);
    createCategory("Gaji B", TransactionType.INCOME, otherUser, false);

    try (XSSFWorkbook workbook = downloadTemplate()) {
        List<List<String>> categories = readRows(workbook.getSheet("Category"), 1, 4);
        assertEquals(List.of(
            List.of("1", "Makanan", "EXPENSE", ""),
            List.of("2", "Gaji", "INCOME", "")
        ), categories);

        // kolom no tetap formula agar baris yang ditambah user ikut bernomor
        Sheet category = workbook.getSheet("Category");
        assertEquals(CellType.FORMULA, category.getRow(1).getCell(0).getCellType());
        assertEquals(CellType.FORMULA, category.getRow(10).getCell(0).getCellType());
    }
}

@Test
void downloadTemplateWithoutCategoriesHasEmptyCategorySheet() throws Exception {
    try (XSSFWorkbook workbook = downloadTemplate()) {
        Sheet category = workbook.getSheet("Category");
        assertEquals("name", new DataFormatter().formatCellValue(category.getRow(0).getCell(1)));
        assertEquals(List.of(), readRows(category, 1, 4));
    }
}

@Test
void downloadTemplateKeepsSampleTransactionRows() throws Exception {
    try (XSSFWorkbook workbook = downloadTemplate()) {
        Sheet transaction = workbook.getSheet("Transaction");
        assertEquals("name", new DataFormatter().formatCellValue(transaction.getRow(1).getCell(1)));
        List<List<String>> samples = readRows(transaction, 2, 5);
        assertEquals(2, samples.size());
        assertEquals(List.of("1", "Gaji Bulanan", "Gaji", "2026-09-25", "5,000,000"), samples.get(0));
        assertEquals("Makan Siang", samples.get(1).get(1));
    }
}
}

package com.ilham.personal_finance_api.services;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ilham.personal_finance_api.dto.TransactionType;
import com.ilham.personal_finance_api.entity.Category;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.repository.CategoryRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ImportTemplateService {

    private static final String TEMPLATE_PATH = "import-templates/personal-finance-import-template.xlsx";
    private static final String SHEET_CATEGORY = "Category";
    private static final String SHEET_TYPE = "Type";
    private static final int TYPE_FIRST_DATA_ROW = 1;
    private static final int CATEGORY_FIRST_DATA_ROW = 1;
    private static final int TYPE_FIRST_DATA_COLUMN = 0;

    // kolom 0 sheet Category berisi formula no dari template, jadi data mulai kolom 1
    private static final int CATEGORY_NO_COLUMN = 0;
    private static final int CATEGORY_FIRST_DATA_COLUMN = 1;
    private static final String CATEGORY_NO_FORMULA = "IF(B%d=\"\",\"\",ROW()-1)";

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private WorkbookWriter workbookWriter;

    
    @Transactional(readOnly = true)
    public byte[] getTemplateImport(User user) {
        List<String> types = Arrays.stream(TransactionType.values()).map(Enum::name).toList();
        List<Category> categories = categoryRepository.findAllByUserAndIsDeletedFalseOrderByTypeAscNameAsc(user);

        try (InputStream in = new ClassPathResource(TEMPLATE_PATH).getInputStream();
             XSSFWorkbook workbook = new XSSFWorkbook(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet categorySheet = workbook.getSheet(SHEET_CATEGORY);
            workbookWriter.fillSheet(workbook.getSheet(SHEET_TYPE), TYPE_FIRST_DATA_ROW, TYPE_FIRST_DATA_COLUMN, toTypeRows(types));
            workbookWriter.fillSheet(categorySheet, CATEGORY_FIRST_DATA_ROW, CATEGORY_FIRST_DATA_COLUMN, toCategoryRows(categories));
            workbookWriter.ensureRowFormula(categorySheet, CATEGORY_FIRST_DATA_ROW, categories.size(),
                CATEGORY_NO_COLUMN, CATEGORY_NO_FORMULA);
            // cache hasil formula dari template sudah basi, minta Excel menghitung ulang saat dibuka
            workbook.setForceFormulaRecalculation(true);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Failed to build import template for user {}", user.getId(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate import template");
        }
    }

    private List<List<Object>> toTypeRows(List<String> types) {
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 0; i < types.size(); i++) {
            rows.add(List.of(i + 1, types.get(i)));
        }
        return rows;
    }

    private List<List<Object>> toCategoryRows(List<Category> categories) {
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 0; i < categories.size(); i++) {
            Category category = categories.get(i);
            rows.add(List.of(category.getName(), category.getType()));
        }
        return rows;
    }
}

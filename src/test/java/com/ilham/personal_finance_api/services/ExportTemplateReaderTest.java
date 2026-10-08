package com.ilham.personal_finance_api.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ExportTemplateReaderTest {

    private final ExportTemplateReader reader = new ExportTemplateReader();

    @Test
    void openLoadsBundledTemplate() throws Exception {
        try (XSSFWorkbook workbook = reader.open()) {
            assertEquals(2, workbook.getNumberOfSheets());
        }
    }

    @Test
    void validateRejectsMissingSheet() throws Exception {
        try (XSSFWorkbook workbook = reader.open()) {
            workbook.removeSheetAt(workbook.getSheetIndex(ExportTemplateReader.SHEET_TRANSACTION));

            assertInvalid(workbook);
        }
    }

    @Test
    void validateRejectsChangedDetailHeader() throws Exception {
        try (XSSFWorkbook workbook = reader.open()) {
            Sheet transaction = workbook.getSheet(ExportTemplateReader.SHEET_TRANSACTION);
            replaceText(transaction.getRow(ExportTemplateReader.DETAIL_HEADER_ROW), 5, "nominal");

            assertInvalid(workbook);
        }
    }

    @Test
    void validateRejectsShiftedSummaryLabel() throws Exception {
        try (XSSFWorkbook workbook = reader.open()) {
            Sheet summary = workbook.getSheet(ExportTemplateReader.SHEET_SUMMARY);
            replaceText(summary.getRow(ExportTemplateReader.SUMMARY_BALANCE_ROW), 0, "Saldo");

            assertInvalid(workbook);
        }
    }

    @Test
    void validateIgnoresCaseAndSurroundingSpaces() throws Exception {
        try (XSSFWorkbook workbook = reader.open()) {
            Sheet summary = workbook.getSheet(ExportTemplateReader.SHEET_SUMMARY);
            replaceText(summary.getRow(ExportTemplateReader.CATEGORY_HEADER_ROW), 3, "  total transaction ");

            assertDoesNotThrow(() -> reader.validate(workbook));
        }
    }

    // sel template berupa inline string; setCellValue tidak menimpa isinya, jadi sel dibuat ulang
    private void replaceText(Row row, int column, String value) {
        row.removeCell(row.getCell(column));
        row.createCell(column).setCellValue(value);
    }

    private void assertInvalid(XSSFWorkbook workbook) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> reader.validate(workbook));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatusCode());
    }
}

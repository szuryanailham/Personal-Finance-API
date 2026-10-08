package com.ilham.personal_finance_api.services;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import lombok.extern.slf4j.Slf4j;

// Memuat template export dan memastikan struktur sheet/header sesuai posisi yang ditulis TransactionExportService.
@Slf4j
@Component
public class ExportTemplateReader {

    private static final String TEMPLATE_PATH = "export-templates/transaction_export_template.xlsx";
    private static final String INVALID_TEMPLATE_MESSAGE = "Export template is invalid";

    public static final String SHEET_SUMMARY = "Summary";
    public static final String SHEET_TRANSACTION = "Transaction";

    // Summary: label di kolom A, nilai di kolom B (posisi 0-based)
    public static final int SUMMARY_LABEL_COLUMN = 0;
    public static final int SUMMARY_VALUE_COLUMN = 1;
    public static final int SUMMARY_START_DATE_ROW = 5;
    public static final int SUMMARY_END_DATE_ROW = 6;
    public static final int SUMMARY_COUNT_ROW = 7;
    public static final int SUMMARY_INCOME_ROW = 8;
    public static final int SUMMARY_EXPENSE_ROW = 9;
    public static final int SUMMARY_SAVING_ROW = 10;
    public static final int SUMMARY_BALANCE_ROW = 11;

    // Summary bagian B: kolom 0 berisi formula no, data mulai kolom 1
    public static final int CATEGORY_HEADER_ROW = 15;
    public static final int CATEGORY_FIRST_DATA_ROW = 16;
    public static final int CATEGORY_NO_COLUMN = 0;
    public static final int CATEGORY_FIRST_DATA_COLUMN = 1;
    public static final String CATEGORY_NO_FORMULA = "IF(B%d=\"\",\"\",ROW()-16)";

    // Transaction: kolom 0 berisi formula no, data mulai kolom 1
    public static final int DETAIL_HEADER_ROW = 1;
    public static final int DETAIL_FIRST_DATA_ROW = 2;
    public static final int DETAIL_NO_COLUMN = 0;
    public static final int DETAIL_FIRST_DATA_COLUMN = 1;
    public static final String DETAIL_NO_FORMULA = "IF(B%d=\"\",\"\",ROW()-2)";

    private static final List<String> SUMMARY_LABELS = List.of(
        "Start Date", "End Date", "Number of Transactions", "Total Income (INCOME)",
        "Total Expenses (EXPENSE)", "Total Savings (SAVING)", "Total Balance"
    );
    private static final List<String> CATEGORY_HEADERS = List.of(
        "no", "name", "type", "Total Transaction", "Total Amount"
    );
    private static final List<String> DETAIL_HEADERS = List.of(
        "no", "name", "category", "type", "date", "amount", "description"
    );

    private final DataFormatter formatter = new DataFormatter();

    public XSSFWorkbook open() {
        try (InputStream in = new ClassPathResource(TEMPLATE_PATH).getInputStream()) {
            XSSFWorkbook workbook = new XSSFWorkbook(in);
            try {
                validate(workbook);
                return workbook;
            } catch (ResponseStatusException e) {
                workbook.close();
                throw e;
            }
        } catch (IOException e) {
            log.error("Failed to load export template {}", TEMPLATE_PATH, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, INVALID_TEMPLATE_MESSAGE);
        }
    }

    public void validate(Workbook workbook) {
        Sheet summary = requireSheet(workbook, SHEET_SUMMARY);
        Sheet transaction = requireSheet(workbook, SHEET_TRANSACTION);

        requireColumn(summary, SUMMARY_START_DATE_ROW, SUMMARY_LABEL_COLUMN, SUMMARY_LABELS);
        requireRow(summary, CATEGORY_HEADER_ROW, CATEGORY_HEADERS);
        requireRow(transaction, DETAIL_HEADER_ROW, DETAIL_HEADERS);
    }

    private Sheet requireSheet(Workbook workbook, String name) {
        Sheet sheet = workbook.getSheet(name);
        if (sheet == null) {
            throw invalid("missing sheet " + name);
        }
        return sheet;
    }

    // label disusun ke bawah mulai firstRow pada satu kolom
    private void requireColumn(Sheet sheet, int firstRow, int column, List<String> expected) {
        List<String> actual = new ArrayList<>();
        for (int i = 0; i < expected.size(); i++) {
            actual.add(cellText(sheet.getRow(firstRow + i), column));
        }
        requireMatch(sheet, expected, actual);
    }

    // header disusun ke samping mulai kolom 0 pada satu baris
    private void requireRow(Sheet sheet, int rowIndex, List<String> expected) {
        Row row = sheet.getRow(rowIndex);
        List<String> actual = new ArrayList<>();
        for (int c = 0; c < expected.size(); c++) {
            actual.add(cellText(row, c));
        }
        requireMatch(sheet, expected, actual);
    }

    private void requireMatch(Sheet sheet, List<String> expected, List<String> actual) {
        for (int i = 0; i < expected.size(); i++) {
            if (!expected.get(i).equalsIgnoreCase(actual.get(i))) {
                throw invalid("sheet " + sheet.getSheetName() + " expected " + expected + " but found " + actual);
            }
        }
    }

    private String cellText(Row row, int column) {
        Cell cell = row == null ? null : row.getCell(column);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private ResponseStatusException invalid(String detail) {
        log.error("Export template {} is invalid: {}", TEMPLATE_PATH, detail);
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, INVALID_TEMPLATE_MESSAGE);
    }
}

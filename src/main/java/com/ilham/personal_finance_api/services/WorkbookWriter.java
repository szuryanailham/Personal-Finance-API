package com.ilham.personal_finance_api.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Component;

// Helper menulis data ke sheet template dengan tetap memakai style baris data pertama template.
@Component
public class WorkbookWriter {

    // menulis rows mulai firstColumn; kolom sebelum firstColumn (mis. formula no) tidak disentuh
    public void fillSheet(Sheet sheet, int firstDataRow, int firstColumn, List<List<Object>> rows) {
        List<CellStyle> columnStyles = captureRowStyles(sheet.getRow(firstDataRow));
        clearDataRows(sheet, firstDataRow, firstColumn);
        for (int r = 0; r < rows.size(); r++) {
            List<Object> values = rows.get(r);
            Row row = getOrCreateRow(sheet, firstDataRow + r);
            for (int c = 0; c < values.size(); c++) {
                int column = firstColumn + c;
                Cell cell = getOrCreateCell(row, column);
                applyStyle(cell, columnStyles, column);
                setCellValue(cell, values.get(c));
            }
        }
    }

    // template hanya menyediakan formula untuk sejumlah baris; baris data di luar itu diberi formula yang sama.
    // formulaPattern menerima nomor baris Excel (1-based) lewat %d
    public void ensureRowFormula(Sheet sheet, int firstDataRow, int rowCount, int column, String formulaPattern) {
        List<CellStyle> columnStyles = captureRowStyles(sheet.getRow(firstDataRow));
        for (int r = firstDataRow; r < firstDataRow + rowCount; r++) {
            Cell cell = getOrCreateCell(getOrCreateRow(sheet, r), column);
            if (cell.getCellType() != CellType.FORMULA) {
                applyStyle(cell, columnStyles, column);
                cell.setCellFormula(String.format(formulaPattern, r + 1));
            }
        }
    }

    public Row getOrCreateRow(Sheet sheet, int index) {
        Row row = sheet.getRow(index);
        return row != null ? row : sheet.createRow(index);
    }

    public Cell getOrCreateCell(Row row, int index) {
        Cell cell = row.getCell(index);
        return cell != null ? cell : row.createCell(index);
    }

    // null dikosongkan agar tidak tertulis "null"; tanggal & angka ditulis sebagai nilai asli supaya format sel berlaku
    public void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof BigDecimal decimal) {
            cell.setCellValue(decimal.doubleValue());
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof LocalDate date) {
            cell.setCellValue(date);
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }

    private void applyStyle(Cell cell, List<CellStyle> columnStyles, int column) {
        if (column < columnStyles.size() && columnStyles.get(column) != null) {
            cell.setCellStyle(columnStyles.get(column));
        }
    }

    private List<CellStyle> captureRowStyles(Row row) {
        List<CellStyle> styles = new ArrayList<>();
        if (row == null) {
            return styles;
        }
        for (int c = 0; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            styles.add(cell == null ? null : cell.getCellStyle());
        }
        return styles;
    }

    private void clearDataRows(Sheet sheet, int firstDataRow, int firstColumn) {
        for (int r = firstDataRow; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            row.forEach(cell -> {
                if (cell.getColumnIndex() >= firstColumn) {
                    cell.setBlank();
                }
            });
        }
    }
}

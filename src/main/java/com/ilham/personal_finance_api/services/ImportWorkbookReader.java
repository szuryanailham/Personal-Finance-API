package com.ilham.personal_finance_api.services;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import lombok.extern.slf4j.Slf4j;

// Membaca dan memvalidasi struktur file import; tidak menyentuh database.
@Slf4j
@Component
public class ImportWorkbookReader {

    public static final int MAX_DATA_ROWS = 1000;

    private static final String XLSX_EXTENSION = ".xlsx";
    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern DIGITS_ONLY = Pattern.compile("\\d+");

    private final DataFormatter formatter = new DataFormatter();

    // Data baris beserta posisinya di sheet (0-based)
    public record DataRow(int rowIndex, Row row) {}

    // value null jika kosong atau formatnya salah; text berisi isi sel apa adanya
    public record DateCell(String text, LocalDate value) {}

    // raw null jika kosong; value null jika bukan angka (teks harus digit saja)
    public record AmountCell(Object raw, BigDecimal value) {}

    public XSSFWorkbook open(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(XLSX_EXTENSION)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must be .xlsx");
        }

        try (InputStream in = file.getInputStream()) {
            return new XSSFWorkbook(in);
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to open import file {}", filename, e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid xlsx file");
        }
    }

    public Sheet requireSheet(Workbook workbook, String name) {
        Sheet sheet = workbook.getSheet(name);
        if (sheet == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sheet '" + name + "' not found");
        }
        return sheet;
    }

    public void requireHeaders(Sheet sheet, int headerRowIndex, List<String> expected) {
        Row headerRow = sheet.getRow(headerRowIndex);
        List<String> actual = new ArrayList<>();
        if (headerRow != null) {
            headerRow.forEach(cell -> {
                String value = formatter.formatCellValue(cell).trim();
                if (!value.isEmpty()) {
                    actual.add(value);
                }
            });
        }

        if (!actual.equals(expected)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Invalid header on sheet '" + sheet.getSheetName() + "', expected " + expected);
        }
    }

    // Mengembalikan baris yang tidak kosong pada kolom data; baris kosong dilewati
    public List<DataRow> readDataRows(Sheet sheet, int firstDataRow, int columnCount) {
        FormulaEvaluator evaluator = evaluator(sheet);
        List<DataRow> rows = new ArrayList<>();
        for (int r = firstDataRow; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null || isBlankRow(row, columnCount, evaluator)) {
                continue;
            }
            if (rows.size() == MAX_DATA_ROWS) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Sheet '" + sheet.getSheetName() + "' exceeds " + MAX_DATA_ROWS + " rows");
            }
            rows.add(new DataRow(r, row));
        }
        return rows;
    }

    public String readText(Row row, int column) {
        Cell cell = row.getCell(column);
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell, evaluator(row.getSheet())).trim();
    }

    public DateCell readDate(Row row, int column) {
        Cell cell = row.getCell(column);
        if (cell != null && resultType(cell) == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            LocalDate date = cell.getLocalDateTimeCellValue().toLocalDate();
            return new DateCell(date.toString(), date);
        }

        String text = readText(row, column);
        return new DateCell(text, parseDate(text));
    }

    public AmountCell readAmount(Row row, int column) {
        Cell cell = row.getCell(column);
        if (cell != null && resultType(cell) == CellType.NUMERIC) {
            BigDecimal value = toPlain(BigDecimal.valueOf(cell.getNumericCellValue()));
            return new AmountCell(value, value);
        }

        String text = readText(row, column);
        if (text.isEmpty()) {
            return new AmountCell(null, null);
        }
        BigDecimal value = parseAmount(text);
        return new AmountCell(value != null ? value : text, value);
    }

    private boolean isBlankRow(Row row, int columnCount, FormulaEvaluator evaluator) {
        for (int c = 0; c < columnCount; c++) {
            Cell cell = row.getCell(c);
            if (cell != null && !formatter.formatCellValue(cell, evaluator).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private FormulaEvaluator evaluator(Sheet sheet) {
        return sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
    }

    private CellType resultType(Cell cell) {
        return cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
    }

    private LocalDate parseDate(String text) {
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(text, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // amount berupa teks hanya boleh digit; "35.000" ditolak agar tidak terbaca sebagai 35
    private BigDecimal parseAmount(String text) {
        return DIGITS_ONLY.matcher(text).matches() ? new BigDecimal(text) : null;
    }

    // 5000000.0 -> 5000000, tanpa notasi eksponen (5E+6)
    private BigDecimal toPlain(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}

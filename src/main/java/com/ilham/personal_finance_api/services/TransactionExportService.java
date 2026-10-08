package com.ilham.personal_finance_api.services;

import static com.ilham.personal_finance_api.services.ExportTemplateReader.CATEGORY_FIRST_DATA_COLUMN;
import static com.ilham.personal_finance_api.services.ExportTemplateReader.CATEGORY_FIRST_DATA_ROW;
import static com.ilham.personal_finance_api.services.ExportTemplateReader.CATEGORY_NO_COLUMN;
import static com.ilham.personal_finance_api.services.ExportTemplateReader.CATEGORY_NO_FORMULA;
import static com.ilham.personal_finance_api.services.ExportTemplateReader.DETAIL_FIRST_DATA_COLUMN;
import static com.ilham.personal_finance_api.services.ExportTemplateReader.DETAIL_FIRST_DATA_ROW;
import static com.ilham.personal_finance_api.services.ExportTemplateReader.DETAIL_NO_COLUMN;
import static com.ilham.personal_finance_api.services.ExportTemplateReader.DETAIL_NO_FORMULA;
import static com.ilham.personal_finance_api.services.ExportTemplateReader.SUMMARY_START_DATE_ROW;
import static com.ilham.personal_finance_api.services.ExportTemplateReader.SUMMARY_VALUE_COLUMN;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ilham.personal_finance_api.dto.TransactionExportFilter;
import com.ilham.personal_finance_api.dto.TransactionExportPeriod;
import com.ilham.personal_finance_api.dto.TransactionType;
import com.ilham.personal_finance_api.dto.exporter.CategorySummary;
import com.ilham.personal_finance_api.dto.exporter.ExportDateRange;
import com.ilham.personal_finance_api.dto.exporter.ExportFile;
import com.ilham.personal_finance_api.dto.exporter.ExportSummary;
import com.ilham.personal_finance_api.entity.Category;
import com.ilham.personal_finance_api.entity.Transaction;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.repository.TransactionRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TransactionExportService {

    private static final String EMPTY_DATE = "-";
    private static final String FILENAME_FORMAT = "transactions-%s-%s.xlsx";

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private ExportTemplateReader exportTemplateReader;

    @Autowired
    private WorkbookWriter workbookWriter;

    @Autowired
    private Clock clock;

    @Value("${app.export.max-rows:10000}")
    private int maxRows;

    // [MAIN] export transaksi aktif (is_deleted = false) milik user ke xlsx berdasarkan template

    @Transactional(readOnly = true)
    public ExportFile export(User user, TransactionExportFilter filter) {
        TransactionExportPeriod period = filter.getPeriod() == null ? TransactionExportPeriod.ALL : filter.getPeriod();
        ExportDateRange range = resolveRange(period, filter.getStartDate(), filter.getEndDate());

        List<Transaction> transactions = loadTransactions(user, range);
        ExportSummary summary = summarize(range, transactions);
        List<CategorySummary> categories = summarizeByCategory(transactions);

        byte[] content = buildWorkbook(user, summary, categories, transactions);
        return new ExportFile(filename(period), content);
    }

    // [HELPER]
    private ExportDateRange resolveRange(TransactionExportPeriod period, LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now(clock);

        return switch (period) {
            case ALL -> ExportDateRange.unbounded();
            case YEARLY -> {
                LocalDate start = today.withDayOfYear(1);
                yield new ExportDateRange(start.atStartOfDay(), start.plusYears(1).atStartOfDay());
            }
            case MONTHLY -> {
                LocalDate start = today.withDayOfMonth(1);
                yield new ExportDateRange(start.atStartOfDay(), start.plusMonths(1).atStartOfDay());
            }
            // hari yang sama minggu lalu s.d. hari ini (inklusif), misal Rabu lalu s.d. Rabu ini
            case WEEKLY -> new ExportDateRange(today.minusWeeks(1).atStartOfDay(), today.plusDays(1).atStartOfDay());
            case MANUAL -> resolveManualRange(startDate, endDate);
        };
    }

    // [HELPER] endDate inklusif, jadi batas akhir query adalah awal hari berikutnya
    private ExportDateRange resolveManualRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate and endDate are required for MANUAL period");
        }

        if (endDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate must not be before startDate");
        }

        return new ExportDateRange(startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());
    }

    // [HELPER] ambil maxRows + 1 untuk mendeteksi data yang melebihi batas tanpa count query terpisah
    private List<Transaction> loadTransactions(User user, ExportDateRange range) {
        List<Transaction> transactions = transactionRepository.findAllActiveForExport(
            user, range.start(), range.end(), Limit.of(maxRows + 1));

        if (transactions.size() > maxRows) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Too many transactions to export (max " + maxRows + "), please narrow the date range"
            );
        }
        return transactions;
    }

    // [HELPER] periode ALL memakai tanggal transaksi pertama & terakhir (data sudah urut tanggal)
    private ExportSummary summarize(ExportDateRange range, List<Transaction> transactions) {
        LocalDate startDate;
        LocalDate endDate;
        if (range.isUnbounded()) {
            startDate = transactions.isEmpty() ? null : transactions.getFirst().getTransactionDate().toLocalDate();
            endDate = transactions.isEmpty() ? null : transactions.getLast().getTransactionDate().toLocalDate();
        } else {
            startDate = range.start().toLocalDate();
            endDate = range.end().toLocalDate().minusDays(1);
        }

        return new ExportSummary(
            startDate,
            endDate,
            transactions.size(),
            sumByType(transactions, TransactionType.INCOME),
            sumByType(transactions, TransactionType.EXPENSE),
            sumByType(transactions, TransactionType.SAVING)
        );
    }

    // [HELPER]
    private BigDecimal sumByType(List<Transaction> transactions, TransactionType type) {
        return transactions.stream()
            .filter(transaction -> type.name().equals(transaction.getCategory().getType()))
            .map(Transaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // [HELPER] dikelompokkan per category (bukan per nama) lalu diurutkan berdasarkan type dan name
    private List<CategorySummary> summarizeByCategory(List<Transaction> transactions) {
        Map<UUID, List<Transaction>> byCategory = new LinkedHashMap<>();
        transactions.forEach(transaction ->
            byCategory.computeIfAbsent(transaction.getCategory().getId(), id -> new ArrayList<>()).add(transaction));

        return byCategory.values().stream()
            .map(this::toCategorySummary)
            .sorted(Comparator.comparing(CategorySummary::type).thenComparing(CategorySummary::name))
            .toList();
    }

    // [HELPER]
    private CategorySummary toCategorySummary(List<Transaction> transactions) {
        Category category = transactions.getFirst().getCategory();
        BigDecimal total = transactions.stream()
            .map(Transaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CategorySummary(category.getName(), category.getType(), transactions.size(), total);
    }

    // [HELPER]
    private byte[] buildWorkbook(User user, ExportSummary summary, List<CategorySummary> categories,
            List<Transaction> transactions) {
        try (XSSFWorkbook workbook = exportTemplateReader.open();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet summarySheet = workbook.getSheet(ExportTemplateReader.SHEET_SUMMARY);
            writeSummary(summarySheet, summary);
            writeCategories(summarySheet, categories);
            writeDetails(workbook.getSheet(ExportTemplateReader.SHEET_TRANSACTION), transactions);
            // kolom no berupa formula; minta Excel menghitung ulang saat dibuka
            workbook.setForceFormulaRecalculation(true);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Failed to build transaction export for user {}", user.getId(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate export file");
        }
    }

    // [HELPER] urutan nilai mengikuti label Summary mulai baris Start Date
    private void writeSummary(Sheet sheet, ExportSummary summary) {
        List<Object> values = Arrays.asList(
            summary.startDate() == null ? EMPTY_DATE : summary.startDate(),
            summary.endDate() == null ? EMPTY_DATE : summary.endDate(),
            summary.transactionCount(),
            summary.totalIncome(),
            summary.totalExpense(),
            summary.totalSaving(),
            summary.totalBalance()
        );

        for (int i = 0; i < values.size(); i++) {
            workbookWriter.setCellValue(
                workbookWriter.getOrCreateCell(workbookWriter.getOrCreateRow(sheet, SUMMARY_START_DATE_ROW + i), SUMMARY_VALUE_COLUMN),
                values.get(i)
            );
        }
    }

    // [HELPER]
    private void writeCategories(Sheet sheet, List<CategorySummary> categories) {
        List<List<Object>> rows = categories.stream()
            .map(category -> List.<Object>of(
                category.name(), category.type(), category.transactionCount(), category.totalAmount()))
            .toList();

        workbookWriter.fillSheet(sheet, CATEGORY_FIRST_DATA_ROW, CATEGORY_FIRST_DATA_COLUMN, rows);
        workbookWriter.ensureRowFormula(sheet, CATEGORY_FIRST_DATA_ROW, rows.size(), CATEGORY_NO_COLUMN, CATEGORY_NO_FORMULA);
    }

    // [HELPER] description boleh null, jadi memakai Arrays.asList (List.of menolak null)
    private void writeDetails(Sheet sheet, List<Transaction> transactions) {
        List<List<Object>> rows = transactions.stream()
            .map(transaction -> Arrays.<Object>asList(
                transaction.getTransactionName(),
                transaction.getCategory().getName(),
                transaction.getCategory().getType(),
                transaction.getTransactionDate().toLocalDate(),
                transaction.getAmount(),
                transaction.getDescription()))
            .toList();

        workbookWriter.fillSheet(sheet, DETAIL_FIRST_DATA_ROW, DETAIL_FIRST_DATA_COLUMN, rows);
        workbookWriter.ensureRowFormula(sheet, DETAIL_FIRST_DATA_ROW, rows.size(), DETAIL_NO_COLUMN, DETAIL_NO_FORMULA);
    }

    // [HELPER]
    private String filename(TransactionExportPeriod period) {
        return String.format(FILENAME_FORMAT,
            period.name().toLowerCase(Locale.ROOT),
            LocalDate.now(clock).format(DateTimeFormatter.BASIC_ISO_DATE));
    }
}

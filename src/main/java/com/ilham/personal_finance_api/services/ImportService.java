package com.ilham.personal_finance_api.services;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.ilham.personal_finance_api.dto.importer.ImportData;
import com.ilham.personal_finance_api.dto.importer.ImportFieldError;
import com.ilham.personal_finance_api.dto.importer.ImportResponse;
import com.ilham.personal_finance_api.dto.importer.ImportSheetSummary;
import com.ilham.personal_finance_api.dto.importer.ImportSummary;
import com.ilham.personal_finance_api.dto.importer.TransactionImportRow;
import com.ilham.personal_finance_api.entity.Category;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.repository.CategoryRepository;
import com.ilham.personal_finance_api.services.ImportWorkbookReader.AmountCell;
import com.ilham.personal_finance_api.services.ImportWorkbookReader.DataRow;
import com.ilham.personal_finance_api.services.ImportWorkbookReader.DateCell;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ImportService {

    private static final String SHEET_TRANSACTION = "Transaction";

    private static final List<String> TRANSACTION_HEADERS = List.of("no", "name", "category", "date", "amount");

    private static final int TRANSACTION_HEADER_ROW = 1;

    // kolom pada sheet Transaction (kolom 0 = no, tidak dibaca)
    private static final int TRANSACTION_COL_NAME = 1;
    private static final int TRANSACTION_COL_CATEGORY = 2;
    private static final int TRANSACTION_COL_DATE = 3;
    private static final int TRANSACTION_COL_AMOUNT = 4;

    private static final int MAX_NAME_LENGTH = 100;

    private static final String MESSAGE_SUCCESS = "Import file parsed successfully";
    private static final String MESSAGE_WITH_ERRORS = "Import file parsed with some errors";

    @Autowired
    private ImportWorkbookReader reader;

    @Autowired
    private CategoryRepository categoryRepository;

    // [MAIN] konversi file xlsx ke JSON untuk preview; tidak menyimpan apa pun ke database.
    // category dicocokkan dengan category aktif milik user, baris bermasalah ditandai lewat errors.
    @Transactional(readOnly = true)
    public ImportResponse importFile(User user, MultipartFile file) {
        try (XSSFWorkbook workbook = reader.open(file)) {
            Sheet transactionSheet = reader.requireSheet(workbook, SHEET_TRANSACTION);
            reader.requireHeaders(transactionSheet, TRANSACTION_HEADER_ROW, TRANSACTION_HEADERS);

            int transactionFirstRow = TRANSACTION_HEADER_ROW + 1;
            List<DataRow> transactionRows = reader.readDataRows(transactionSheet, transactionFirstRow, TRANSACTION_HEADERS.size());

            Map<String, Category> categoriesByName = loadActiveCategories(user);
            List<TransactionImportRow> transactionResults = parseTransactions(transactionRows, transactionFirstRow, categoriesByName);

            return buildResponse(transactionResults);
        } catch (IOException e) {
            log.error("Failed to close import workbook for user {}", user.getId(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to process import file");
        }
    }

    // [HELPER] category aktif milik user, dikunci berdasarkan nama
    private Map<String, Category> loadActiveCategories(User user) {
        return categoryRepository.findAllByUserAndIsDeletedFalseOrderByTypeAscNameAsc(user).stream()
            .collect(Collectors.toMap(Category::getName, Function.identity(), (first, second) -> first, HashMap::new));
    }

    // [HELPER] transaction
    private List<TransactionImportRow> parseTransactions(List<DataRow> rows, int firstDataRow, Map<String, Category> categoriesByName) {
        List<TransactionImportRow> results = new ArrayList<>();
        for (DataRow dataRow : rows) {
            Row row = dataRow.row();
            String name = reader.readText(row, TRANSACTION_COL_NAME);
            String categoryName = reader.readText(row, TRANSACTION_COL_CATEGORY);
            DateCell date = reader.readDate(row, TRANSACTION_COL_DATE);
            AmountCell amount = reader.readAmount(row, TRANSACTION_COL_AMOUNT);

            Category category = categoriesByName.get(categoryName);
            List<ImportFieldError> errors = validateTransaction(name, categoryName, category, date, amount);

            results.add(TransactionImportRow.builder()
                .row(dataRow.rowIndex() - firstDataRow + 1)
                .name(name)
                .category(categoryName)
                .categoryId(category != null ? category.getId() : null)
                .categoryType(category != null ? category.getType() : null)
                .date(date.text())
                .amount(amount.raw())
                .valid(errors.isEmpty())
                .errors(errors)
                .build());
        }
        return results;
    }

    private List<ImportFieldError> validateTransaction(String name, String categoryName, Category category, DateCell date, AmountCell amount) {
        List<ImportFieldError> errors = new ArrayList<>();
        validateName(name, errors);

        if (categoryName.isEmpty()) {
            errors.add(fieldError("category", "Category is required"));
        } else if (category == null) {
            errors.add(fieldError("category", "Category not found"));
        }

        if (date.text().isEmpty()) {
            errors.add(fieldError("date", "Date is required"));
        } else if (date.value() == null) {
            errors.add(fieldError("date", "Invalid date format, expected yyyy-mm-dd"));
        }

        if (amount.raw() == null) {
            errors.add(fieldError("amount", "Amount is required"));
        } else if (amount.value() == null) {
            errors.add(fieldError("amount", "Amount must be a number without Rp, dots, or commas"));
        } else if (amount.value().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(fieldError("amount", "Amount must be greater than 0"));
        }
        return errors;
    }

    // [HELPER] validasi bersama
    private void validateName(String name, List<ImportFieldError> errors) {
        if (name.isEmpty()) {
            errors.add(fieldError("name", "Name is required"));
        } else if (name.length() > MAX_NAME_LENGTH) {
            errors.add(fieldError("name", "Name must be at most " + MAX_NAME_LENGTH + " characters"));
        }
    }

    private ImportFieldError fieldError(String fieldName, String error) {
        return ImportFieldError.builder().fieldName(fieldName).error(error).build();
    }

    // [HELPER] summary
    private ImportResponse buildResponse(List<TransactionImportRow> transactions) {
        ImportSheetSummary transactionSummary = summarize(transactions.stream().map(TransactionImportRow::getErrors).toList());
        int invalid = transactionSummary.getInvalid();

        ImportSummary summary = ImportSummary.builder()
            .totalRows(transactionSummary.getTotalRows())
            .valid(transactionSummary.getValid())
            .invalid(invalid)
            .transactions(transactionSummary)
            .build();

        return ImportResponse.builder()
            .code(HttpStatus.OK.value())
            .message(invalid == 0 ? MESSAGE_SUCCESS : MESSAGE_WITH_ERRORS)
            .summary(summary)
            .data(ImportData.builder().transactions(transactions).build())
            .build();
    }

    private ImportSheetSummary summarize(List<List<ImportFieldError>> rowErrors) {
        int invalid = (int) rowErrors.stream().filter(errors -> !errors.isEmpty()).count();
        return ImportSheetSummary.builder()
            .totalRows(rowErrors.size())
            .valid(rowErrors.size() - invalid)
            .invalid(invalid)
            .build();
    }
}

package com.ilham.personal_finance_api.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ilham.personal_finance_api.dto.BulkCreateTransactionRequest;
import com.ilham.personal_finance_api.dto.BulkTransactionItem;
import com.ilham.personal_finance_api.dto.BulkDeleteTransactionRequest;
import com.ilham.personal_finance_api.dto.BulkDeleteTransactionResponse;
import com.ilham.personal_finance_api.dto.BulkUpdateTransactionRequest;
import com.ilham.personal_finance_api.dto.CategoryResponse;
import com.ilham.personal_finance_api.dto.CreateTransactionRequest;
import com.ilham.personal_finance_api.dto.CreateTransactionResponse;
import com.ilham.personal_finance_api.dto.TransactionDatePeriod;
import com.ilham.personal_finance_api.dto.TransactionFilter;
import com.ilham.personal_finance_api.dto.TransactionResponse;
import com.ilham.personal_finance_api.dto.TransactionSort;
import com.ilham.personal_finance_api.dto.TransactionStatFilter;
import com.ilham.personal_finance_api.dto.TransactionStateResponse;
import com.ilham.personal_finance_api.dto.TransactionStatisticResponse;
import com.ilham.personal_finance_api.dto.TransactionStatisticResponse.TransactionPeriodItemResponse;
import com.ilham.personal_finance_api.dto.TransactionType;
import com.ilham.personal_finance_api.dto.UpdateTransactionRequest;
import com.ilham.personal_finance_api.entity.Category;
import com.ilham.personal_finance_api.entity.Transaction;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.repository.CategoryRepository;
import com.ilham.personal_finance_api.repository.TransactionRepository;

@Service
public class TransactionService {

    private static final int PERCENTAGE_SCALE = 2;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransactionCodeGenerator transactionCodeGenerator;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private Clock clock;

    // [MAIN] create transaction

    @Transactional
    public CreateTransactionResponse create(User user, CreateTransactionRequest request) {
        validationService.validate(request);

        Category category = categoryRepository.findByIdAndUser(request.getCategoryId(), user)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

        String transactionCode = transactionCodeGenerator.generate();
        Transaction transaction = new Transaction();
        transaction.setTransactionName(request.getTransactonName());
        transaction.setTransactionCode(transactionCode);
        transaction.setCategory(category);
        transaction.setUser(user);
        transaction.setAmount(request.getAmount());
        transaction.setDescription(request.getDescription());
        transaction.setTransactionDate(request.getDate().atStartOfDay());

        transactionRepository.save(transaction);

        CreateTransactionResponse response = new CreateTransactionResponse();
        response.setId(transaction.getId());
        response.setTransactionName(transaction.getTransactionName());
        response.setAmount(transaction.getAmount());
        response.setDescription(transaction.getDescription());
        response.setCategoryId(category.getId());
        response.setDate(request.getDate());
        return response;
    }

    // [MAIN] bulk create transaction (setelah preview import)
    // all-or-nothing: jika ada category yang tidak valid, tidak ada transaksi yang disimpan.

    @Transactional
    public List<TransactionResponse> bulkCreate(User user, BulkCreateTransactionRequest request) {
        validationService.validate(request);

        List<BulkTransactionItem> items = request.getTransactions();
        Set<UUID> categoryIds = items.stream()
            .map(BulkTransactionItem::getCategoryId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, Category> categoriesById = loadCategories(user, categoryIds);
        List<String> transactionCodes = transactionCodeGenerator.generate(items.size());

        List<Transaction> transactions = IntStream.range(0, items.size())
            .mapToObj(i -> toTransaction(user, items.get(i), categoriesById.get(items.get(i).getCategoryId()), transactionCodes.get(i)))
            .toList();

        return transactionRepository.saveAll(transactions).stream()
            .map(transaction -> toTransactionResponse(transaction, transaction.getCategory()))
            .toList();
    }

    // [HELPER] category aktif milik user; gagal jika ada categoryId yang tidak ditemukan
    private Map<UUID, Category> loadCategories(User user, Set<UUID> categoryIds) {
        if (categoryIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Category> categoriesById = categoryRepository.findAllByIdInAndUserAndIsDeletedFalse(categoryIds, user).stream()
            .collect(Collectors.toMap(Category::getId, Function.identity()));

        List<String> missingIds = categoryIds.stream()
            .filter(id -> !categoriesById.containsKey(id))
            .map(UUID::toString)
            .toList();

        if (!missingIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found: " + String.join(", ", missingIds));
        }
        return categoriesById;
    }

    // [HELPER]
    private Transaction toTransaction(User user, BulkTransactionItem item, Category category, String transactionCode) {
        Transaction transaction = new Transaction();
        transaction.setTransactionName(item.getName());
        transaction.setTransactionCode(transactionCode);
        transaction.setCategory(category);
        transaction.setUser(user);
        transaction.setAmount(item.getAmount());
        transaction.setDescription(item.getDescription());
        transaction.setTransactionDate(item.getDate().atStartOfDay());
        return transaction;
    }

    // [MAIN] delete transaction
@Transactional
public void delete(User user, String transactionCode) {
    Transaction transaction = transactionRepository
            .findByUserAndTransactionCodeAndIsDeletedFalse(user, transactionCode)
            .orElseThrow(() ->
                    new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Transaction not found"
                    )
            );

    transaction.setDeleted(true);
}

    // [MAIN] bulk delete transaction (soft delete)
    // partial: code yang tidak ditemukan / sudah terhapus di-skip, sisanya tetap dihapus.
    @Transactional
    public BulkDeleteTransactionResponse bulkDelete(User user, BulkDeleteTransactionRequest request) {
        validationService.validate(request);

        Set<String> requestedCodes = new LinkedHashSet<>(request.getTransactionCodes());

        Set<String> deletableCodes = transactionRepository.findAllByUserAndTransactionCodeIn(user, requestedCodes).stream()
            .filter(transaction -> !transaction.isDeleted())
            .map(Transaction::getTransactionCode)
            .collect(Collectors.toSet());

        if (!deletableCodes.isEmpty()) {
            transactionRepository.softDeleteAllByUserAndTransactionCodeIn(user, deletableCodes);
        }

        Map<Boolean, List<String>> partitioned = requestedCodes.stream()
            .collect(Collectors.partitioningBy(deletableCodes::contains));

        BulkDeleteTransactionResponse response = new BulkDeleteTransactionResponse();
        response.setDeletedCount(partitioned.get(true).size());
        response.setDeletedCodes(partitioned.get(true));
        response.setSkippedCodes(partitioned.get(false));
        return response;
    }


    // [MAIN] bulk update transaction (partial update)
    // field yang sama diterapkan ke semua code; code duplikat di-dedupe.
    // all-or-nothing: jika ada code / category yang tidak valid, tidak ada transaksi yang diubah.
    @Transactional
    public int bulkUpdate(User user, BulkUpdateTransactionRequest request) {
        validationService.validate(request);

        if (!request.hasAnyField()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one field to update must be provided");
        }

        Set<String> codes = new LinkedHashSet<>(request.getTransactionCodes());
        Map<String, Transaction> transactionsByCode = loadActiveTransactions(user, codes);

        Category category = request.getCategoryId() == null
            ? null
            : loadCategories(user, Set.of(request.getCategoryId())).get(request.getCategoryId());

        transactionsByCode.values().forEach(transaction -> applyUpdate(transaction, request, category));
        return transactionsByCode.size();
    }

    // [HELPER] 
    private Map<String, Transaction> loadActiveTransactions(User user, Set<String> codes) {
        Map<String, Transaction> transactionsByCode = transactionRepository.findAllByUserAndTransactionCodeIn(user, codes).stream()
            .filter(transaction -> !transaction.isDeleted())
            .collect(Collectors.toMap(Transaction::getTransactionCode, Function.identity()));

        List<String> missingCodes = codes.stream()
            .filter(code -> !transactionsByCode.containsKey(code))
            .toList();

        if (!missingCodes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found: " + String.join(", ", missingCodes));
        }
        return transactionsByCode;
    }

    // [HELPER] hanya field yang dikirim (non-null) yang diubah
    private void applyUpdate(Transaction transaction, BulkUpdateTransactionRequest request, Category category) {
        if (request.getTransactionName() != null) {
            transaction.setTransactionName(request.getTransactionName());
        }
        if (request.getDescription() != null) {
            transaction.setDescription(request.getDescription());
        }
        if (category != null) {
            transaction.setCategory(category);
        }
        if (request.getDate() != null) {
            transaction.setTransactionDate(request.getDate().atStartOfDay());
        }
    }

// [MAIN] update transaction

@Transactional
public TransactionResponse update(User user, String transactionCode, UpdateTransactionRequest request) {

    validationService.validate(request);

    Transaction transaction = transactionRepository
        .findByUserAndTransactionCodeAndIsDeletedFalse(user, transactionCode)
        .orElseThrow(() ->
            new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Transaction not found"
            )
        );

    if (request.getTransactonName() != null) {
        transaction.setTransactionName(request.getTransactonName());
    }

    if (request.getCategoryId() != null) {
        Category category = categoryRepository
            .findByIdAndUser(request.getCategoryId(), user)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Category not found"
                )
            );

        transaction.setCategory(category);
    }

    if (request.getDescription() != null) {
        transaction.setDescription(request.getDescription());
    }

    if (request.getAmount() != null) {
        transaction.setAmount(request.getAmount());
    }

    if (request.getDate() != null) {
        transaction.setTransactionDate(request.getDate().atStartOfDay());
    }

    return toTransactionResponse(transaction, transaction.getCategory());
}


// [MAIN] get single transaction

@Transactional (readOnly = true)
public TransactionResponse get(User user , String transactionCode) {

    Transaction transaction = transactionRepository.findByUserAndTransactionCodeAndIsDeletedFalse(user, transactionCode).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
    return toTransactionResponse(transaction, transaction.getCategory());
}


// [MAIN] get all transaction

 @Transactional (readOnly = true)
 public Page<TransactionResponse> getAll(User user, int skip, int limit, TransactionFilter filter) {

     if (skip < 0) {
         throw new ResponseStatusException(
             HttpStatus.BAD_REQUEST,
             "Skip must be greater than or equal to 0"
         );
     }

     if (limit <= 0) {
         throw new ResponseStatusException(
             HttpStatus.BAD_REQUEST,
             "Limit must be greater than 0"
         );
     }

     Sort sort = TransactionSort.fromValue(filter.getSort()).getSort();
     Pageable pageable = PageRequest.of(skip / limit, limit, sort);

     String search = filter.getSearch() == null || filter.getSearch().isBlank()
         ? null
         : "%" + filter.getSearch().toLowerCase() + "%";
     DateRange range = resolveDateRange(filter);
     String type = filter.getType() == null ? null : filter.getType().name();

     Page<Transaction> transactions = transactionRepository.findAllActive(
         user, search, range.start(), range.end(), type, filter.getCategoryId(), pageable
     );

    return transactions.map(transaction -> toTransactionResponse(transaction, transaction.getCategory()));
 }

    // [HELPER] start inklusif, end eksklusif; null berarti tanpa batas
    private record DateRange(LocalDateTime start, LocalDateTime end) {}

    private static final DateRange ALL_DATES = new DateRange(null, null);

    // [HELPER] date (satu hari) diprioritaskan di atas period
    // [HELPER] LAST_7_DAYS / LAST_14_DAYS sudah termasuk hari ini
    private DateRange resolveDateRange(TransactionFilter filter) {
        if (filter.getDate() != null) {
            return new DateRange(filter.getDate().atStartOfDay(), filter.getDate().plusDays(1).atStartOfDay());
        }

        LocalDate today = LocalDate.now(clock);
        LocalDateTime tomorrow = today.plusDays(1).atStartOfDay();
        TransactionDatePeriod period = filter.getPeriod() == null ? TransactionDatePeriod.ALL : filter.getPeriod();

        return switch (period) {
            case ALL -> ALL_DATES;
            case TODAY -> new DateRange(today.atStartOfDay(), tomorrow);
            case LAST_7_DAYS -> new DateRange(today.minusDays(6).atStartOfDay(), tomorrow);
            case LAST_14_DAYS -> new DateRange(today.minusDays(13).atStartOfDay(), tomorrow);
            case MANUAL -> resolveManualDateRange(filter.getStartDate(), filter.getEndDate());
        };
    }

    // [HELPER] endDate inklusif, jadi batas akhir query adalah awal hari berikutnya
    private DateRange resolveManualDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate and endDate are required for MANUAL period");
        }

        if (endDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate must not be before startDate");
        }

        return new DateRange(startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());
    }


// [MAIN] get stat transaction

    @Transactional(readOnly = true)
    public TransactionStateResponse getStat(User user, TransactionStatFilter filter) {
        StatRange range = resolveStatRange(filter);
        PeriodTotals current = sumTotals(user, range.currentStart(), range.currentEnd());
        PeriodTotals last = sumTotals(user, range.lastStart(), range.currentStart());

        TransactionStateResponse response = new TransactionStateResponse();
        response.setTotalBalance(toStatistic(current.balance(), last.balance()));
        response.setTotalIncome(toStatistic(current.income(), last.income()));
        response.setTotalExpense(toStatistic(current.expense(), last.expense()));
        response.setTotalSaving(toStatistic(current.saving(), last.saving()));
        return response;
    }

    // [HELPER] periode sebelumnya selalu berakhir tepat di currentStart
    private record StatRange(LocalDateTime lastStart, LocalDateTime currentStart, LocalDateTime currentEnd) {}

    // [HELPER] saving adalah dana masuk ke akun SAVINGS, jadi menambah balance
    private record PeriodTotals(BigDecimal income, BigDecimal expense, BigDecimal saving) {
        BigDecimal balance() {
            return income.subtract(expense).add(saving);
        }
    }

    // [HELPER]
    private StatRange resolveStatRange(TransactionStatFilter filter) {
        LocalDate today = LocalDate.now(clock);

        return switch (filter.getType()) {
            case YEARLY -> {
                LocalDateTime start = today.withDayOfYear(1).atStartOfDay();
                yield new StatRange(start.minusYears(1), start, start.plusYears(1));
            }
            case MONTHLY -> {
                LocalDateTime start = today.withDayOfMonth(1).atStartOfDay();
                yield new StatRange(start.minusMonths(1), start, start.plusMonths(1));
            }
            case WEEKLY -> {
                LocalDateTime end = today.plusDays(1).atStartOfDay();
                LocalDateTime start = end.minusWeeks(1);
                yield new StatRange(start.minusWeeks(1), start, end);
            }
            case CUSTOM -> resolveCustomRange(filter.getStartDate(), filter.getEndDate());
        };
    }

    // [HELPER]
    private StatRange resolveCustomRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate and endDate are required for CUSTOM type");
        }

        if (endDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate must not be before startDate");
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        long periodDays = ChronoUnit.DAYS.between(start, end);

        return new StatRange(start.minusDays(periodDays), start, end);
    }

    // [HELPER]
    private PeriodTotals sumTotals(User user, LocalDateTime start, LocalDateTime end) {
        return new PeriodTotals(
            sumAmount(user, TransactionType.INCOME, start, end),
            sumAmount(user, TransactionType.EXPENSE, start, end),
            sumAmount(user, TransactionType.SAVING, start, end)
        );
    }

    // [HELPER]
    private BigDecimal sumAmount(User user, TransactionType type, LocalDateTime start, LocalDateTime end) {
        return transactionRepository.sumAmountByTypeAndPeriod(user, type.name(), start, end);
    }

   // [HELPER]
   private TransactionStateResponse.Statistic toStatistic(BigDecimal current, BigDecimal last) {
      TransactionStateResponse.Statistic statistic = new TransactionStateResponse.Statistic();
      statistic.setAmount(current);
      statistic.setChangePercentage(calculateChangePercentage(current, last));
      return statistic;
   }

// [HELPER]
private BigDecimal calculateChangePercentage( BigDecimal current, BigDecimal last) {
    if (last.compareTo(BigDecimal.ZERO) == 0) {
        // defisit dari 0 -> -100, surplus dari 0 -> 100
        return ONE_HUNDRED.multiply(BigDecimal.valueOf(current.signum())).setScale(PERCENTAGE_SCALE);
    }

    return current.subtract(last)
            .multiply(ONE_HUNDRED)
            .divide(last.abs(), PERCENTAGE_SCALE, RoundingMode.HALF_UP);
}


    // [HELPER]
    private TransactionResponse toTransactionResponse(Transaction transaction, Category category) {
        TransactionResponse response = new TransactionResponse();
        response.setTransactionName(transaction.getTransactionName());
        response.setTransactionCode(transaction.getTransactionCode());
        response.setAmount(transaction.getAmount());
        response.setDescription(transaction.getDescription());
        response.setCategory(toCategoryResponse(category));
        response.setDate(transaction.getTransactionDate().toLocalDate().toString());
        return response;
    }

    // [HELPER]
    private CategoryResponse toCategoryResponse(Category category) {
        CategoryResponse response = new CategoryResponse();
        response.setId(category.getId());
        response.setName(category.getName());
        response.setType(category.getType());
        return response;
    }


    // [MAIN] get statistic transation
   @Transactional (readOnly = true)
    public TransactionStatisticResponse getStatistic(User user, String periode) {

        if (periode == null || periode.isEmpty()) {
            periode = "Monthly";
        }

        if (!"Monthly".equals(periode) && !"Weekly".equals(periode)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "periode is not valid"
            );
        }

        LocalDate now = LocalDate.now(clock);
        LocalDate startDate;
        LocalDate endDate;

        if ("Monthly".equals(periode)) {
            startDate = now.withDayOfMonth(1);
            endDate = now.withDayOfMonth(now.lengthOfMonth());
        } else {
            startDate = now.minusDays(6);
            endDate = now;
        }

        List<TransactionPeriodItemResponse> dailyItems = transactionRepository.getSumDailyIncomeAndExpenseByPeriod(
            user,
            startDate.atStartOfDay(),
            endDate.plusDays(1).atStartOfDay()
        );

        TransactionStatisticResponse response = new TransactionStatisticResponse();
        response.setPeriod(periode);
        response.setStartDate(startDate);
        response.setEndDate(endDate);
        response.setItems(fillMissingDates(dailyItems, startDate, endDate));
        return response;
    }

    // [HELPER]
    private List<TransactionPeriodItemResponse> fillMissingDates(
        List<TransactionPeriodItemResponse> dailyItems,
        LocalDate startDate,
        LocalDate endDate
    ) {
        Map<LocalDate, TransactionPeriodItemResponse> itemsByDate = dailyItems.stream()
            .collect(Collectors.toMap(TransactionPeriodItemResponse::getDate, Function.identity()));

        return startDate.datesUntil(endDate.plusDays(1))
            .map(date -> itemsByDate.getOrDefault(date,
                new TransactionPeriodItemResponse(date, BigDecimal.ZERO, BigDecimal.ZERO)))
            .toList();
    }

    

}
    
  




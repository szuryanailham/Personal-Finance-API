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
import com.ilham.personal_finance_api.dto.CategoryResponse;
import com.ilham.personal_finance_api.dto.CreateTransactionRequest;
import com.ilham.personal_finance_api.dto.CreateTransactionResponse;
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

        return CreateTransactionResponse.builder()
            .id(transaction.getId())
            .transactionName(transaction.getTransactionName())
            .amount(transaction.getAmount())
            .description(transaction.getDescription())
            .categoryId(category.getId())
            .date(request.getDate())
            .build();
    }

    // [MAIN] bulk create transaction (setelah preview import)
    // all-or-nothing: jika ada category yang tidak valid, tidak ada transaksi yang disimpan.

    @Transactional
    public List<TransactionResponse> bulkCreate(User user, BulkCreateTransactionRequest request) {
        validationService.validate(request);

        List<BulkTransactionItem> items = request.getTransactions();
        Map<UUID, Category> categoriesById = loadCategories(user, items);
        List<String> transactionCodes = transactionCodeGenerator.generate(items.size());

        List<Transaction> transactions = IntStream.range(0, items.size())
            .mapToObj(i -> toTransaction(user, items.get(i), categoriesById.get(items.get(i).getCategoryId()), transactionCodes.get(i)))
            .toList();

        return transactionRepository.saveAll(transactions).stream()
            .map(transaction -> toTransactionResponse(transaction, transaction.getCategory()))
            .toList();
    }

    // [HELPER] category aktif milik user; gagal jika ada categoryId yang tidak ditemukan
    private Map<UUID, Category> loadCategories(User user, List<BulkTransactionItem> items) {
        Set<UUID> categoryIds = items.stream()
            .map(BulkTransactionItem::getCategoryId)
            .collect(Collectors.toCollection(LinkedHashSet::new));

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
     LocalDateTime start = filter.getDate() == null ? null : filter.getDate().atStartOfDay();
     LocalDateTime end = filter.getDate() == null ? null : filter.getDate().plusDays(1).atStartOfDay();
     String type = filter.getType() == null ? null : filter.getType().name();

     Page<Transaction> transactions = transactionRepository.findAllActive(user, search, start, end, type, pageable);

    return transactions.map(transaction -> toTransactionResponse(transaction, transaction.getCategory()));
 }


// [MAIN] get stat transaction

    @Transactional(readOnly = true)
    public TransactionStateResponse getStat(User user, TransactionStatFilter filter) {
        StatRange range = resolveStatRange(filter);
        PeriodTotals current = sumTotals(user, range.currentStart(), range.currentEnd());
        PeriodTotals last = sumTotals(user, range.lastStart(), range.currentStart());

        return TransactionStateResponse.builder()
            .totalBalance(toStatistic(current.balance(), last.balance()))
            .totalIncome(toStatistic(current.income(), last.income()))
            .totalExpense(toStatistic(current.expense(), last.expense()))
            .totalSaving(toStatistic(current.saving(), last.saving()))
            .build();
    }

    // [HELPER] periode sebelumnya selalu berakhir tepat di currentStart
    private record StatRange(LocalDateTime lastStart, LocalDateTime currentStart, LocalDateTime currentEnd) {}

    // [HELPER]
    private record PeriodTotals(BigDecimal income, BigDecimal expense, BigDecimal saving) {
        BigDecimal balance() {
            return income.subtract(expense).subtract(saving);
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
      return TransactionStateResponse.Statistic.builder()
          .amount(current)
          .changePercentage(calculateChangePercentage(current, last))
          .build();
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
        return TransactionResponse.builder()
            .transactionName(transaction.getTransactionName())
            .transactionCode(transaction.getTransactionCode())
            .amount(transaction.getAmount())
            .description(transaction.getDescription())
            .category(toCategoryResponse(category))
            .date(transaction.getTransactionDate().toLocalDate().toString())
            .build();
    }

    // [HELPER]
    private CategoryResponse toCategoryResponse(Category category) {
        return CategoryResponse.builder()
            .id(category.getId())
            .name(category.getName())
            .type(category.getType())
            .build();
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

        return TransactionStatisticResponse.builder()
            .period(periode)
            .startDate(startDate)
            .endDate(endDate)
            .items(fillMissingDates(dailyItems, startDate, endDate))
            .build();
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
            .map(date -> itemsByDate.getOrDefault(date, TransactionPeriodItemResponse.builder()
                .date(date)
                .income(BigDecimal.ZERO)
                .expense(BigDecimal.ZERO)
                .build()))
            .toList();
    }

    

}
    
  




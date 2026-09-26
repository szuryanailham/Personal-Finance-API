package com.ilham.personal_finance_api.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ilham.personal_finance_api.dto.CategoryResponse;
import com.ilham.personal_finance_api.dto.CreateTransactionRequest;
import com.ilham.personal_finance_api.dto.CreateTransactionResponse;
import com.ilham.personal_finance_api.dto.TransactionFilter;
import com.ilham.personal_finance_api.dto.TransactionResponse;
import com.ilham.personal_finance_api.dto.TransactionSort;
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
import com.ilham.personal_finance_api.repository.TransactionSpecification;

@Service
public class TransactionService {

    private static final int PERCENTAGE_SCALE = 2;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final String TRANSACTION_CODE_PREFIX = "TRX-";
    private static final DateTimeFormatter TRANSACTION_CODE_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private Clock clock;

    // create transaction

    @Transactional
    public CreateTransactionResponse create(User user, CreateTransactionRequest request) {
        validationService.validate(request);

        Category category = categoryRepository.findByIdAndUser(request.getCategoryId(), user)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

        String transactionCode = generateTransactionCode();
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


    // generate transaction code 

    // format: TRX-yyyyMMdd-NNN, nomor urut reset setiap hari
    private String generateTransactionCode() {
        String prefix = TRANSACTION_CODE_PREFIX + LocalDate.now(clock).format(TRANSACTION_CODE_DATE_FORMAT) + "-";

        int nextSequence = transactionRepository
            .findTopByTransactionCodeStartingWithOrderByTransactionCodeDesc(prefix)
            .map(last -> Integer.parseInt(last.getTransactionCode().substring(prefix.length())) + 1)
            .orElse(1);

        String transactionCode;
        do {
            transactionCode = prefix + String.format("%03d", nextSequence++);
        } while (transactionRepository.existsByTransactionCode(transactionCode));

        return transactionCode;
    }

    // delete transaction
@Transactional
public void delete(User user, String transactionCode) {
    Transaction transaction = transactionRepository
            .findByUserAndTransactionCode(user, transactionCode)
            .orElseThrow(() ->
                    new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Transaction not found"
                    )
            );

    transaction.setDeleted(true);
}

// update transaction 

@Transactional
public TransactionResponse update(User user, String transactionCode, UpdateTransactionRequest request) {

    validationService.validate(request);

    Transaction transaction = transactionRepository
        .findByUserAndTransactionCode(user, transactionCode)
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


// get single transaction 

@Transactional (readOnly = true)
public TransactionResponse get(User user , String transactionCode) {

    Transaction transaction = transactionRepository.findByUserAndTransactionCode(user, transactionCode).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
    return toTransactionResponse(transaction, transaction.getCategory());
}


// get all transaction

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

     Specification<Transaction> specification = TransactionSpecification.filterBy(user, filter);
     Page<Transaction> transactions = transactionRepository.findAll(specification, pageable);

    return transactions.map(transaction -> toTransactionResponse(transaction, transaction.getCategory()));
 }


//  get state transaction
   @Transactional (readOnly = true)
   public TransactionStateResponse getStat(User user, LocalDateTime startDate, LocalDateTime endDate) {
      if (!endDate.isAfter(startDate)) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate must be after startDate");
      }


      LocalDateTime lastStartDate = startDate.toLocalDate().minusMonths(1).withDayOfMonth(1).atStartOfDay();
      LocalDateTime lastEndDate = startDate.toLocalDate().withDayOfMonth(1).atStartOfDay();

      BigDecimal currentIncome = sumAmount(user, TransactionType.INCOME, startDate, endDate);
      BigDecimal currentExpense = sumAmount(user, TransactionType.EXPENSE, startDate, endDate);
      BigDecimal currentSaving = sumAmount(user, TransactionType.SAVING, startDate, endDate);
      BigDecimal lastIncome = sumAmount(user, TransactionType.INCOME, lastStartDate, lastEndDate);
      BigDecimal lastExpense = sumAmount(user, TransactionType.EXPENSE, lastStartDate, lastEndDate);
      BigDecimal lastSaving = sumAmount(user, TransactionType.SAVING, lastStartDate, lastEndDate);


      BigDecimal currentBalance = currentIncome.subtract(currentExpense);
      BigDecimal lastBalance = lastIncome.subtract(lastExpense);

      

      return TransactionStateResponse.builder()
          .totalBalance(toStatistic(currentBalance, lastBalance))
          .totalIncome(toStatistic(currentIncome, lastIncome))
          .totalExpense(toStatistic(currentExpense, lastExpense))
          .totalSaving(toStatistic(currentSaving, lastSaving))
          .build();
   }

   private BigDecimal sumAmount(User user, TransactionType type, LocalDateTime start, LocalDateTime end) {
      return transactionRepository.sumAmountByTypeAndPeriod(user, type.name(), start, end);
   }

   private TransactionStateResponse.Statistic toStatistic(BigDecimal current, BigDecimal last) {
      BigDecimal nonNegativeCurrent = current.max(BigDecimal.ZERO);
      return TransactionStateResponse.Statistic.builder()
          .amount(nonNegativeCurrent)
          .changePercentage(calculateChangePercentage(nonNegativeCurrent, last))
          .build();
   }

private BigDecimal calculateChangePercentage( BigDecimal current, BigDecimal last) {
    if (last.compareTo(BigDecimal.ZERO) == 0) {
        BigDecimal result = current.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : ONE_HUNDRED;
        return result.setScale(PERCENTAGE_SCALE);
    }

    return current.subtract(last)
            .multiply(ONE_HUNDRED)
            .divide(last.abs(), PERCENTAGE_SCALE, RoundingMode.HALF_UP);
}


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

    private CategoryResponse toCategoryResponse(Category category) {
        return CategoryResponse.builder()
            .id(category.getId())
            .name(category.getName())
            .type(category.getType())
            .build();
    }


    // get statistic transation
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
    
  




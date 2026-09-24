package com.ilham.personal_finance_api.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import com.ilham.personal_finance_api.dto.TransactionStateResponse;
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

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ValidationService validationService;


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




    private String generateTransactionCode() {
        String transactionCode;
        do {
            transactionCode = "TRX-" + UUID.randomUUID();
        } while (transactionRepository.existsByTransactionCode(transactionCode));

        return transactionCode;
    }

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


@Transactional (readOnly = true)
public TransactionResponse get(User user , String transactionCode) {

    Transaction transaction = transactionRepository.findByUserAndTransactionCode(user, transactionCode).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
    return toTransactionResponse(transaction, transaction.getCategory());
}



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

     Pageable pageable = PageRequest.of(skip / limit, limit);

     Specification<Transaction> specification = TransactionSpecification.filterBy(user, filter);
     Page<Transaction> transactions = transactionRepository.findAll(specification, pageable);

    return transactions.map(transaction -> toTransactionResponse(transaction, transaction.getCategory()));
 }



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

      // Balance = income - expense (SAVING tidak mengurangi balance)
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
      return TransactionStateResponse.Statistic.builder()
          .amount(current)
          .changePercentage(calculateChangePercentage(current, last))
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


  


    
}

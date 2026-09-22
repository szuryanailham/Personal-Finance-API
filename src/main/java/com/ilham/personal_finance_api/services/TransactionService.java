package com.ilham.personal_finance_api.services;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.ilham.personal_finance_api.entity.Category;
import com.ilham.personal_finance_api.entity.Transaction;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.model.CategoryResponse;
import com.ilham.personal_finance_api.model.CreateTransactionRequest;
import com.ilham.personal_finance_api.model.CreateTransactionResponse;
import com.ilham.personal_finance_api.model.TransactionResponse;
import com.ilham.personal_finance_api.model.UpdateTransactionRequest;
import com.ilham.personal_finance_api.repository.CategoryRepository;
import com.ilham.personal_finance_api.repository.TransactionRepository;

@Service
public class TransactionService {

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
 public Page<TransactionResponse> getAll(User user, int skip, int limit) {
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

     Page<Transaction> transactions = transactionRepository.findAllByUser(user, pageable);

    return transactions.map(transaction -> toTransactionResponse(transaction, transaction.getCategory()));
 }



    private TransactionResponse toTransactionResponse(Transaction transaction, Category category) {
        return TransactionResponse.builder()
            .transactionName(transaction.getTransactionName())
            .transactionCode(transaction.getTransactionCode())
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

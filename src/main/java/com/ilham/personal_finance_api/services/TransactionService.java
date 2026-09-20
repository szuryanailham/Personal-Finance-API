package com.ilham.personal_finance_api.services;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ilham.personal_finance_api.entity.Category;
import com.ilham.personal_finance_api.entity.Transaction;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.model.CreateTransactionRequest;
import com.ilham.personal_finance_api.model.CreateTransactionResponse;
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
        Transaction transaction = transactionRepository.findByUserandCode(user, transactionCode)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        transactionRepository.delete(transaction);
    }




    
}

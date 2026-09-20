package com.ilham.personal_finance_api.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.model.CreateTransactionRequest;
import com.ilham.personal_finance_api.model.CreateTransactionResponse;
import com.ilham.personal_finance_api.model.WebResponse;
import com.ilham.personal_finance_api.services.TransactionService;
import jakarta.validation.Valid;

@RestController 
public class TransactionController {

    @Autowired 
    private TransactionService transactionService;

    @PostMapping (
        path = "/api/transaction",
        produces = MediaType.APPLICATION_JSON_VALUE,
        consumes = MediaType.APPLICATION_JSON_VALUE
    )

    public WebResponse<CreateTransactionResponse> create(User user , @Valid @RequestBody CreateTransactionRequest request) {
        CreateTransactionResponse response = transactionService.create(user, request);
        return new WebResponse<>(
            response,
            "Transaction created Successfully",
            null
            , null
        );
    }

}

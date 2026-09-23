package com.ilham.personal_finance_api.controller;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.dto.CreateTransactionRequest;
import com.ilham.personal_finance_api.dto.CreateTransactionResponse;
import com.ilham.personal_finance_api.dto.PaginationResponse;
import com.ilham.personal_finance_api.dto.TransactionFilter;
import com.ilham.personal_finance_api.dto.TransactionResponse;
import com.ilham.personal_finance_api.dto.TransactionType;
import com.ilham.personal_finance_api.dto.UpdateTransactionRequest;
import com.ilham.personal_finance_api.dto.WebResponse;
import com.ilham.personal_finance_api.services.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;

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


    @DeleteMapping  (
        path = "/api/transaction/{transactionCode}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    
    public WebResponse<String> delete(User user, @PathVariable("transactionCode") String categoryId) {
        transactionService.delete(user, categoryId);
        return WebResponse.<String>builder()
        .message("Transaction deleted successfully")
        .build();
    }

   @GetMapping (
    path = "/api/transaction/{transactionCode}",
    produces = MediaType.APPLICATION_JSON_VALUE
)
    public WebResponse<TransactionResponse> get(User user , @PathVariable("transactionCode") String transactionCode) {
        TransactionResponse transactionResponse = transactionService.get(user, transactionCode);
        return WebResponse.<TransactionResponse>builder().data(transactionResponse).build();
    }

@GetMapping(
    path = "/api/transaction",
    produces = MediaType.APPLICATION_JSON_VALUE
)
public WebResponse<List<TransactionResponse>> getAll(
        User user,
        @RequestParam int skip,
        @RequestParam int limit,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(required = false) TransactionType type
) {
    TransactionFilter filter = TransactionFilter.builder()
            .search(search)
            .date(date)
            .type(type)
            .build();

    Page<TransactionResponse> transaction =
            transactionService.getAll(user, skip, limit, filter);

    return WebResponse.<List<TransactionResponse>>builder()
            .data(transaction.getContent())
            .paging(
                PaginationResponse.builder()
                    .currentPage(transaction.getNumber() + 1)
                    .totalPage(transaction.getTotalPages())
                    .size(transaction.getSize())
                    .build()
            )
            .build();
}

@PatchMapping (
    path = "/api/transaction/{transactionCode}",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE
)

public WebResponse<TransactionResponse>update(User user ,
    @Valid 
    @RequestBody 
    UpdateTransactionRequest request,
    @PathVariable("transactionCode") String transactionCode
) {
    TransactionResponse transactionResponse = transactionService.update(user, transactionCode, request);
    return WebResponse.<TransactionResponse>builder().data(transactionResponse).build();
}


    
}

package com.ilham.personal_finance_api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BulkCreateTransactionRequest {

    public static final int MAX_BULK_SIZE = 500;

    @NotEmpty(message = "Transactions must not be empty")
    @Size(
        max = MAX_BULK_SIZE,
        message = "Transactions must be at most " + MAX_BULK_SIZE + " items"
    )
    private List<@Valid BulkTransactionItem> transactions;

    public BulkCreateTransactionRequest(List<BulkTransactionItem> transactions) {
        this.transactions = transactions;
    }
}

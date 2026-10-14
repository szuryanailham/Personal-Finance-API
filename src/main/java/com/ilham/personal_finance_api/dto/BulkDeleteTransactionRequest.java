package com.ilham.personal_finance_api.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BulkDeleteTransactionRequest {

    public static final int MAX_BULK_SIZE = 500;

    @NotEmpty(message = "Transaction codes must not be empty")
    @Size(max = MAX_BULK_SIZE, message = "Transaction codes must be at most " + MAX_BULK_SIZE + " items")
    private List<@NotBlank String> transactionCodes;
}

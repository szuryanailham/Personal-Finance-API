package com.ilham.personal_finance_api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

// field yang sama diterapkan ke semua transactionCodes; field yang null tidak diubah
@Data
@NoArgsConstructor
public class BulkUpdateTransactionRequest {

    public static final int MAX_BULK_SIZE = 500;

    @NotEmpty(message = "Transaction codes must not be empty")
    @Size(max = MAX_BULK_SIZE, message = "Transaction codes must be at most " + MAX_BULK_SIZE + " items")
    private List<@NotBlank String> transactionCodes;

    @Size(max = 100, message = "Name must be at most 100 characters")
    private String transactionName;

    private UUID categoryId;

    private LocalDate date;

    @Size(max = 255, message = "Description must not exceed 255 characters")
    private String description;

    public boolean hasAnyField() {
        return transactionName != null || categoryId != null || date != null || description != null;
    }
}

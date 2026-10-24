package com.ilham.personal_finance_api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateTransferRequest {

    @NotNull(message = "Source account is required")
    private UUID fromAccountId;

    @NotNull(message = "Destination account is required")
    private UUID toAccountId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0", inclusive = false, message = "Amount must be greater than 0")
    @Digits(integer = 13, fraction = 2, message = "Amount must have at most 13 digits and 2 decimals")
    private BigDecimal amount;

    @Size(max = 255, message = "Note must be at most 255 characters")
    private String note;
}

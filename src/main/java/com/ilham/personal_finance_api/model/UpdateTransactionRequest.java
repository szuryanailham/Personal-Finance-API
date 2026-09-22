package com.ilham.personal_finance_api.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data 
@NoArgsConstructor 
@AllArgsConstructor 
@Builder 

public class UpdateTransactionRequest {

    @NotNull(message = "Transaction Name is required")
     @Size(max = 100, message = "Name of Transaction must not exceed 100 characters")
    private String transactonName;

    @NotNull(message = "Amount is required")
    private BigDecimal amount;

    private String description;

    @NotNull(message = "Category ID is required")
    private UUID categoryId;

    @NotNull(message = "Date is required")
    private LocalDate date;


}

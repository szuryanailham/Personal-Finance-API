package com.ilham.personal_finance_api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

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

    @Size(max = 100, message = "Name of Transaction must not exceed 100 characters")
    private String transactonName;

    private BigDecimal amount;

    private String description;

    private UUID categoryId;

    private LocalDate date;


}

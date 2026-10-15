package com.ilham.personal_finance_api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter 
@Setter 
@NoArgsConstructor 
public class CreateTransactionResponse {
    private UUID id;
    private String transactionName;
    private BigDecimal amount;
    private String description;
    private UUID categoryId;
    private LocalDate date;

}

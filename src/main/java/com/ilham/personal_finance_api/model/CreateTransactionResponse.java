package com.ilham.personal_finance_api.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter 
@Setter 
@NoArgsConstructor 
@AllArgsConstructor 
@Builder 
public class CreateTransactionResponse {
    private UUID id;
    private String transactionName;
    private BigDecimal amount;
    private String description;
    private UUID categoryId;
    private LocalDate date;

}

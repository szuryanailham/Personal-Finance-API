package com.ilham.personal_finance_api.dto;

import java.math.BigDecimal;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor

public class TransactionResponse {
private String transactionName;
private String transactionCode;
private BigDecimal amount;
private String description;
private CategoryResponse category;
private String date;
}

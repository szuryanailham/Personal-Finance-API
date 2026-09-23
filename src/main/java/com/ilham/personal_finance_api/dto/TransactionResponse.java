package com.ilham.personal_finance_api.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor

public class TransactionResponse {
private String transactionName;
private String transactionCode;
private BigDecimal amount;
private String description;
private CategoryResponse category;
private String date;
}

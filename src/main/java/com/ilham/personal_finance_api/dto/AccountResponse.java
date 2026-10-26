package com.ilham.personal_finance_api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AccountResponse {
    private UUID id;
    private AccountType accountType;
    private String name;
    private BigDecimal openingBalance;
    private BigDecimal currentBalance;
}

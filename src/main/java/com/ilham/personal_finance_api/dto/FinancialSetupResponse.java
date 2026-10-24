package com.ilham.personal_finance_api.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class FinancialSetupResponse {
    private boolean setupCompleted;
    private List<AccountResponse> accounts;
    private BigDecimal totalBalance;
}

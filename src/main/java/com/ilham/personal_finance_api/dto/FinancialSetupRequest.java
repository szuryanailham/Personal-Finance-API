package com.ilham.personal_finance_api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import lombok.Data;
import lombok.NoArgsConstructor;

// balance = saldo awal MAIN, savings = saldo awal SAVINGS; null / tidak dikirim berarti 0
@Data
@NoArgsConstructor
public class FinancialSetupRequest {

    @DecimalMin(value = "0", message = "Balance must not be negative")
    @Digits(integer = 13, fraction = 2, message = "Balance must have at most 13 digits and 2 decimals")
    private BigDecimal balance;

    @DecimalMin(value = "0", message = "Savings must not be negative")
    @Digits(integer = 13, fraction = 2, message = "Savings must have at most 13 digits and 2 decimals")
    private BigDecimal savings;
}

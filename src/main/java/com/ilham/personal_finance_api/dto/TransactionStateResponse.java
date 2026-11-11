package com.ilham.personal_finance_api.dto;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TransactionStateResponse {

    private Statistic totalBalance;
    private Statistic totalIncome;
    private Statistic totalExpense;
    private Statistic totalSaving;
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    @Getter 
    @Setter 
    @NoArgsConstructor 
    
    public static class Statistic {
        private BigDecimal amount;
    }
}

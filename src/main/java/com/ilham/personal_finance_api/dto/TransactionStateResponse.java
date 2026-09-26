package com.ilham.personal_finance_api.dto;
import java.math.BigDecimal;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionStateResponse {

    private Statistic totalBalance;
    private Statistic totalIncome;
    private Statistic totalExpense;
     private Statistic totalSaving;

    @Getter 
    @Setter 
    @Builder 
    @NoArgsConstructor 
    @AllArgsConstructor 
    
    public static class Statistic {
        private BigDecimal amount;
        private BigDecimal changePercentage;
    }
}

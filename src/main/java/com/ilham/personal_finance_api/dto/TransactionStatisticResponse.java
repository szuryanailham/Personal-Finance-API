package com.ilham.personal_finance_api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter 
@Setter 
@NoArgsConstructor 

public class TransactionStatisticResponse {

    private String period;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<TransactionPeriodItemResponse> items;

@Getter 
@Setter 
@NoArgsConstructor 

    public static class TransactionPeriodItemResponse {
        private LocalDate date;
        private BigDecimal income;
        private BigDecimal expense;

        // dipakai oleh JPQL constructor projection
        public TransactionPeriodItemResponse(LocalDate date, BigDecimal income, BigDecimal expense) {
            this.date = date;
            this.income = income;
            this.expense = expense;
        }
    }
}
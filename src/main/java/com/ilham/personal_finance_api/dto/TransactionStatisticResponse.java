package com.ilham.personal_finance_api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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

public class TransactionStatisticResponse {

    private String period;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<TransactionPeriodItemResponse> items;

@Getter 
@Setter 
@Builder 
@NoArgsConstructor 
@AllArgsConstructor 

    public static class TransactionPeriodItemResponse {
        private LocalDate date;
        private BigDecimal income;
        private BigDecimal expense;
    }
}
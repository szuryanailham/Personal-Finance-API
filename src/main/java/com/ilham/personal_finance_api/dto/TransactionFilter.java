package com.ilham.personal_finance_api.dto;

import java.time.LocalDate;
import java.util.UUID;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor

public class TransactionFilter {
    private String search;
    private LocalDate date;
    private UUID categoryId;
    private TransactionType type;
    private TransactionDatePeriod period;
    private LocalDate startDate;
    private LocalDate endDate;
    private  String sort;
}

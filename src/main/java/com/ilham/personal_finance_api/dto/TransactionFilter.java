package com.ilham.personal_finance_api.dto;

import java.time.LocalDate;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder

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

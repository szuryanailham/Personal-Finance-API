package com.ilham.personal_finance_api.dto;

import java.time.LocalDate;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TransactionStatFilter {

    private TransactionStatPeriod type;

    private LocalDate startDate;

    // inclusive
    private LocalDate endDate;
}

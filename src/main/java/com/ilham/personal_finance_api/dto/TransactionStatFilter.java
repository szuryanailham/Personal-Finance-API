package com.ilham.personal_finance_api.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransactionStatFilter {

    private TransactionStatPeriod type;

    private LocalDate startDate;

    // inclusive
    private LocalDate endDate;
}

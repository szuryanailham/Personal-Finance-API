package com.ilham.personal_finance_api.dto;

import java.time.LocalDate;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TransactionExportFilter {
    private TransactionExportPeriod period;
    private LocalDate startDate;
    private LocalDate endDate;
}

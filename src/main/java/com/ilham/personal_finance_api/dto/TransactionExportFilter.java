package com.ilham.personal_finance_api.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TransactionExportFilter {
    private TransactionExportPeriod period;
    private LocalDate startDate;
    private LocalDate endDate;
}

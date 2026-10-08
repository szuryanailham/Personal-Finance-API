package com.ilham.personal_finance_api.dto.exporter;

import java.math.BigDecimal;
import java.time.LocalDate;

// startDate/endDate null jika periode ALL tanpa transaksi
public record ExportSummary(
    LocalDate startDate,
    LocalDate endDate,
    int transactionCount,
    BigDecimal totalIncome,
    BigDecimal totalExpense,
    BigDecimal totalSaving
) {

    // sama dengan perhitungan balance di endpoint stat: saving mengurangi balance
    public BigDecimal totalBalance() {
        return totalIncome.subtract(totalExpense).subtract(totalSaving);
    }
}

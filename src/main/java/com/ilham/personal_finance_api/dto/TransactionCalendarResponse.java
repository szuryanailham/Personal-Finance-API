package com.ilham.personal_finance_api.dto;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

// startDate & endDate inklusif; days hanya berisi tanggal yang punya transaksi
public record TransactionCalendarResponse(
    YearMonth month,
    LocalDate startDate,
    LocalDate endDate,
    List<CalendarTypeSummaryResponse> totals,
    List<CalendarDayResponse> days
) {}

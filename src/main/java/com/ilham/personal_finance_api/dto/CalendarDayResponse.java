package com.ilham.personal_finance_api.dto;

import java.time.LocalDate;
import java.util.List;

// summaries selalu berisi INCOME, EXPENSE, SAVING (urut)
public record CalendarDayResponse(LocalDate date, List<CalendarTypeSummaryResponse> summaries) {}

package com.ilham.personal_finance_api.dto;

import java.math.BigDecimal;

public record CalendarTypeSummaryResponse(TransactionType type, BigDecimal amount, long count) {}

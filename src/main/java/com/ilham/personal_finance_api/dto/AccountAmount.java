package com.ilham.personal_finance_api.dto;

import java.math.BigDecimal;
import java.util.UUID;

// hasil agregasi SUM(amount) transfer per akun
public record AccountAmount(UUID accountId, BigDecimal amount) {
}

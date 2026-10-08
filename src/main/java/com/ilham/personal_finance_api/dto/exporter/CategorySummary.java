package com.ilham.personal_finance_api.dto.exporter;

import java.math.BigDecimal;

public record CategorySummary(String name, String type, int transactionCount, BigDecimal totalAmount) {}

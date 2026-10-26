package com.ilham.personal_finance_api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// dipakai oleh JPQL constructor projection: total per (tanggal, type category)
public record CalendarDailyTypeTotal(LocalDate date, String type, BigDecimal amount, Long count) {}

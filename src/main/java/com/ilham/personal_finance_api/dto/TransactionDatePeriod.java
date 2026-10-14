package com.ilham.personal_finance_api.dto;

import java.util.Arrays;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public enum TransactionDatePeriod {
    ALL,
    TODAY,
    LAST_7_DAYS,
    LAST_14_DAYS,
    MANUAL;

    private static final TransactionDatePeriod DEFAULT = ALL;

    public static TransactionDatePeriod fromValue(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }

        return Arrays.stream(values())
            .filter(period -> period.name().equalsIgnoreCase(value.trim()))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid period: " + value
            ));
    }
}

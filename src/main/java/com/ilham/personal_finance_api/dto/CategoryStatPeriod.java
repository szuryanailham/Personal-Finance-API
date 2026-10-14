package com.ilham.personal_finance_api.dto;

import java.util.Arrays;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public enum CategoryStatPeriod {

    YEARLY,
    MONTHLY,
    WEEKLY,
    TODAY,
    CUSTOM;

    private static final CategoryStatPeriod DEFAULT = MONTHLY;

    public static CategoryStatPeriod fromValue(String value) {
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

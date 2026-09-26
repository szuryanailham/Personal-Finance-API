package com.ilham.personal_finance_api.dto;

import java.util.Arrays;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import lombok.Getter;

@Getter
public enum TransactionSort {

    TRANSACTION_CODE_ASC("transactionCode", Direction.ASC, "transactionCode"),
    TRANSACTION_CODE_DESC("-transactionCode", Direction.DESC, "transactionCode"),
    NAME_ASC("name", Direction.ASC, "transactionName"),
    NAME_DESC("-name", Direction.DESC, "transactionName"),
    DATE_NEWEST("date", Direction.DESC, "transactionDate"),
    DATE_OLDEST("-date", Direction.ASC, "transactionDate"),
    CATEGORY_ASC("category", Direction.ASC, "category.name"),
    CATEGORY_DESC("-category", Direction.DESC, "category.name"),
    AMOUNT_HIGHEST("amount", Direction.DESC, "amount"),
    AMOUNT_LOWEST("-amount", Direction.ASC, "amount");

    private static final TransactionSort DEFAULT = DATE_NEWEST;
    private static final String TIEBREAKER_PROPERTY = "transactionCode";

    private final String value;
    private final Sort sort;

    TransactionSort(String value, Direction direction, String property) {
        this.value = value;
        Sort primary = Sort.by(direction, property);
        // keep paging stable when the primary values are equal
        this.sort = TIEBREAKER_PROPERTY.equals(property)
            ? primary
            : primary.and(Sort.by(Direction.ASC, TIEBREAKER_PROPERTY));
    }

    public static TransactionSort fromValue(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }

        return Arrays.stream(values())
            .filter(sort -> sort.value.equals(value))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid sort value: " + value
            ));
    }
}

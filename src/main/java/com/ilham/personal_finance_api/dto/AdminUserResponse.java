package com.ilham.personal_finance_api.dto;

import java.util.UUID;

public record AdminUserResponse(UUID id, String firstName, String lastName, String email,
        Long transactionCount, Long activeTransactionCount) {}

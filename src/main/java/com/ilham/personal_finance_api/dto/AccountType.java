package com.ilham.personal_finance_api.dto;

public enum AccountType {
    MAIN("Main Balance"),
    SAVINGS("Savings");

    private final String defaultName;

    AccountType(String defaultName) {
        this.defaultName = defaultName;
    }

    public String getDefaultName() {
        return defaultName;
    }
}

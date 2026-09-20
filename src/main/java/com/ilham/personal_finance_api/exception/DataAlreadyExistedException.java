package com.ilham.personal_finance_api.exception;

public class DataAlreadyExistedException extends RuntimeException {
    public DataAlreadyExistedException(String message) {
        super(message);
    }
}

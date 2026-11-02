package com.ilham.personal_finance_api.services;

public interface PasswordResetMailSender {
    void send(String email, String recipientName, String token);
}

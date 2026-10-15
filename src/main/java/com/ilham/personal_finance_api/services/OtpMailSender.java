package com.ilham.personal_finance_api.services;

import java.time.Duration;

import org.springframework.mail.MailException;

public interface OtpMailSender {

    /**
     * Kirim OTP registrasi ke email tujuan.
     *
     * @throws MailException jika email gagal dikirim
     */
    void send(String email, String recipientName, String otp, Duration validFor);
}

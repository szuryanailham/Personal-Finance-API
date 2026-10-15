package com.ilham.personal_finance_api.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class OtpSentResponse {
    private String email;
    private long expiresInSeconds;
    private long resendAvailableInSeconds;
}

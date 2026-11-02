package com.ilham.personal_finance_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordRequest {
    @NotBlank(message = "Token is required")
    @Size(max = 100)
    private String token;

    @NotBlank(message = "Password is required")
    @Size(max = 100)
    private String newPassword;
}

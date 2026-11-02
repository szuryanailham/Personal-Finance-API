package com.ilham.personal_finance_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForgotPasswordRequest {
    @NotBlank(message = "Email is required")
    @Size(max = 100)
    @jakarta.validation.constraints.Email(message = "Invalid email format")
    private String email;
}

package com.ilham.personal_finance_api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ResendOtpRequest {

    @NotBlank(message = "Invalid email is required")
    @Size(max = 100)
    @Email(message = "Invalid email format")
    private String email;
}

package com.ilham.personal_finance_api.blog.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BlogLoginRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 72) String password) {
    public BlogLoginRequest {
        email = email == null ? null : email.strip();
    }
}

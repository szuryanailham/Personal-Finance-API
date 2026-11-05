package com.ilham.personal_finance_api.blog.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BlogRegisterRequest(
        @NotBlank @Size(max = 150) String displayName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 72) String password) {
    public BlogRegisterRequest {
        displayName = displayName == null ? null : displayName.strip();
        email = email == null ? null : email.strip();
    }
}

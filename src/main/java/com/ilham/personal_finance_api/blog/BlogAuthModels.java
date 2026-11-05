package com.ilham.personal_finance_api.blog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class BlogAuthModels {
    private BlogAuthModels() {}

    public record RegisterRequest(
            @NotBlank @Size(max = 150) String displayName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 72) String password) {
        public RegisterRequest {
            displayName = displayName == null ? null : displayName.strip();
            email = email == null ? null : email.strip();
        }
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 72) String password) {
        public LoginRequest {
            email = email == null ? null : email.strip();
        }
    }

    public record UserResponse(UUID id, String displayName, String email, String status,
                               List<String> roles) {}

    public record LoginResponse(String token, String tokenType, Instant expiresAt,
                                UserResponse user) {}
}

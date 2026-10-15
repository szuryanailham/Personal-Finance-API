package com.ilham.personal_finance_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GoogleLoginRequest {

    // ID token (JWT) dari Google Sign-In di sisi client
    @NotBlank(message = "idToken is required")
    @Size(max = 4096)
    private String idToken;
}

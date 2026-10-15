package com.ilham.personal_finance_api.dto;

import java.util.UUID;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor

public class UserResponse {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
}

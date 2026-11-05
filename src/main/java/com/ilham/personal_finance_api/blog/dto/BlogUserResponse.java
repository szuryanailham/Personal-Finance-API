package com.ilham.personal_finance_api.blog.dto;

import java.util.List;
import java.util.UUID;

public record BlogUserResponse(UUID id, String displayName, String email, String status,
                           List<String> roles) {}

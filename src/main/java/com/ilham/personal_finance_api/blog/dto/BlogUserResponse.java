package com.ilham.personal_finance_api.blog.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record BlogUserResponse(UUID id, String displayName, String email, String status,
        List<String> roles, String photoProfile, String description, Map<String, String> socialMedia) {
    public BlogUserResponse(UUID id, String displayName, String email, String status, List<String> roles) {
        this(id, displayName, email, status, roles, null, null, Map.of());
    }
}

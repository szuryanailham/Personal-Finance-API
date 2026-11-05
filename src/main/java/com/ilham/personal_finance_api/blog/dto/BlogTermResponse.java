package com.ilham.personal_finance_api.blog.dto;

import java.time.Instant;
import java.util.UUID;

public record BlogTermResponse(UUID id, String name, String slug, String description,
                           Instant createdAt, Instant updatedAt) {}

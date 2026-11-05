package com.ilham.personal_finance_api.blog.dto;

import java.time.Instant;
import java.util.UUID;

public record BlogPostSummary(UUID id, UUID authorId, String title, String slug, String excerpt,
        String status, Instant publishedAt, Integer readingTimeMinutes, Instant createdAt, Instant updatedAt) {}

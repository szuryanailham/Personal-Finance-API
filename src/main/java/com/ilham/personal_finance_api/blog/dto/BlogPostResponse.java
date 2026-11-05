package com.ilham.personal_finance_api.blog.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.*;

public record BlogPostResponse(UUID id, UUID authorId, String title, String slug, String excerpt,
        String contentHtml, String status, Instant publishedAt, int readingTimeMinutes,
        List<UUID> categoryIds, List<UUID> tagIds, Instant createdAt, Instant updatedAt) {}

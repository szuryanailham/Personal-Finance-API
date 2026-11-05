package com.ilham.personal_finance_api.blog.dto;

import java.util.UUID;
import java.time.Instant;

public record PublicBlogPostSummary(UUID id, String title, String slug, String excerpt, Instant publishedAt, Integer readingTimeMinutes) {}

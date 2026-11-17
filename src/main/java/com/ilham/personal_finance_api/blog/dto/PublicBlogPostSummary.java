package com.ilham.personal_finance_api.blog.dto;

import java.util.UUID;
import java.util.List;
import java.time.Instant;

public record PublicBlogPostSummary(UUID id, String authorName, String title, String slug, String excerpt, String heroImage, Instant publishedAt, Integer readingTimeMinutes, List<PublicBlogTerm> categories) {}

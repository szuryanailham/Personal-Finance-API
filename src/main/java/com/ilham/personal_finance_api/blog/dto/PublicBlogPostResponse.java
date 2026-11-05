package com.ilham.personal_finance_api.blog.dto;

import java.util.UUID;
import java.time.Instant;
import java.util.List;

public record PublicBlogPostResponse(UUID id, String title, String slug, String excerpt, String contentHtml, Instant publishedAt, Integer readingTimeMinutes, List<PublicBlogTerm> categories, List<PublicBlogTerm> tags, Instant updatedAt) {}

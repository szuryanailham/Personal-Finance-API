package com.ilham.personal_finance_api.blog.dto;

import java.util.List;

public record BlogPostListResponse(List<BlogPostSummary> data, BlogOffsetPaging paging) {}

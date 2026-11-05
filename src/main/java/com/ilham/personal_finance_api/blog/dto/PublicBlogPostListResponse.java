package com.ilham.personal_finance_api.blog.dto;

import java.util.List;

public record PublicBlogPostListResponse(List<PublicBlogPostSummary> data, BlogOffsetPaging paging) {}

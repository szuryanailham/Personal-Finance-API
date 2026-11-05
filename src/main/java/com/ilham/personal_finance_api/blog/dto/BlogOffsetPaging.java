package com.ilham.personal_finance_api.blog.dto;

public record BlogOffsetPaging(long skip, int limit, long totalItem, boolean hasMore) {}

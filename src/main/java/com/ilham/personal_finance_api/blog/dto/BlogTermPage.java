package com.ilham.personal_finance_api.blog.dto;

import java.util.List;
import com.ilham.personal_finance_api.dto.PaginationResponse;

public record BlogTermPage(List<BlogTermResponse> items, PaginationResponse paging) {}

package com.ilham.personal_finance_api.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BlogCategoryRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 150) @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*") String slug,
        @Size(max = 10000) String description) {
    public BlogCategoryRequest {
        name = name == null ? null : name.strip();
        slug = slug == null ? null : slug.strip();
    }
}

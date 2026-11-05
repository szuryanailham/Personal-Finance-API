package com.ilham.personal_finance_api.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BlogTagRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 150) @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*") String slug) {
    public BlogTagRequest {
        name = name == null ? null : name.strip();
        slug = slug == null ? null : slug.strip();
    }
}

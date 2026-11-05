package com.ilham.personal_finance_api.blog.dto;

import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.*;

public record CreateBlogPostRequest(
        @NotBlank @Size(max = 250) String title,
        @NotBlank @Size(max = 200) @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*") String slug,
        @Size(max = 10000) String excerpt,
        @NotBlank @Size(max = 1000000) String contentHtml,
        @Pattern(regexp = "DRAFT|PUBLISHED") String status,
        @NotNull @Size(min = 1, max = 100) List<@NotNull UUID> categoryIds,
        @NotNull @Size(max = 100) List<@NotNull UUID> tagIds) {
    public CreateBlogPostRequest {
        title = title == null ? null : title.strip();
        slug = slug == null ? null : slug.strip();
        status = status == null ? "DRAFT" : status;
    }
}

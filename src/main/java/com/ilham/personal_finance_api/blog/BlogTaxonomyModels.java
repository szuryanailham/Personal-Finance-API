package com.ilham.personal_finance_api.blog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.ilham.personal_finance_api.dto.PaginationResponse;

public final class BlogTaxonomyModels {
    private BlogTaxonomyModels() {}

    // Table identifiers are internal constants, never request input.
    enum Kind {
        CATEGORY("categories"), TAG("tags");
        final String table;
        Kind(String table) { this.table = table; }
    }

    public record CategoryRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 150) @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*") String slug,
            @Size(max = 10000) String description) {
        public CategoryRequest {
            name = name == null ? null : name.strip();
            slug = slug == null ? null : slug.strip();
        }
    }

    public record TagRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 150) @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*") String slug) {
        public TagRequest {
            name = name == null ? null : name.strip();
            slug = slug == null ? null : slug.strip();
        }
    }

    public record TermResponse(UUID id, String name, String slug, String description,
                               Instant createdAt, Instant updatedAt) {}
    record TermPage(List<TermResponse> items, PaginationResponse paging) {}
}

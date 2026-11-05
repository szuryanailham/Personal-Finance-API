package com.ilham.personal_finance_api.blog.dto;

import java.util.List;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import jakarta.validation.constraints.*;

/** Tracks supplied fields so an omitted value differs from an explicit null. */
public class UpdateBlogPostRequest {
    private final Set<String> supplied = new HashSet<>();
    @Size(max = 250) @Pattern(regexp = "(?s).*\\S.*")
    private String title;
    @Size(max = 200) @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*")
    private String slug;
    @Size(max = 10000)
    private String excerpt;
    @Size(max = 1000000) @Pattern(regexp = "(?s).*\\S.*")
    private String contentHtml;
    @Pattern(regexp = "DRAFT|PUBLISHED|ARCHIVED")
    private String status;
    @Size(min = 1, max = 100)
    private List<@NotNull UUID> categoryIds;
    @Size(max = 100)
    private List<@NotNull UUID> tagIds;

    public boolean has(String name) { return supplied.contains(name); }
    public boolean empty() { return supplied.isEmpty(); }

    public String title() { return title; }
    public void setTitle(String value) {
        if (value == null) throw new IllegalArgumentException("title must not be null");
        value = value.strip();
        supplied.add("title");
        title = value;
    }

    public String slug() { return slug; }
    public void setSlug(String value) {
        if (value == null) throw new IllegalArgumentException("slug must not be null");
        value = value.strip();
        supplied.add("slug");
        slug = value;
    }

    public String excerpt() { return excerpt; }
    public void setExcerpt(String value) {
        supplied.add("excerpt");
        excerpt = value;
    }

    public String contentHtml() { return contentHtml; }
    public void setContentHtml(String value) {
        if (value == null) throw new IllegalArgumentException("contentHtml must not be null");
        supplied.add("contentHtml");
        contentHtml = value;
    }

    public String status() { return status; }
    public void setStatus(String value) {
        if (value == null) throw new IllegalArgumentException("status must not be null");
        supplied.add("status");
        status = value;
    }

    public List<UUID> categoryIds() { return categoryIds; }
    public void setCategoryIds(List<UUID> value) {
        if (value == null) throw new IllegalArgumentException("categoryIds must not be null");
        supplied.add("categoryIds");
        categoryIds = value;
    }

    public List<UUID> tagIds() { return tagIds; }
    public void setTagIds(List<UUID> value) {
        if (value == null) throw new IllegalArgumentException("tagIds must not be null");
        supplied.add("tagIds");
        tagIds = value;
    }
}

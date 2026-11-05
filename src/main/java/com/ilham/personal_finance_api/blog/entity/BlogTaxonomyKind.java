package com.ilham.personal_finance_api.blog.entity;

// Internal table identifiers, never request input.
public enum BlogTaxonomyKind {
    CATEGORY("categories"), TAG("tags");

    private final String table;

    public String table() { return table; }

    BlogTaxonomyKind(String table) { this.table = table; }
}

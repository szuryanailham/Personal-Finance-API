package com.ilham.personal_finance_api.blog.dto;

import java.util.Map;

public record PublicBlogAuthor(String name, String photoProfile, String description,
                               Map<String, String> socialMedia) {}

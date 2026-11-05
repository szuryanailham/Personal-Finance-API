package com.ilham.personal_finance_api.blog.entity;

import java.util.UUID;

/** Internal database model for a CMS account; never returned directly by the API. */
public record BlogAccount(UUID id, String displayName, String email, String status, String passwordHash) {}

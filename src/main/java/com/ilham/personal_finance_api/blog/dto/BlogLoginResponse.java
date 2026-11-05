package com.ilham.personal_finance_api.blog.dto;

import java.time.Instant;

public record BlogLoginResponse(String token, String tokenType, Instant expiresAt,
                            BlogUserResponse user) {}

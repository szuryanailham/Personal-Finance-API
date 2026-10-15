package com.ilham.personal_finance_api.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfigurasi login Google (prefix {@code app.google}).
 *
 * @param clientIds OAuth client ID yang diterima sebagai audience ID token (web, android, ios)
 */
@ConfigurationProperties("app.google")
public record GoogleAuthProperties(List<String> clientIds) {

    public GoogleAuthProperties {
        clientIds = clientIds == null ? List.of() : List.copyOf(clientIds);
    }
}

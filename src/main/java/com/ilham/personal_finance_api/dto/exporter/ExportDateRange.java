package com.ilham.personal_finance_api.dto.exporter;

import java.time.LocalDateTime;

// start inklusif, end eksklusif; keduanya null untuk periode ALL
public record ExportDateRange(LocalDateTime start, LocalDateTime end) {

    public static ExportDateRange unbounded() {
        return new ExportDateRange(null, null);
    }

    public boolean isUnbounded() {
        return start == null && end == null;
    }
}

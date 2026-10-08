package com.ilham.personal_finance_api.dto.importer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ImportResponse {
    private int code;
    private String message;
    private ImportSummary summary;
    private ImportData data;
}

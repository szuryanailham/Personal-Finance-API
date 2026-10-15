package com.ilham.personal_finance_api.dto.importer;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ImportResponse {
    private int code;
    private String message;
    private ImportSummary summary;
    private ImportData data;
}

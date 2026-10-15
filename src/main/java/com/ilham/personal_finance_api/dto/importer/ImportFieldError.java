package com.ilham.personal_finance_api.dto.importer;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ImportFieldError {
    private String fieldName;
    private String error;
}

package com.ilham.personal_finance_api.dto.importer;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ImportSheetSummary {
    private int totalRows;
    private int valid;
    private int invalid;
}

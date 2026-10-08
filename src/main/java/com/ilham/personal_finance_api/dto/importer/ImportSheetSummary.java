package com.ilham.personal_finance_api.dto.importer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ImportSheetSummary {
    private int totalRows;
    private int valid;
    private int invalid;
}

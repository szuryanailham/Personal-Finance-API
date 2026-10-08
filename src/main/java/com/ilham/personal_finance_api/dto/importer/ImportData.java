package com.ilham.personal_finance_api.dto.importer;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ImportData {
    private List<TransactionImportRow> transactions;
}

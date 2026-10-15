package com.ilham.personal_finance_api.dto.importer;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ImportData {
    private List<TransactionImportRow> transactions;
}

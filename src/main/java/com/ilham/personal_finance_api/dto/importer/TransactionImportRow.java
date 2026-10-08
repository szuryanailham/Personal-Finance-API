package com.ilham.personal_finance_api.dto.importer;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransactionImportRow {
    private int row;
    private String name;
    private String category;

    // null jika category tidak ditemukan pada category aktif milik user
    private UUID categoryId;
    private String categoryType;
    private String date;

    // BigDecimal jika valid, teks mentah jika bukan angka, null jika kosong
    private Object amount;
    private boolean valid;
    private List<ImportFieldError> errors;
}

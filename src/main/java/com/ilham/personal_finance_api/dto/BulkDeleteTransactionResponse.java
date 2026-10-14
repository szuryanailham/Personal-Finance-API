package com.ilham.personal_finance_api.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BulkDeleteTransactionResponse {
    private int deletedCount;
    private List<String> deletedCodes;
    // code yang tidak ditemukan, milik user lain, atau sudah terhapus
    private List<String> skippedCodes;
}

package com.ilham.personal_finance_api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// envelope khusus bulk update: updatedCount di top-level, tanpa data
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BulkUpdateTransactionResponse {
    private String message;
    private int updatedCount;
    private String errors;
    private PaginationResponse paging;
}

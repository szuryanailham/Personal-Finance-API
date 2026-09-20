package com.ilham.personal_finance_api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder

public class PaginationResponse {
    private Integer currentPage;
    private Integer totalPage;
    private Integer size;
}

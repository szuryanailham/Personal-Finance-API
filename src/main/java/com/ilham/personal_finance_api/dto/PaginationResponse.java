package com.ilham.personal_finance_api.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor

public class PaginationResponse {
    private Integer currentPage;
    private Integer totalPage;
    private Integer size;
    private Long totalItem;
}

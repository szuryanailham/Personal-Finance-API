package com.ilham.personal_finance_api.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor

public class WebResponse <T>{
    private T data;
    private String message;
    private String errors;
    private PaginationResponse paging;
}

package com.ilham.personal_finance_api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data 
@NoArgsConstructor 

public class CategoryResponse {
    private UUID id;
    private String name;
    private String type;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long budget;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String icon;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String color;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BigDecimal totalExpense;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BigDecimal remainingBudget;
}

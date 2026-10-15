package com.ilham.personal_finance_api.dto;

import java.util.UUID;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data 
@NoArgsConstructor 

public class CategoryResponse {
    private UUID id;
    private String name;
    private String type;
}

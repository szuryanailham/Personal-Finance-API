package com.ilham.personal_finance_api.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data 
@NoArgsConstructor 

public class CreateCategoryResponse {
 private String name;   
 private String type;
}

package com.ilham.personal_finance_api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data 
@AllArgsConstructor 
@NoArgsConstructor 
@Builder 

public class CreateCategoryResponse {
 private String name;   
 private String type;

}

package com.ilham.personal_finance_api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data 
@Builder 
@AllArgsConstructor 
@NoArgsConstructor 

public class TransactionResponse {
private String transactionName;
private String transactionCode;
private String description;
private CategoryResponse category;
private String date;
}

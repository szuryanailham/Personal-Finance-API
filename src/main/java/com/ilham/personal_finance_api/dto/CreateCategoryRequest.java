package com.ilham.personal_finance_api.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data 
@NoArgsConstructor 


public class CreateCategoryRequest {

    @NotBlank(message = "Name of Category is required")
    @Size(max = 100, message = "Name of Category must not exceed 100 characters")
    private String name;

    @NotNull(message = "Type of Category is required")
    private TransactionType type;
}
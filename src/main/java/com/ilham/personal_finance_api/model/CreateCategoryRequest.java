package com.ilham.personal_finance_api.model;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data 
@NoArgsConstructor 
@AllArgsConstructor 
@Builder 


public class CreateCategoryRequest {

    @NotBlank(message = "Name of Category is required")
    @Size(max = 100, message = "Name of Category must not exceed 100 characters")
    private String name;

    @NotNull(message = "Type of Category is required")
    private CategoryType type;

    public enum CategoryType {
        EXPENSE,
        INCOME
    }
}
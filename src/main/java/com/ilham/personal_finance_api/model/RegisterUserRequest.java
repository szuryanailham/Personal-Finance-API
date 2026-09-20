package com.ilham.personal_finance_api.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder

public class RegisterUserRequest {

    
    @NotBlank(message = "Invalid first name is required")
    @Size(max = 100)
    private String firstName;

   
    @NotBlank(message = "Invalid last name is required")
    @Size(max = 100)
    private String lastName;

    
    @NotBlank(message = "Invalid email is required")
    @Size(max = 100)
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "password is required")
    @Size(max = 100)
    private String password;    
    
}

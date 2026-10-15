package com.ilham.personal_finance_api.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data 
@NoArgsConstructor 

public class RegisterUserResponse {
    private String firstName;
    private String lastName;
    private String email;   
}

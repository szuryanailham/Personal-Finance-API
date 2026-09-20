package com.ilham.personal_finance_api.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.ilham.personal_finance_api.model.RegisterUserRequest;
import com.ilham.personal_finance_api.model.RegisterUserResponse;
import com.ilham.personal_finance_api.model.WebResponse;
import com.ilham.personal_finance_api.services.UserService;

import jakarta.validation.Valid;

@RestController 
public class UserController {

    @Autowired 
    private  UserService userService;

    @PostMapping(
        path = "/api/auth/sign-up",
        produces = MediaType.APPLICATION_JSON_VALUE,
        consumes = MediaType.APPLICATION_JSON_VALUE
    )

    
    public WebResponse<RegisterUserResponse> register(
        @Valid  @RequestBody  RegisterUserRequest request) {
        RegisterUserResponse response = userService.register(request);
      
         return new WebResponse<>(
        response,
        "Register successfully",
        null,
        null
    );
    }

}

package com.ilham.personal_finance_api.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.ilham.personal_finance_api.dto.LoginUserRequest;
import com.ilham.personal_finance_api.dto.TokenResponse;
import com.ilham.personal_finance_api.dto.WebResponse;
import com.ilham.personal_finance_api.services.AuthService;

@RestController 
public class AuthController {


    private final AuthService authService;
    
      public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping(
        path = "/api/auth/login",
        consumes = MediaType.APPLICATION_JSON_VALUE, 
        produces = MediaType.APPLICATION_JSON_VALUE
    )

    public WebResponse<TokenResponse> login(@RequestBody LoginUserRequest request) {
        TokenResponse tokenResponse = authService.login(request);
        return new WebResponse<>(
        tokenResponse,
        "Register successfully",
        null,
        null
        );
    }
}

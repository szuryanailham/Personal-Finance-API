package com.ilham.personal_finance_api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ilham.personal_finance_api.dto.OtpSentResponse;
import com.ilham.personal_finance_api.dto.RegisterUserRequest;
import com.ilham.personal_finance_api.dto.RegisterUserResponse;
import com.ilham.personal_finance_api.dto.ResendOtpRequest;
import com.ilham.personal_finance_api.dto.VerifyOtpRequest;
import com.ilham.personal_finance_api.dto.UserResponse;
import com.ilham.personal_finance_api.dto.WebResponse;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.services.RegistrationService;
import com.ilham.personal_finance_api.services.UserService;

import jakarta.validation.Valid;

@RestController
public class UserController {

    private final RegistrationService registrationService;
    private final UserService userService;

    public UserController(RegistrationService registrationService, UserService userService) {
        this.registrationService = registrationService;
        this.userService = userService;
    }

    @GetMapping(
        path = "/api/users/me",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public WebResponse<UserResponse> me(User user) {
        UserResponse response = userService.get(user);
        WebResponse<UserResponse> webResponse = new WebResponse<>();
        webResponse.setData(response);
        webResponse.setMessage("Get current user successfully");
        return webResponse;
    }

    @PostMapping(
        path = "/api/auth/sign-up",
        produces = MediaType.APPLICATION_JSON_VALUE,
        consumes = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    public WebResponse<OtpSentResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        OtpSentResponse response = registrationService.register(request);
        WebResponse<OtpSentResponse> webResponse = new WebResponse<>();
        webResponse.setData(response);
        webResponse.setMessage("OTP has been sent to your email");
        return webResponse;
    }

    @PostMapping(
        path = "/api/auth/sign-up/verify",
        produces = MediaType.APPLICATION_JSON_VALUE,
        consumes = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    public WebResponse<RegisterUserResponse> verify(@Valid @RequestBody VerifyOtpRequest request) {
        RegisterUserResponse response = registrationService.verify(request);
        WebResponse<RegisterUserResponse> webResponse = new WebResponse<>();
        webResponse.setData(response);
        webResponse.setMessage("Register successfully");
        return webResponse;
    }

    @PostMapping(
        path = "/api/auth/sign-up/resend",
        produces = MediaType.APPLICATION_JSON_VALUE,
        consumes = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    public WebResponse<OtpSentResponse> resend(@Valid @RequestBody ResendOtpRequest request) {
        OtpSentResponse response = registrationService.resend(request);
        WebResponse<OtpSentResponse> webResponse = new WebResponse<>();
        webResponse.setData(response);
        webResponse.setMessage("OTP has been sent to your email");
        return webResponse;
    }

}

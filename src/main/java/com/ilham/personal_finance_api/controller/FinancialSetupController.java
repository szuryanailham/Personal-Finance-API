package com.ilham.personal_finance_api.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.ilham.personal_finance_api.dto.FinancialSetupRequest;
import com.ilham.personal_finance_api.dto.FinancialSetupResponse;
import com.ilham.personal_finance_api.dto.WebResponse;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.services.FinancialSetupService;

import jakarta.validation.Valid;

@RestController
public class FinancialSetupController {

    @Autowired
    private FinancialSetupService financialSetupService;

    @GetMapping(
        path = "/api/financial-setup",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public WebResponse<FinancialSetupResponse> get(User user) {
        WebResponse<FinancialSetupResponse> response = new WebResponse<>();
        response.setData(financialSetupService.get(user));
        return response;
    }

    // body boleh kosong: kedua saldo awal dianggap 0
    @PutMapping(
        path = "/api/financial-setup",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public WebResponse<FinancialSetupResponse> complete(User user, @Valid @RequestBody(required = false) FinancialSetupRequest request) {
        FinancialSetupRequest body = request == null ? new FinancialSetupRequest() : request;
        WebResponse<FinancialSetupResponse> response = new WebResponse<>();
        response.setData(financialSetupService.complete(user, body));
        response.setMessage("Financial setup completed successfully");
        return response;
    }

    @PostMapping(
        path = "/api/financial-setup/skip",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public WebResponse<FinancialSetupResponse> skip(User user) {
        WebResponse<FinancialSetupResponse> response = new WebResponse<>();
        response.setData(financialSetupService.skip(user));
        response.setMessage("Financial setup skipped");
        return response;
    }
}

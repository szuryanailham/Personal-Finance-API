package com.ilham.personal_finance_api.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ilham.personal_finance_api.dto.CreateTransferRequest;
import com.ilham.personal_finance_api.dto.PaginationResponse;
import com.ilham.personal_finance_api.dto.TransferResponse;
import com.ilham.personal_finance_api.dto.WebResponse;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.services.TransferService;

import jakarta.validation.Valid;

@RestController
public class TransferController {

    @Autowired
    private TransferService transferService;

    @PostMapping(
        path = "/api/transfers",
        produces = MediaType.APPLICATION_JSON_VALUE,
        consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public WebResponse<TransferResponse> create(User user, @Valid @RequestBody CreateTransferRequest request) {
        WebResponse<TransferResponse> response = new WebResponse<>();
        response.setData(transferService.create(user, request));
        response.setMessage("Transfer created successfully");
        return response;
    }

    @GetMapping(
        path = "/api/transfers",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public WebResponse<List<TransferResponse>> getAll(
        User user,
        @RequestParam(defaultValue = "0") int skip,
        @RequestParam(defaultValue = "10") int limit
    ) {
        Page<TransferResponse> transfers = transferService.getAll(user, skip, limit);

        PaginationResponse paging = new PaginationResponse();
        paging.setCurrentPage(transfers.getNumber() + 1);
        paging.setTotalPage(transfers.getTotalPages());
        paging.setSize(transfers.getSize());
        paging.setTotalItem(transfers.getTotalElements());

        WebResponse<List<TransferResponse>> response = new WebResponse<>();
        response.setData(transfers.getContent());
        response.setPaging(paging);
        return response;
    }
}

package com.ilham.personal_finance_api.controller;

import java.util.List;
import com.ilham.personal_finance_api.blog.service.BlogAuthService;
import com.ilham.personal_finance_api.dto.AdminUserResponse;
import com.ilham.personal_finance_api.dto.PaginationResponse;
import com.ilham.personal_finance_api.dto.WebResponse;
import com.ilham.personal_finance_api.repository.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@ConditionalOnProperty(name = "app.blog.enabled", havingValue = "true")
public class AdminUserController {
    private final BlogAuthService auth;
    private final UserRepository users;

    public AdminUserController(BlogAuthService auth, UserRepository users) {
        this.auth = auth;
        this.users = users;
    }

    @GetMapping("/api/admin/users")
    public WebResponse<List<AdminUserResponse>> list(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (!auth.me(authorization).roles().contains("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN role is required");
        }
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0 and size must be between 1 and 100");
        }
        var result = users.findAdminUsers(PageRequest.of(page, size));
        var paging = new PaginationResponse();
        paging.setCurrentPage(page);
        paging.setSize(size);
        paging.setTotalPage(result.getTotalPages());
        paging.setTotalItem(result.getTotalElements());
        WebResponse<List<AdminUserResponse>> response = new WebResponse<>();
        response.setData(result.getContent());
        response.setPaging(paging);
        response.setMessage("Get registered users successfully");
        return response;
    }
}

package com.ilham.personal_finance_api.controller;

import com.ilham.personal_finance_api.blog.dto.BlogUserResponse;
import com.ilham.personal_finance_api.blog.service.BlogAuthService;
import com.ilham.personal_finance_api.dto.AdminUserResponse;
import com.ilham.personal_finance_api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminUserControllerTest {
    BlogAuthService auth;
    UserRepository users;
    MockMvc mvc;

    @BeforeEach void setup() {
        auth = mock(BlogAuthService.class);
        users = mock(UserRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(new AdminUserController(auth, users)).build();
    }

    void role(String role) {
        when(auth.me("Bearer admin")).thenReturn(new BlogUserResponse(
                UUID.randomUUID(), "Admin", "admin@example.com", "ACTIVE", List.of(role)));
    }

    @Test void listsUsersIncludingThoseWithoutTransactions() throws Exception {
        role("ADMIN");
        var rows = List.of(new AdminUserResponse(UUID.randomUUID(), "New", "User", "new@example.com", 0L, 0L),
                new AdminUserResponse(UUID.randomUUID(), "Active", "User", "active@example.com", 5L, 3L));
        when(users.findAdminUsers(PageRequest.of(0, 20))).thenReturn(new PageImpl<>(rows));
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].email").value("new@example.com"))
                .andExpect(jsonPath("$.data[0].transactionCount").value(0))
                .andExpect(jsonPath("$.data[1].transactionCount").value(5))
                .andExpect(jsonPath("$.data[1].activeTransactionCount").value(3))
                .andExpect(jsonPath("$.data[0].password").doesNotExist())
                .andExpect(jsonPath("$.paging.totalItem").value(2));
    }

    @Test void rejectsNonAdminsBeforeReadingUsers() throws Exception {
        role("AUTHOR");
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer admin"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(users);
    }

    @Test void rejectsUnauthenticatedRequests() throws Exception {
        when(auth.me(null)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        verifyNoInteractions(users);
    }

    @Test void validatesPagination() throws Exception {
        role("ADMIN");
        for (String query : List.of("page=-1", "size=0", "size=101", "page=invalid")) {
            mvc.perform(get("/api/admin/users?" + query).header("Authorization", "Bearer admin"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(users);
    }
}

package com.ilham.personal_finance_api.blog;

import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.ilham.personal_finance_api.dto.WebResponse;
import static com.ilham.personal_finance_api.blog.BlogAuthModels.*;

@RestController
@RequestMapping("/api/blog/auth")
@ConditionalOnProperty(name = "app.blog.enabled", havingValue = "true")
public class BlogAuthController {
    private final BlogAuthService auth;

    public BlogAuthController(BlogAuthService auth) { this.auth = auth; }

    @PostMapping("/register")
    public ResponseEntity<WebResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request,
            @RequestHeader(value = "X-Blog-Registration-Key", required = false) String key) {
        return response(HttpStatus.CREATED, auth.register(request, key));
    }

    @PostMapping("/login")
    public ResponseEntity<WebResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return response(HttpStatus.OK, auth.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<WebResponse<UserResponse>> me(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return response(HttpStatus.OK, auth.me(authorization));
    }

    @PostMapping("/logout")
    public ResponseEntity<WebResponse<Void>> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        auth.logout(authorization);
        return response(HttpStatus.OK, null);
    }

    private static <T> ResponseEntity<WebResponse<T>> response(HttpStatus status, T data) {
        var body = new WebResponse<T>();
        body.setData(data);
        return ResponseEntity.status(status).cacheControl(org.springframework.http.CacheControl.noStore()).body(body);
    }
}

package com.ilham.personal_finance_api.blog.controller;

import com.ilham.personal_finance_api.blog.service.BlogPostService;

import com.ilham.personal_finance_api.blog.dto.BlogPostResponse;
import com.ilham.personal_finance_api.blog.dto.CreateBlogPostRequest;

import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import com.ilham.personal_finance_api.dto.WebResponse;

@RestController
@RequestMapping("/api/blog/posts")
@ConditionalOnProperty(name = "app.blog.enabled", havingValue = "true")
public class BlogPostController {
    private final BlogPostService service;

    public BlogPostController(BlogPostService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<WebResponse<BlogPostResponse>> create(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody CreateBlogPostRequest request) {
        var body = new WebResponse<BlogPostResponse>();
        body.setData(service.create(authorization, request));
        body.setMessage("Blog post created successfully");
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(body);
    }
}

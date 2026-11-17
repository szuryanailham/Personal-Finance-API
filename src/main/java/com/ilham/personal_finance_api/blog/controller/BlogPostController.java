package com.ilham.personal_finance_api.blog.controller;

import com.ilham.personal_finance_api.blog.service.BlogPostService;

import com.ilham.personal_finance_api.blog.dto.BlogPostResponse;
import com.ilham.personal_finance_api.blog.dto.CreateBlogPostRequest;

import com.ilham.personal_finance_api.blog.dto.BlogPostListResponse;

import com.ilham.personal_finance_api.blog.dto.UpdateBlogPostRequest;

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

    @GetMapping
    public ResponseEntity<BlogPostListResponse> list(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(defaultValue = "0") long skip,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.list(authorization, skip, limit));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<WebResponse<BlogPostResponse>> detail(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable String slug) {
        var body = new WebResponse<BlogPostResponse>();
        body.setData(service.detail(authorization, slug));
        body.setMessage("Blog post retrieved successfully");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    @DeleteMapping("/{slug}")
    public ResponseEntity<WebResponse<Void>> delete(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable String slug) {
        service.delete(authorization, slug);
        var body = new WebResponse<Void>();
        body.setMessage("Blog post deleted successfully");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    @PatchMapping("/{slug}")
    public ResponseEntity<WebResponse<BlogPostResponse>> update(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable String slug, @Valid @RequestBody UpdateBlogPostRequest request) {
        var body = new WebResponse<BlogPostResponse>();
        body.setData(service.update(authorization, slug, request));
        body.setMessage("Blog post updated successfully");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

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

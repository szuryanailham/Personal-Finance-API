package com.ilham.personal_finance_api.blog.controller;

import com.ilham.personal_finance_api.blog.dto.PublicBlogPostListResponse;
import com.ilham.personal_finance_api.blog.dto.PublicBlogPostResponse;
import com.ilham.personal_finance_api.blog.service.BlogPostService;
import com.ilham.personal_finance_api.dto.WebResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/blog/published")
@ConditionalOnProperty(name = "app.blog.enabled", havingValue = "true")
public class PublicBlogPostController {
    private final BlogPostService service;

    public PublicBlogPostController(BlogPostService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<PublicBlogPostListResponse> list(
            @RequestParam(defaultValue = "0") long skip,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.publicList(skip, limit));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<WebResponse<PublicBlogPostResponse>> detail(@PathVariable String slug) {
        var body = new WebResponse<PublicBlogPostResponse>();
        body.setData(service.publicDetail(slug));
        body.setMessage("Blog article retrieved successfully");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}

package com.ilham.personal_finance_api.blog.service;

import com.ilham.personal_finance_api.blog.repository.BlogStore;

import com.ilham.personal_finance_api.blog.dto.BlogPostResponse;
import com.ilham.personal_finance_api.blog.dto.CreateBlogPostRequest;

import java.time.Clock;
import java.util.HashSet;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(name = "app.blog.enabled", havingValue = "true")
public class BlogPostService {
    private final BlogStore store;
    private final BlogAuthService auth;
    private final Clock clock;

    public BlogPostService(BlogStore store, BlogAuthService auth, Clock clock) {
        this.store = store;
        this.auth = auth;
        this.clock = clock;
    }

    public BlogPostResponse create(String authorization, CreateBlogPostRequest request) {
        var user = auth.me(authorization);
        boolean editor = user.roles().stream().anyMatch(r -> r.equals("ADMIN") || r.equals("EDITOR"));
        if (!editor && !user.roles().contains("AUTHOR")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A CMS author role is required");
        }
        if (request.status().equals("PUBLISHED") && !editor) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN or EDITOR role is required to publish");
        }
        if (new HashSet<>(request.categoryIds()).size() != request.categoryIds().size()
                || new HashSet<>(request.tagIds()).size() != request.tagIds().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate category or tag IDs are not allowed");
        }
        String html = Jsoup.clean(request.contentHtml(), Safelist.relaxed()
                .addAttributes("a", "rel").addEnforcedAttribute("a", "rel", "nofollow noopener noreferrer")
                .removeProtocols("img", "src", "ftp"));
        String text = Jsoup.parse(html).text().strip();
        if (text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content must contain readable text");
        }
        int minutes = Math.max(1, (text.split("\\s+").length + 199) / 200);
        try {
            return store.createPost(user.id(), request, html, minutes, clock.instant());
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Post slug is already used or reserved");
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid category or tag reference");
        }
    }
}

package com.ilham.personal_finance_api.blog.service;

import com.ilham.personal_finance_api.blog.repository.BlogStore;

import com.ilham.personal_finance_api.blog.dto.BlogPostResponse;
import com.ilham.personal_finance_api.blog.dto.CreateBlogPostRequest;

import com.ilham.personal_finance_api.blog.dto.BlogPostListResponse;

import com.ilham.personal_finance_api.blog.dto.UpdateBlogPostRequest;
import com.ilham.personal_finance_api.blog.dto.PublicBlogPostListResponse;
import com.ilham.personal_finance_api.blog.dto.PublicBlogPostResponse;
import java.util.UUID;

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

    public PublicBlogPostListResponse publicList(long skip, int limit) {
        if (skip < 0 || limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use skip >= 0 and limit between 1 and 100");
        }
        return store.publicPosts(skip, limit, clock.instant());
    }

    public PublicBlogPostResponse publicDetail(String slug) {
        if (slug.length() > 200 || !slug.matches("[a-z0-9]+(-[a-z0-9]+)*")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Blog article not found");
        }
        return store.publicPost(slug, clock.instant()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Blog article not found"));
    }

    public BlogPostListResponse list(String authorization, long skip, int limit) {
        var user = auth.me(authorization);
        if (user.roles().stream().noneMatch(role -> role.equals("AUTHOR")
                || role.equals("EDITOR") || role.equals("ADMIN"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A CMS author role is required");
        }
        if (skip < 0 || limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use skip >= 0 and limit between 1 and 100");
        }
        return store.posts(skip, limit);
    }

    public BlogPostResponse detail(String authorization, UUID id) {
        var user = auth.me(authorization);
        boolean editor = user.roles().stream().anyMatch(r -> r.equals("ADMIN") || r.equals("EDITOR"));
        if (!editor && !user.roles().contains("AUTHOR")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A CMS author role is required");
        }
        var post = store.post(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Blog post not found"));
        if (!editor && !user.id().equals(post.authorId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authors may only view their own posts");
        }
        return post;
    }

    public void delete(String authorization, UUID id) {
        var user = auth.me(authorization);
        boolean editor = user.roles().stream().anyMatch(r -> r.equals("ADMIN") || r.equals("EDITOR"));
        if (!editor && !user.roles().contains("AUTHOR")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A CMS author role is required");
        }
        store.softDeletePost(id, clock.instant(), (authorId, status) -> {
            if (!editor && (!user.id().equals(authorId) || !status.equals("DRAFT"))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authors may only delete their own drafts");
            }
        });
    }

    public BlogPostResponse update(String authorization, UUID id, UpdateBlogPostRequest request) {
        var user = auth.me(authorization);
        boolean editor = user.roles().stream().anyMatch(r -> r.equals("ADMIN") || r.equals("EDITOR"));
        if (!editor && !user.roles().contains("AUTHOR")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A CMS author role is required");
        }
        if (request.empty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supply at least one field to update");
        try {
            return store.updatePost(id, current -> {
                String status = request.has("status") ? request.status() : current.status();
                if (!editor && (!user.id().equals(current.authorId()) || !current.status().equals("DRAFT")
                        || !status.equals("DRAFT"))) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authors may only edit their own drafts");
                }
                var categories = request.has("categoryIds") ? request.categoryIds() : current.categoryIds();
                var tags = request.has("tagIds") ? request.tagIds() : current.tagIds();
                if (new HashSet<>(categories).size() != categories.size() || new HashSet<>(tags).size() != tags.size()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate category or tag IDs are not allowed");
                }
                String html = current.contentHtml();
                int minutes = current.readingTimeMinutes();
                if (request.has("contentHtml")) {
                    html = sanitize(request.contentHtml());
                    minutes = readingMinutes(html);
                }
                var publishedAt = status.equals("PUBLISHED") && !current.status().equals("PUBLISHED")
                        ? clock.instant() : current.publishedAt();
                return new BlogPostResponse(current.id(), current.authorId(),
                        request.has("title") ? request.title() : current.title(),
                        request.has("slug") ? request.slug() : current.slug(),
                        request.has("excerpt") ? request.excerpt() : current.excerpt(), html, status,
                        publishedAt, minutes, categories, tags, current.createdAt(), current.updatedAt());
            });
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Post slug is already used or reserved");
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid category or tag reference");
        }
    }

    private static String sanitize(String html) {
        return Jsoup.clean(html, Safelist.relaxed()
                .addAttributes("a", "rel").addEnforcedAttribute("a", "rel", "nofollow noopener noreferrer")
                .removeProtocols("img", "src", "ftp"));
    }

    private static int readingMinutes(String html) {
        String text = Jsoup.parse(html).text().strip();
        if (text.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content must contain readable text");
        return Math.max(1, (text.split("\\s+").length + 199) / 200);
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
        String html = sanitize(request.contentHtml());
        int minutes = readingMinutes(html);
        try {
            return store.createPost(user.id(), request, html, minutes, clock.instant());
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Post slug is already used or reserved");
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid category, tag, or featured media reference");
        }
    }
}

package com.ilham.personal_finance_api.blog;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static com.ilham.personal_finance_api.blog.BlogTaxonomyModels.*;

@Service
@ConditionalOnProperty(name = "app.blog.enabled", havingValue = "true")
public class BlogTaxonomyService {
    private final BlogStore store;
    private final BlogAuthService auth;

    public BlogTaxonomyService(BlogStore store, BlogAuthService auth) {
        this.store = store;
        this.auth = auth;
    }

    private void authorize(String authorization, boolean write) {
        var user = auth.me(authorization);
        if (write && user.roles().stream().noneMatch(role -> role.equals("ADMIN") || role.equals("EDITOR"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN or EDITOR role is required");
        }
    }

    TermPage list(Kind kind, String authorization, int page, int size, String search) {
        authorize(authorization, false);
        if (page < 0 || size < 1 || size > 100 || search.length() > 150) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use page >= 0, size 1-100, and search up to 150 characters");
        }
        return store.terms(kind, page, size, search.strip());
    }

    TermResponse get(Kind kind, String authorization, UUID id) {
        authorize(authorization, false);
        return store.term(kind, id).orElseThrow(BlogTaxonomyService::notFound);
    }

    TermResponse create(Kind kind, String authorization, String name, String slug, String description) {
        authorize(authorization, true);
        try {
            return store.createTerm(kind, name, slug, description);
        } catch (DuplicateKeyException ex) {
            throw slugConflict();
        }
    }

    TermResponse update(Kind kind, String authorization, UUID id, String name, String slug, String description) {
        authorize(authorization, true);
        try {
            return store.updateTerm(kind, id, name, slug, description).orElseThrow(BlogTaxonomyService::notFound);
        } catch (DuplicateKeyException ex) {
            throw slugConflict();
        }
    }

    void delete(Kind kind, String authorization, UUID id) {
        authorize(authorization, true);
        try {
            if (!store.deleteTerm(kind, id)) throw notFound();
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category or tag is still used by a post");
        }
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Category or tag not found");
    }

    private static ResponseStatusException slugConflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Slug is already used");
    }
}

package com.ilham.personal_finance_api.blog;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.ilham.personal_finance_api.dto.WebResponse;
import static com.ilham.personal_finance_api.blog.BlogTaxonomyModels.*;
import static com.ilham.personal_finance_api.blog.BlogTaxonomyModels.Kind.CATEGORY;

@RestController
@RequestMapping("/api/blog/categories")
@ConditionalOnProperty(name = "app.blog.enabled", havingValue = "true")
public class BlogCategoryController {
    private final BlogTaxonomyService service;

    public BlogCategoryController(BlogTaxonomyService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<WebResponse<List<TermResponse>>> list(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String search) {
        var result = service.list(CATEGORY, authorization, page, size, search);
        var body = new WebResponse<List<TermResponse>>();
        body.setData(result.items());
        body.setPaging(result.paging());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<WebResponse<TermResponse>> get(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {
        return response(HttpStatus.OK, service.get(CATEGORY, authorization, id));
    }

    @PostMapping
    public ResponseEntity<WebResponse<TermResponse>> create(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody CategoryRequest request) {
        return response(HttpStatus.CREATED, service.create(CATEGORY, authorization,
                request.name(), request.slug(), request.description()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WebResponse<TermResponse>> update(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return response(HttpStatus.OK, service.update(CATEGORY, authorization, id,
                request.name(), request.slug(), request.description()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<WebResponse<Void>> delete(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {
        service.delete(CATEGORY, authorization, id);
        var body = new WebResponse<Void>();
        body.setMessage("Category deleted successfully");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    private static <T> ResponseEntity<WebResponse<T>> response(HttpStatus status, T data) {
        var body = new WebResponse<T>();
        body.setData(data);
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }
}

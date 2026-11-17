package com.ilham.personal_finance_api.blog.controller;

import com.ilham.personal_finance_api.blog.service.BlogAuthService;
import com.ilham.personal_finance_api.dto.WebResponse;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/blog/media")
@ConditionalOnProperty(name = "app.blog.enabled", havingValue = "true")
public class BlogMediaController {
    private final BlogAuthService auth;
    private final JdbcTemplate jdbc;
    private final String privateKey;
    private final RestClient imageKit;

    @org.springframework.beans.factory.annotation.Autowired
    public BlogMediaController(BlogAuthService auth, @Qualifier("blogDataSource") DataSource dataSource,
            @Value("${IMAGEKIT_PRIVATE_KEY:}") String privateKey) {
        this(auth, dataSource, privateKey, RestClient.builder());
    }

    public BlogMediaController(BlogAuthService auth, DataSource dataSource, String privateKey,
            RestClient.Builder imageKitBuilder) {
        this.imageKit = imageKitBuilder.build();
        this.auth = auth;
        this.jdbc = new JdbcTemplate(dataSource);
        this.privateKey = privateKey;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<WebResponse<Map<String, Object>>> upload(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", required = false) String type) throws java.io.IOException {
        var user = auth.me(authorization);
        if (user.roles().stream().noneMatch(role -> role.equals("ADMIN") || role.equals("EDITOR") || role.equals("AUTHOR"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A CMS author role is required");
        }
        String folder = type == null ? "/blog/hero" : switch (type) {
            case "HERO" -> "/blog/hero";
            case "CONTENT" -> "/blog/content";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type harus HERO atau CONTENT.");
        };
        if (privateKey.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "IMAGEKIT_PRIVATE_KEY belum dikonfigurasi pada server backend.");
        String mime = file.getContentType();
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024 || mime == null
                || !java.util.Set.of("image/jpeg", "image/png", "image/webp", "image/gif").contains(mime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gunakan JPG, PNG, WebP, atau GIF maksimal 5 MB.");
        }
        byte[] bytes = file.getBytes();
        // Check signatures too; WebP has no decoder in the default JDK ImageIO installation.
        boolean webp = bytes.length >= 12 && new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                && new String(bytes, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
        if (mime.equals("image/webp") ? !webp : ImageIO.read(new ByteArrayInputStream(bytes)) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File gambar tidak valid.");
        }
        UUID id = UUID.randomUUID();
        String extension = switch (mime) { case "image/jpeg" -> ".jpg"; case "image/png" -> ".png"; case "image/gif" -> ".gif"; default -> ".webp"; };
        var form = new LinkedMultiValueMap<String, Object>();
        var headers = new HttpHeaders(); headers.setContentType(MediaType.parseMediaType(mime));
        form.add("file", new HttpEntity<>(new ByteArrayResource(bytes) {
            @Override public String getFilename() { return id + extension; }
        }, headers));
        form.add("fileName", id + extension);
        form.add("folder", folder);
        Map<?, ?> result;
        try {
            result = imageKit.post().uri("https://upload.imagekit.io/api/v1/files/upload")
                    .headers(h -> h.setBasicAuth(privateKey, ""))
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().body(Map.class);
        } catch (org.springframework.web.client.RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gagal upload ke ImageKit. Periksa konfigurasi server.");
        }
        if (result == null || !(result.get("url") instanceof String url) || !url.startsWith("https://")
                || !(result.get("fileId") instanceof String assetId) || assetId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Respons ImageKit tidak valid.");
        }
        jdbc.update("INSERT INTO media(id, uploaded_by, url, mime_type, file_size_bytes, storage_provider, provider_asset_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, user.id(), url, mime, file.getSize(), "imagekit", assetId);
        var body = new WebResponse<Map<String, Object>>();
        body.setData(Map.of("id", id, "url", url));
        body.setMessage("Gambar berhasil diunggah ke ImageKit");
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(body);
    }
}

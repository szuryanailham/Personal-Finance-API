package com.ilham.personal_finance_api;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.ilham.personal_finance_api.resolver.UserArgumentResolver;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    // Lama browser boleh cache hasil preflight (detik)
    private static final long CORS_MAX_AGE_SECONDS = 3600;

    @Autowired
    private UserArgumentResolver userArgumentResolver;

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:30001,https://cms.myartoku.com}")
    private String[] allowedOrigins;

    @Value("${app.blog.cors.allowed-origins:https://www.myartoku.com}")
    private String[] blogPublicOrigins;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        WebMvcConfigurer.super.addArgumentResolvers(resolvers);
        resolvers.add(userArgumentResolver);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Register specific public routes before the general API mapping.
        for (String path : new String[]{"/api/blog/published", "/api/blog/published/**"}) {
            registry.addMapping(path)
                    .allowedOrigins(blogPublicOrigins)
                    .allowedMethods("GET", "OPTIONS")
                    .allowedHeaders("Accept", "Content-Type")
                    .maxAge(CORS_MAX_AGE_SECONDS);
        }
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept")
                // Retry-After bukan header CORS-safelisted, jadi harus di-expose agar bisa dibaca frontend
                .exposedHeaders("Retry-After")
                .maxAge(CORS_MAX_AGE_SECONDS);
    }
}

package com.ilham.personal_finance_api.blog;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.blog.enabled", havingValue = "true")
public class BlogConfiguration {
    // Non-default candidate preserves the finance datasource/JPA auto-configuration.
    @Bean(defaultCandidate = false, destroyMethod = "close")
    @Qualifier("blogDataSource")
    HikariDataSource blogDataSource(
            @Value("${app.blog.datasource.url}") String url,
            @Value("${app.blog.datasource.username}") String username,
            @Value("${app.blog.datasource.password}") String password) {
        var config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setPoolName("artoku-blog");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10000);
        return new HikariDataSource(config);
    }

    @Bean
    BlogStore blogStore(@Qualifier("blogDataSource") HikariDataSource dataSource,
            @Value("${app.blog.flyway.enabled:true}") boolean migrate,
            @Value("${app.blog.flyway.username:${app.blog.datasource.username}}") String username,
            @Value("${app.blog.flyway.password:${app.blog.datasource.password}}") String password) {
        // Do not declare a second Flyway bean: keep main auto-configuration unchanged.
        if (migrate) {
            Flyway.configure().dataSource(dataSource.getJdbcUrl(), username, password)
                    .locations("classpath:db/blog-migration")
                    .baselineOnMigrate(false).cleanDisabled(true)
                    .validateMigrationNaming(true).load().migrate();
        }
        return new BlogStore(dataSource);
    }
}

package com.ilham.personal_finance_api.blog.service;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(name = "app.blog.enabled", havingValue = "true")
public class ImageKitMediaClient {
    private final String privateKey;
    private final RestClient client;

    @Autowired
    public ImageKitMediaClient(@Value("${IMAGEKIT_PRIVATE_KEY:}") String privateKey) {
        this(privateKey, builder());
    }

    public ImageKitMediaClient(String privateKey, RestClient.Builder builder) {
        this.privateKey = privateKey;
        this.client = builder.build();
    }

    private static RestClient.Builder builder() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().requestFactory(factory);
    }

    public boolean configured() { return !privateKey.isBlank(); }

    public void delete(String fileId) {
        if (!configured() || fileId == null || fileId.isBlank()) {
            throw new IllegalStateException("ImageKit delete configuration or fileId is missing");
        }
        try {
            var response = client.delete().uri("https://api.imagekit.io/v1/files/{fileId}", fileId)
                    .headers(headers -> headers.setBasicAuth(privateKey, ""))
                    .retrieve().toBodilessEntity();
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RestClientException("Unexpected ImageKit delete response");
            }
        } catch (RestClientResponseException ex) {
            // A previous attempt may have deleted the file before its DB commit failed.
            if (ex.getStatusCode().value() != 404) throw ex;
        }
    }
}

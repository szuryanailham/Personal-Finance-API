package com.ilham.personal_finance_api.blog;

import com.ilham.personal_finance_api.blog.service.ImageKitMediaClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ImageKitMediaClientTest {
    @Test void deletesByFileIdWithPrivateKeyAndAcceptsAlreadyDeletedFiles() {
        for (HttpStatus status : new HttpStatus[]{HttpStatus.NO_CONTENT, HttpStatus.NOT_FOUND}) {
            var builder = RestClient.builder();
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo("https://api.imagekit.io/v1/files/file-123"))
                    .andExpect(method(HttpMethod.DELETE))
                    .andExpect(header("Authorization", "Basic cHJpdmF0ZTo="))
                    .andRespond(withStatus(status));
            new ImageKitMediaClient("private", builder).delete("file-123");
            server.verify();
        }
    }

    @Test void propagatesFailuresForPersistentRetryAndDoesNotSendEmptyIds() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new ImageKitMediaClient("private", builder);
        assertThrows(IllegalStateException.class, () -> client.delete(""));
        for (HttpStatus status : new HttpStatus[]{HttpStatus.TOO_MANY_REQUESTS, HttpStatus.FOUND, HttpStatus.INTERNAL_SERVER_ERROR}) {
            server.expect(requestTo("https://api.imagekit.io/v1/files/file-123"))
                    .andRespond(withStatus(status));
        }
        for (int i = 0; i < 3; i++) {
            assertThrows(RestClientException.class, () -> client.delete("file-123"));
        }
        server.verify();
    }
}

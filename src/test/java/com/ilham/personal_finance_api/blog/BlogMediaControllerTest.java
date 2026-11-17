package com.ilham.personal_finance_api.blog;

import com.ilham.personal_finance_api.blog.controller.BlogMediaController;
import com.ilham.personal_finance_api.blog.dto.BlogUserResponse;
import com.ilham.personal_finance_api.blog.service.BlogAuthService;
import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BlogMediaControllerTest {
    private BlogAuthService auth(String role) {
        var auth = mock(BlogAuthService.class);
        when(auth.me("Bearer cms")).thenReturn(new BlogUserResponse(UUID.randomUUID(), "Author", "a@example.com", "ACTIVE", List.of(role)));
        return auth;
    }
    @Test void rejectsMissingConfigurationBeforeUploading() {
        var controller = new BlogMediaController(auth("AUTHOR"), mock(DataSource.class), "");
        var error = assertThrows(ResponseStatusException.class, () -> controller.upload("Bearer cms", new MockMultipartFile("file", new byte[]{1}), null));
        assertEquals(503, error.getStatusCode().value());
    }
    @Test void rejectsUsersWithoutCmsAuthorRole() {
        var controller = new BlogMediaController(auth("READER"), mock(DataSource.class), "private_test");
        var error = assertThrows(ResponseStatusException.class, () -> controller.upload("Bearer cms", new MockMultipartFile("file", new byte[]{1}), null));
        assertEquals(403, error.getStatusCode().value());
    }
    @Test void rejectsDisguisedAndOversizedFilesBeforeNetworkOrDatabaseAccess() {
        var source = mock(DataSource.class);
        var controller = new BlogMediaController(auth("EDITOR"), source, "private_test");
        for (var file : List.of(
                new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes()),
                new MockMultipartFile("file", "huge.png", "image/png", new byte[5 * 1024 * 1024 + 1]),
                new MockMultipartFile("file", "script.svg", "image/svg+xml", "<svg/>".getBytes()))) {
            var error = assertThrows(ResponseStatusException.class, () -> controller.upload("Bearer cms", file, null));
            assertEquals(400, error.getStatusCode().value());
        }
        verifyNoInteractions(source);
    }

    @Test void rejectsUnsupportedTypesBeforeNetworkOrDatabaseAccess() {
        var source = mock(DataSource.class);
        var builder = org.springframework.web.client.RestClient.builder();
        var server = org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
        var controller = new BlogMediaController(auth("AUTHOR"), source, "private_test", builder);
        for (String type : List.of("", "hero", "content", "OTHER", "/blog/content")) {
            var error = assertThrows(ResponseStatusException.class,
                    () -> controller.upload("Bearer cms", new MockMultipartFile("file", new byte[]{1}), type));
            assertEquals(400, error.getStatusCode().value());
        }
        server.verify();
        verifyNoInteractions(source);
    }

    @Test void uploadsEachTypeAndPersistsTheReturnedMediaReference() throws Exception {
        for (String type : new String[]{"HERO", "CONTENT", null}) {
            var source = mock(DataSource.class);
            var connection = mock(java.sql.Connection.class);
            var statement = mock(java.sql.PreparedStatement.class);
            when(source.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(statement);
            when(statement.executeUpdate()).thenReturn(1);
            var builder = org.springframework.web.client.RestClient.builder();
            var server = org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
            String folder = "CONTENT".equals(type) ? "/blog/content" : "/blog/hero";
            String url = "https://ik.imagekit.io/test" + folder + "/image.png";
            server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(
                    "https://upload.imagekit.io/api/v1/files/upload"))
                    .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.method(
                            org.springframework.http.HttpMethod.POST))
                    .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content().string(
                            org.hamcrest.Matchers.containsString("\r\n\r\n" + folder + "\r\n")))
                    .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                            "{\"url\":\"" + url + "\",\"fileId\":\"asset-id\"}",
                            org.springframework.http.MediaType.APPLICATION_JSON));
            var auth = auth("AUTHOR");
            var controller = new BlogMediaController(auth, source, "private_test", builder);
            var bytes = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1,
                    java.awt.image.BufferedImage.TYPE_INT_RGB), "png", bytes);
            var response = controller.upload("Bearer cms",
                    new MockMultipartFile("file", "image.png", "image/png", bytes.toByteArray()), type);
            assertEquals(201, response.getStatusCode().value());
            assertEquals(url, response.getBody().getData().get("url"));
            verify(statement).setObject(1, response.getBody().getData().get("id"));
            verify(statement).setObject(2, auth.me("Bearer cms").id());
            verify(statement).setString(3, url);
            verify(statement).setString(4, "image/png");
            verify(statement).setString(6, "imagekit");
            verify(statement).setString(7, "asset-id");
            verify(statement).executeUpdate();
            server.verify();
        }
    }
}

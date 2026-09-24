package com.ilham.personal_finance_api.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.dto.RegisterUserRequest;
import com.ilham.personal_finance_api.dto.WebResponse;
import com.ilham.personal_finance_api.AbstractIntegrationTest;
import com.ilham.personal_finance_api.repository.CategoryRepository;
import com.ilham.personal_finance_api.repository.TransactionRepository;
import com.ilham.personal_finance_api.repository.UserRepository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
public class UserControllerTest extends AbstractIntegrationTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test 
void testRegisterSuccess() throws Exception {

    RegisterUserRequest request = new RegisterUserRequest();

    request.setFirstName("test");
    request.setLastName("test");
    request.setPassword("test12345");
    request.setEmail("test@gmail.com");

    mockMvc.perform(
        post("/api/auth/sign-up")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request))
    )
    .andExpectAll(
        status().isOk()
    )
    .andDo(result -> {
        WebResponse<String> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<String>>() {}
        );

        System.out.println("Response: " + response);
    });
}

@Test 
void testRegisterBadRequest() throws Exception {
    RegisterUserRequest request = new RegisterUserRequest();
    request.setFirstName("");
    request.setLastName("");
    request.setPassword("");
    request.setPassword("");

    mockMvc.perform(
        post("/api/auth/sign-up")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request))
    ).andExpectAll(
        status().isBadRequest()
    ).andDo(result -> {
        WebResponse<String> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<String>>() {}
        );
        assertNotNull(response.getErrors());
    });
}

@Test
void testRegisterDuplicate() throws Exception {

    User user = new User();
    user.setFirstName("test");
    user.setLastName("test");
    user.setEmail("test@gmail.com");
    user.setPassword("rahasia");

    userRepository.save(user);

    RegisterUserRequest request = new RegisterUserRequest();
    
    request.setFirstName("test");
    request.setLastName("test");
    request.setEmail("test@gmail.com");
    request.setPassword("rahasia");

    mockMvc.perform(
        post("/api/auth/sign-up")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request))
    ).andExpectAll(status().isConflict()).andDo(result -> {
         WebResponse<String> response = objectMapper.readValue(
                    result.getResponse().getContentAsString(),
                    new TypeReference<WebResponse<String>>() {}
            );
        assertNotNull(response.getErrors());
    });
}

}
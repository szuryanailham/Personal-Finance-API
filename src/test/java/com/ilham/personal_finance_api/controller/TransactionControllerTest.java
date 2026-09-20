package com.ilham.personal_finance_api.controller;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import com.ilham.personal_finance_api.entity.Category;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.model.CreateTransactionRequest;
import com.ilham.personal_finance_api.model.CreateTransactionResponse;
import com.ilham.personal_finance_api.model.WebResponse;
import com.ilham.personal_finance_api.model.CreateCategoryRequest.CategoryType;
import com.ilham.personal_finance_api.repository.CategoryRepository;
import com.ilham.personal_finance_api.repository.TransactionRepository;
import com.ilham.personal_finance_api.repository.UserRepository;
import com.ilham.personal_finance_api.security.BCrypt;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

 @SpringBootTest 
   @AutoConfigureMockMvc 

public class TransactionControllerTest {

@Autowired 
private TransactionRepository transactionRepository;

@Autowired 
private ObjectMapper objectMapper;


@Autowired 
private MockMvc mockMvc;

@Autowired 
private UserRepository userRepository;

@Autowired 
private CategoryRepository categoryRepository ;

private User user;

private Category category;

@BeforeEach 
void setUp() {
   transactionRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();       
        user = new User();
        user.setFirstName("test");
        user.setLastName("test");
        user.setEmail("test@gmail.com");
        user.setPassword(BCrypt.hashpw("test", BCrypt.gensalt()));
        user.setToken("test");
        user.setTokenExpiredAt(System.currentTimeMillis()+1000000);
        userRepository.save(user);

         category = new Category();
        category.setName("test");
        category.setUser(user);
        category.setType(CategoryType.INCOME.name());
        categoryRepository.save(category);
}

@Test 
void testCreateTransactionBadRequest() throws Exception {

    CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal("0000000"));
        request.setDescription("Salary");
        request.setDate(LocalDate.of(2026, 8, 31));
        request.setDescription(null);
        request.setTransactonName(null);

    mockMvc.perform(
        post("/api/transaction")
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(request))
           .header("X-API-TOKEN","test")
    ).andExpectAll(status().isBadRequest()).andDo(result -> {
         WebResponse<String> response = objectMapper.readValue(
                    result.getResponse().getContentAsString(),
                    new TypeReference<WebResponse<String>>() {}
            );
        assertNotNull(response.getErrors());
    });
}


@Test
void testCreateTransactionSuccess() throws Exception {
    CreateTransactionRequest request = new CreateTransactionRequest();
    request.setTransactonName("test");
    request.setCategoryId(category.getId());
    request.setAmount(new BigDecimal("5000000"));
    request.setDescription("this transaction for testing");
    request.setDate(LocalDate.of(2026, 9, 20));
    mockMvc.perform(
            post("/api/transaction")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .header("X-API-TOKEN", "test")
    )
    .andExpectAll(
            status().isOk()
    )
   .andDo(result -> {

    WebResponse<CreateTransactionResponse> response =
            objectMapper.readValue(
                    result.getResponse().getContentAsString(),
                    new TypeReference<WebResponse<CreateTransactionResponse>>() {}
            );

    assertNotNull(response);
    assertNotNull(response.getData());

    assertEquals(
            request.getTransactonName(),
            response.getData().getTransactionName()
    );

    assertEquals(
            request.getAmount(),
            response.getData().getAmount()
    );

    assertEquals(
            request.getDescription(),
            response.getData().getDescription()
    );

    assertEquals(
            request.getDate(),
            response.getData().getDate()
    );

    assertEquals(
            request.getCategoryId(),
            response.getData().getCategoryId()
    );

    assertEquals(
            "Transaction created Successfully",
            response.getMessage()
    );

    assertEquals(
            null,
            response.getErrors()
    );
});
}


}


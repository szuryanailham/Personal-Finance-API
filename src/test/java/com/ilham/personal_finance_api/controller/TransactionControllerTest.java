package com.ilham.personal_finance_api.controller;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import com.ilham.personal_finance_api.entity.Category;
import com.ilham.personal_finance_api.entity.Transaction;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.dto.CreateTransactionRequest;
import com.ilham.personal_finance_api.dto.CreateTransactionResponse;
import com.ilham.personal_finance_api.dto.TransactionResponse;
import com.ilham.personal_finance_api.dto.UpdateTransactionRequest;
import com.ilham.personal_finance_api.dto.WebResponse;
import com.ilham.personal_finance_api.dto.TransactionType;
import com.ilham.personal_finance_api.repository.CategoryRepository;
import com.ilham.personal_finance_api.AbstractIntegrationTest;
import com.ilham.personal_finance_api.repository.TransactionRepository;
import com.ilham.personal_finance_api.repository.UserRepository;
import com.ilham.personal_finance_api.security.BCrypt;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

 @SpringBootTest
   @AutoConfigureMockMvc

public class TransactionControllerTest extends AbstractIntegrationTest {

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

private Transaction transaction;

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
        category.setType(TransactionType.INCOME.name());
        categoryRepository.save(category);

      

        transaction = new Transaction();
        transaction.setTransactionName("test");
        transaction.setTransactionCode("TRX-20260920-0001");
        transaction.setCategory(category);
        transaction.setUser(user);
        transaction.setAmount(new BigDecimal("5000000"));
        transaction.setDescription("this is for test");
        transaction.setTransactionDate(LocalDate.of(2026, 8, 31).atStartOfDay());
        transactionRepository.save(transaction);

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
           .header("Authorization", "Bearer test")
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
                    .header("Authorization", "Bearer test")
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

@Test
  void testDeleteTransactionNotFound() throws Exception {
        mockMvc.perform(
            delete("/api/transaction/invalid-token")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer test")
        ).andExpect(status().isNotFound())
        .andDo(result -> {
            WebResponse<String> response =
                    objectMapper.readValue(
                            result.getResponse().getContentAsString(),
                            new TypeReference<WebResponse<String>>() {}
                    );

            assertNotNull(response.getErrors());
        });
}

@Test 
void testDeleteCategorySuccess() throws Exception {
         mockMvc.perform(
            delete("/api/transaction/" + transaction.getTransactionCode())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer test")
        ).andDo(
        result -> {
            WebResponse<String> response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() {}
            );
            assertNull(response.getErrors());
            assertNull(response.getData());
            assertEquals(
                "Transaction deleted successfully",
                response.getMessage()
            );
        }
    );
}

@Test 
void testGetSingleTransactionNotFound() throws Exception {
      mockMvc.perform(
            get("/api/transaction/TRX-12345")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer test")
        ).andExpectAll(status().isNotFound())
        .andDo(result-> {
            WebResponse<String> response = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {});
            assertNotNull(response.getErrors());
        });
}

@Test
void testGetSingleTransactionSuccess() throws Exception {
    mockMvc.perform(
        get("/api/transaction/" + transaction.getTransactionCode())
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpectAll(
        status().isOk()
    )
    .andDo(result -> {
        WebResponse<TransactionResponse> response =
            objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() {}
            );
        assertNull(response.getErrors());
        assertNotNull(response.getData());
        assertEquals(
            transaction.getTransactionCode(),
            response.getData().getTransactionCode()
        );
    });
}

@Test
void testGetTransactionPaginationBadRequest() throws Exception {
    mockMvc.perform(
        get("/api/transaction")
            .param("skip", "-1")
            .param("limit", "10")
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isBadRequest())
    .andDo(result -> {
        WebResponse<String> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<String>>() {}
        );

        assertNotNull(response.getErrors());
    });
}

@Test 
void testGetTransactionPaginationSuccess() throws Exception {
    for (int i = 1; i < 10; i++) {
          transaction = new Transaction();
        transaction.setTransactionName("test"  + "-" + i);
        transaction.setTransactionCode("TRX-" + UUID.randomUUID());
        transaction.setCategory(category);
        transaction.setUser(user);
        transaction.setAmount(new BigDecimal("5000000"));
        transaction.setDescription("this is for test");
        transaction.setTransactionDate(LocalDate.of(2026, 8, 31).atStartOfDay());
        transactionRepository.save(transaction);
    }


     mockMvc.perform(
        get("/api/transaction")
            .param("skip", "1")
            .param("limit", "10")
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    ) .andExpect(status().isOk())
     .andDo(result -> {
        WebResponse<List<TransactionResponse>> response = objectMapper.readValue( result.getResponse().getContentAsString(), new TypeReference<WebResponse<List<TransactionResponse>>>() {} );
         assertNull(response.getErrors());
          assertNotNull(response.getData()); assertNotNull(response.getPaging()); 
          assertEquals(10, response.getData().size()); assertEquals(1, response.getPaging().getCurrentPage()); 
          assertEquals(1, response.getPaging().getTotalPage()); 
          assertEquals(10, response.getPaging().getSize());
    });

}

@Test
void testTransactionBySearchSuccess() throws Exception {
    for (int i = 1; i <= 15; i++) {
        transaction = new Transaction();
        transaction.setTransactionName("Transaction test");
        transaction.setTransactionCode("TRX-" + UUID.randomUUID());
        transaction.setCategory(category);
        transaction.setUser(user);
        transaction.setAmount(new BigDecimal("5000000"));
        transaction.setDescription("this is for test");
        transaction.setTransactionDate(
            LocalDate.of(2026, 8, 31).atStartOfDay()
        );

        transactionRepository.save(transaction);
    }

    mockMvc.perform(
        get("/api/transaction")
            .param("skip", "0")
            .param("limit", "10")
            .param("search", "Transaction test")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
    .andExpect(status().isOk())
    .andDo(result -> {
        WebResponse<List<TransactionResponse>> response =
            objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<WebResponse<List<TransactionResponse>>>() {}
            );

        assertNull(response.getErrors());
        assertNotNull(response.getData());
        assertNotNull(response.getPaging());

        assertEquals(10, response.getData().size());
        assertEquals(1, response.getPaging().getCurrentPage());
        assertEquals(2, response.getPaging().getTotalPage());

        assertEquals(
            "Transaction test",
            response.getData().get(0).getTransactionName()
        );
    });
}

@Test 
void testTransactionBySearchDate() throws Exception {
      for (int i = 1; i <= 15; i++) {
        transaction = new Transaction();
        transaction.setTransactionName("Transaction test");
        transaction.setTransactionCode("TRX-" + UUID.randomUUID());
        transaction.setCategory(category);
        transaction.setUser(user);
        transaction.setAmount(new BigDecimal("5000000"));
        transaction.setDescription("this is for test");
        transaction.setTransactionDate(
            LocalDate.of(2026, 8, 31).atStartOfDay()
        );
        transactionRepository.save(transaction);
    }

      mockMvc.perform(
        get("/api/transaction")
            .param("skip", "0")
            .param("limit", "10")
            .param("date", "2026-08-31")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
      .andExpect(status().isOk())
    .andDo(result -> {
        WebResponse<List<TransactionResponse>> response =
            objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<WebResponse<List<TransactionResponse>>>() {}
            );
        assertNull(response.getErrors());
        assertNotNull(response.getData());
        assertNotNull(response.getPaging());

        assertEquals(10, response.getData().size());
        assertEquals(1, response.getPaging().getCurrentPage());
        assertEquals(2, response.getPaging().getTotalPage());

       assertEquals(
    "2026-08-31",
    response.getData().get(0).getDate()
);
    });
}


@Test 
void testTransactionByTypeTransaction() throws Exception {
      for (int i = 1; i <= 15; i++) {
        transaction = new Transaction();
        transaction.setTransactionName("Transaction test");
        transaction.setTransactionCode("TRX-" + UUID.randomUUID());
        transaction.setCategory(category);
        transaction.setUser(user);
        transaction.setAmount(new BigDecimal("5000000"));
        transaction.setDescription("this is for test");
        transaction.setTransactionDate(
            LocalDate.of(2026, 8, 31).atStartOfDay()
        );
        transactionRepository.save(transaction);
    }

      mockMvc.perform(
        get("/api/transaction")
            .param("skip", "0")
            .param("limit", "10")
            .param("type", "INCOME")
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test")
    )
      .andExpect(status().isOk())
    .andDo(result -> {
        WebResponse<List<TransactionResponse>> response =
            objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<WebResponse<List<TransactionResponse>>>() {}
            );
        assertNull(response.getErrors());
        assertNotNull(response.getData());
        assertNotNull(response.getPaging());

        assertEquals(10, response.getData().size());
        assertEquals(1, response.getPaging().getCurrentPage());
        assertEquals(2, response.getPaging().getTotalPage());

        for (TransactionResponse transaction : response.getData()) {
    assertEquals("INCOME", transaction.getCategory().getType());
}
    });
}





@Test
void tesUpdateTransactionBadRequest() throws Exception {
    UpdateTransactionRequest request = new UpdateTransactionRequest();
    request.setTransactonName("a".repeat(101));

    mockMvc.perform(
      patch("/api/transaction/" + transaction.getTransactionCode())
      .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .header("Authorization", "Bearer test")
    ) .andExpectAll(
            status().isBadRequest()
        ).andDo(result -> {

            WebResponse<String> response = objectMapper.readValue(result.getResponse().getContentAsString(),
             new TypeReference<WebResponse<String>>() {
            });
            assertNotNull(response.getErrors());
        });
}

@Test
void testUpdateTransactionNotFound() throws Exception {
    UpdateTransactionRequest request = new UpdateTransactionRequest();
    request.setTransactonName("test After Update 1");

    mockMvc.perform(
      patch("/api/transaction/12345")
      .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .header("Authorization", "Bearer test")
    ) .andExpectAll(
            status().isNotFound()
        ).andDo(result -> {

            WebResponse<String> response = objectMapper.readValue(result.getResponse().getContentAsString(),
             new TypeReference<WebResponse<String>>() {
            });
            assertNotNull(response.getErrors());
        });
}

@Test
void testUpdateTransactionPartialSuccess() throws Exception {
    UpdateTransactionRequest request = new UpdateTransactionRequest();
    request.setTransactonName("Belanja Bulanan");
    request.setAmount(new BigDecimal("20000"));

    mockMvc.perform(
      patch("/api/transaction/" + transaction.getTransactionCode())
      .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .header("Authorization", "Bearer test")
    ) .andExpectAll(
            status().isOk()
        ).andDo(result -> {
            WebResponse<TransactionResponse> response = objectMapper.readValue(result.getResponse().getContentAsString(),
             new TypeReference<WebResponse<TransactionResponse>>() {});

            assertNull(response.getErrors());
            assertNotNull(response.getData());
            assertEquals(request.getTransactonName(), response.getData().getTransactionName());
            assertEquals(0, request.getAmount().compareTo(response.getData().getAmount()));
            // fields not sent in the request must remain unchanged
            assertEquals(transaction.getDescription(), response.getData().getDescription());
            assertEquals(transaction.getCategory().getId(), response.getData().getCategory().getId());
            assertEquals(transaction.getTransactionDate().toLocalDate().toString(), response.getData().getDate());
        });
}


@Test 
void tesUpdateTransactionSuccess() throws Exception {
 Category secondCategory = new Category();
    secondCategory.setName("test");
    secondCategory.setUser(user);
    secondCategory.setType(TransactionType.INCOME.name());
    secondCategory = categoryRepository.save(secondCategory);

     UpdateTransactionRequest request = new UpdateTransactionRequest();
    request.setTransactonName("test After Update 1");
    request.setDescription("Description after update");
    request.setCategoryId(secondCategory.getId());
    request.setAmount(new BigDecimal("5000000"));
    request.setDate(LocalDate.of(2026, 8, 31));

    mockMvc.perform(
      patch("/api/transaction/" + transaction.getTransactionCode())
      .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .header("Authorization", "Bearer test")
    ) .andExpectAll(
            status().isOk()
        ).andDo(result -> {
            WebResponse<TransactionResponse> response = objectMapper.readValue(result.getResponse().getContentAsString(),
             new TypeReference<WebResponse<TransactionResponse>>() {});

            assertNull(response.getErrors());
            assertNotNull(response.getData());
            assertEquals(request.getTransactonName(), response.getData().getTransactionName());
            assertEquals(request.getDescription(), response.getData().getDescription());
            assertEquals(request.getCategoryId(), response.getData().getCategory().getId());
            assertEquals(request.getDate().toString(), response.getData().getDate());
        });
}




}


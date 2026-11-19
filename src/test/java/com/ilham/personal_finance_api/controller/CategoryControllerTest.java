package com.ilham.personal_finance_api.controller;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.ilham.personal_finance_api.entity.Category;
import com.ilham.personal_finance_api.entity.Transaction;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.dto.CategoryResponse;
import com.ilham.personal_finance_api.dto.CreateCategoryRequest;
import com.ilham.personal_finance_api.dto.WebResponse;
import com.ilham.personal_finance_api.dto.TransactionType;
import com.ilham.personal_finance_api.dto.CreateCategoryResponse;
import com.ilham.personal_finance_api.dto.UpdateCategoryRequest;
import com.ilham.personal_finance_api.AbstractIntegrationTest;
import com.ilham.personal_finance_api.repository.CategoryRepository;
import com.ilham.personal_finance_api.repository.TransactionRepository;
import com.ilham.personal_finance_api.repository.UserRepository;
import com.ilham.personal_finance_api.security.BCrypt;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
public class CategoryControllerTest extends AbstractIntegrationTest {

    @Autowired 
    private CategoryRepository categoryRepository;

    @Autowired 
    private TransactionRepository transactionRepository;
    @Autowired 
    private ObjectMapper objectMapper;

    @Autowired 
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private User user;

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
    }
    
@Test 
void testCreateCategoryDuplicate()  throws Exception {
    Category category = new Category();
    category.setName("test");
    category.setUser(user);
    category.setType(TransactionType.INCOME.name());

    categoryRepository.save(category);

    CreateCategoryRequest request = new CreateCategoryRequest();

    request.setName("test");
    request.setType(TransactionType.INCOME);


     mockMvc.perform(
        post("/api/categories")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .header("Authorization", "Bearer test")

    ).andExpectAll(status().isConflict()).andDo(result -> {
         WebResponse<String> response = objectMapper.readValue(
                    result.getResponse().getContentAsString(),
                    new TypeReference<WebResponse<String>>() {}
            );
        assertNotNull(response.getErrors());
    });

}

@Test 
void testCreateCategoryBadRequst() throws Exception {
      CreateCategoryRequest request = new CreateCategoryRequest();
    request.setName("");
    request.setType(TransactionType.INCOME);

    mockMvc.perform(
        post("/api/categories")
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
void testCreateCategorySuccess() throws Exception {
    CreateCategoryRequest request = new CreateCategoryRequest();
    request.setName("test");
    request.setType(TransactionType.INCOME);

     mockMvc.perform(
        post("/api/categories")
       .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .header("Authorization", "Bearer test")
    ).andExpectAll(status().isOk()).andDo(result -> {
         WebResponse<CreateCategoryResponse> response = objectMapper.readValue(
    result.getResponse().getContentAsString(),
    new TypeReference<WebResponse<CreateCategoryResponse>>() {}
);
   assertNotNull(response);
    assertNotNull(response.getData());
    assertEquals("test", response.getData().getName());
    assertEquals("INCOME", response.getData().getType());
    assertEquals("Category created successfully", response.getMessage());
    assertNull(response.getErrors());
    });

}


@Test
void testCreateExpenseCategoryPersistsBudgetAndAppearance() throws Exception {
    String payload = """
        {"name":"Makan dan Minum","type":"EXPENSE","budget":1000000000000,"icon":"utensils","color":"#F97316"}
        """;

    mockMvc.perform(post("/api/categories")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload)
            .header("Authorization", "Bearer test"))
        .andExpect(status().isOk())
        .andDo(result -> {
            WebResponse<CreateCategoryResponse> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), new TypeReference<>() {});
            assertEquals(1_000_000_000_000L, response.getData().getBudget());
            assertEquals("utensils", response.getData().getIcon());
            assertEquals("#F97316", response.getData().getColor());
        });

    mockMvc.perform(get("/api/categories").param("skip", "0").param("limit", "10")
            .header("Authorization", "Bearer test"))
        .andExpect(status().isOk())
        .andDo(result -> {
            WebResponse<List<CategoryResponse>> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), new TypeReference<>() {});
            CategoryResponse category = response.getData().getFirst();
            assertEquals(1_000_000_000_000L, category.getBudget());
            assertEquals(BigDecimal.ZERO, category.getTotalExpense());
            assertEquals(BigDecimal.valueOf(1_000_000_000_000L), category.getRemainingBudget());
            assertEquals("utensils", category.getIcon());
            assertEquals("#F97316", category.getColor());
        });
}

@Test
void testCreateCategoryRejectsInvalidMetadata() throws Exception {
    for (String payload : java.util.List.of(
            "{\"name\":\"test\",\"type\":\"EXPENSE\"}",
            "{\"name\":\"test\",\"type\":\"EXPENSE\",\"budget\":1.5}",
            "{\"name\":\"test\",\"type\":\"EXPENSE\",\"budget\":-1}",
            "{\"name\":\"test\",\"type\":\"EXPENSE\",\"budget\":1000000000001}",
            "{\"name\":\"test\",\"type\":\"INCOME\",\"budget\":0}",
            "{\"name\":\"test\",\"type\":\"SAVING\",\"icon\":\"unknown\"}",
            "{\"name\":\"test\",\"type\":\"INCOME\",\"color\":\"#abc\"}")) {
        mockMvc.perform(post("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .header("Authorization", "Bearer test"))
            .andExpect(status().isBadRequest());
    }
}

@Test
void testUpdateCategoryPersistsMetadataForDetailAndList() throws Exception {
    Category category = new Category();
    category.setName("Food");
    category.setType("EXPENSE");
    category.setUser(user);
    categoryRepository.save(category);
    String detailPath = "/api/categories/" + category.getId();

    mockMvc.perform(put(detailPath)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"Makan dan Minum","type":"EXPENSE","budget":1500000,"icon":"utensils","color":"#F97316"}
                """)
            .header("Authorization", "Bearer test"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.budget").value(1500000))
        .andExpect(jsonPath("$.data.icon").value("utensils"))
        .andExpect(jsonPath("$.data.color").value("#F97316"));

    for (boolean deleted : List.of(false, true)) {
        Transaction transaction = new Transaction();
        transaction.setTransactionCode("budget-test-" + deleted);
        transaction.setTransactionName("Belanja");
        transaction.setUser(user);
        transaction.setCategory(category);
        transaction.setTransactionDate(LocalDateTime.of(2025, 1, 1, 10, 0));
        transaction.setAmount(BigDecimal.valueOf(1_600_000));
        transaction.setDeleted(deleted);
        transactionRepository.save(transaction);
    }

    mockMvc.perform(get(detailPath).header("Authorization", "Bearer test"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.budget").value(1500000))
        .andExpect(jsonPath("$.data.totalExpense").value(1600000.0))
        .andExpect(jsonPath("$.data.remainingBudget").value(-100000.0));

    mockMvc.perform(get("/api/categories").param("skip", "0").param("limit", "10")
            .header("Authorization", "Bearer test"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].budget").value(1500000))
        .andExpect(jsonPath("$.data[0].totalExpense").value(1600000.0))
        .andExpect(jsonPath("$.data[0].remainingBudget").value(-100000.0))
        .andExpect(jsonPath("$.data[0].icon").value("utensils"))
        .andExpect(jsonPath("$.data[0].color").value("#F97316"));
}

@Test
void testGetSingleCategoryNotFound() throws Exception {
      mockMvc.perform(
            get("/api/categories/" + UUID.randomUUID())
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
void testGetSingleCategorySuccess()  throws Exception {
    Category category = new Category();
    category.setName("belanja");
    category.setUser(user);
    category.setType(TransactionType.EXPENSE.name());
    categoryRepository.save(category);
      mockMvc.perform(
            get("/api/categories/" + category.getId())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer test")
        ).andExpectAll(status().isOk())
        .andDo(result-> {
            WebResponse<CategoryResponse> response = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {});
            assertNull(response.getErrors());
            assertEquals(category.getName(), response.getData().getName());
            assertEquals(category.getType(), response.getData().getType());
        });
}


@Test
void testGetCategoryPaginationBadRequest() throws Exception {

    int skip = 0;

    mockMvc.perform(
            get("/api/categories/" + skip)
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer test")
        )
        .andExpect(status().isBadRequest())
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
void testGetPaginationSuccessRequest() throws Exception {

    int skip = 0;
    int limit = 10;

    for (int i = 0; i < 10; i++) {
        Category category = new Category();

        category.setName("test ke-" + i);
        category.setUser(user);

        if (i < 5) {
            category.setType(TransactionType.EXPENSE.name());
        } else {
            category.setType(TransactionType.INCOME.name());
        }

        categoryRepository.save(category);
    }

    mockMvc.perform(
            get("/api/categories")
                    .param("skip", String.valueOf(skip))
                    .param("limit", String.valueOf(limit))
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer test")
        )
        .andExpect(status().isOk())
        .andDo(result -> {

            WebResponse<List<CategoryResponse>> response =
                    objectMapper.readValue(
                            result.getResponse().getContentAsString(),
                            new TypeReference<WebResponse<List<CategoryResponse>>>() {}
                    );

            assertNull(response.getErrors());
            assertNotNull(response.getData());
            assertEquals(10, response.getData().size());
            assertEquals(1, response.getPaging().getCurrentPage());
            assertEquals(1, response.getPaging().getTotalPage());
            assertEquals(10, response.getPaging().getSize());
        });
}

@Test 
void testDeleteCategoryNotFound() throws Exception {

    mockMvc.perform(
            delete("/api/categories/invalid-uuid")
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer test")
        )
        .andExpect(status().isBadRequest())
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
        Category category = new Category();
        category.setName("test");
        category.setUser(user);
        category.setType(TransactionType.EXPENSE.name());
        categoryRepository.save(category);

          mockMvc.perform(
            delete("/api/categories/" + category.getId())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer test")
        ).andExpectAll(status().isOk()).andDo(
            result -> {
                WebResponse<String> response = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {});

                assertNull(response.getErrors());
                assertNull(response.getData());
                assertEquals("Category deleted successfully", response.getMessage());
            }
        );
}

@Test 
void testUpdateCategoryBadRequest() throws Exception {
    Category category = new Category();
     category.setName("test");
     category.setUser(user);
     category.setType(TransactionType.EXPENSE.name());
    categoryRepository.save(category);

     UpdateCategoryRequest request = new UpdateCategoryRequest();
    request.setName("");
      category.setType(TransactionType.INCOME.name());


    mockMvc.perform(
      put("/api/categories/12345")
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
void testUpdateCategorySuccess() throws Exception {
    Category category = new Category();
    category.setName("test");
    category.setUser(user);
    category.setType(TransactionType.EXPENSE.name());
    categoryRepository.save(category);
    UpdateCategoryRequest request = new UpdateCategoryRequest();
    request.setName("Ilham");
    request.setType(TransactionType.INCOME);

    mockMvc.perform(
            put("/api/categories/" + category.getId())
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", "Bearer test")
        )
        .andExpectAll(
            status().isOk()
        )
        .andDo(result -> {

            WebResponse<CategoryResponse> response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<WebResponse<CategoryResponse>>() {}
            );

            assertNotNull(response);
            assertNotNull(response.getData());
            assertEquals("Ilham", response.getData().getName());
            assertEquals("INCOME", response.getData().getType());
            assertNull(response.getErrors());
        });

}

private Category saveCategory(User owner, String name, TransactionType type, boolean isDeleted) {
    Category category = new Category();
    category.setName(name);
    category.setUser(owner);
    category.setType(type.name());
    category.setDeleted(isDeleted);
    return categoryRepository.save(category);
}

private User saveOtherUser() {
    User other = new User();
    other.setFirstName("other");
    other.setLastName("other");
    other.setEmail("other@gmail.com");
    other.setPassword(BCrypt.hashpw("other", BCrypt.gensalt()));
    other.setToken("other");
    other.setTokenExpiredAt(System.currentTimeMillis() + 1000000);
    return userRepository.save(other);
}

private String toJson(String name, TransactionType type) throws Exception {
    CreateCategoryRequest request = new CreateCategoryRequest();
    request.setName(name);
    request.setType(type);
    return objectMapper.writeValueAsString(request);
}

@Test
void testCreateCategoryAllowsSameNameOwnedByOtherUser() throws Exception {
    saveCategory(saveOtherUser(), "test", TransactionType.INCOME, false);

    mockMvc.perform(
        post("/api/categories")
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson("test", TransactionType.INCOME))
            .header("Authorization", "Bearer test")
    ).andExpectAll(status().isOk());
}

@Test
void testCreateCategoryAllowsSameNameAsDeletedCategory() throws Exception {
    saveCategory(user, "test", TransactionType.INCOME, true);

    mockMvc.perform(
        post("/api/categories")
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson("test", TransactionType.INCOME))
            .header("Authorization", "Bearer test")
    ).andExpectAll(status().isOk());
}

@Test
void testCreateCategoryDuplicateIgnoresCaseAndWhitespace() throws Exception {
    saveCategory(user, "test", TransactionType.INCOME, false);

    mockMvc.perform(
        post("/api/categories")
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson("  TEST  ", TransactionType.INCOME))
            .header("Authorization", "Bearer test")
    ).andExpectAll(status().isConflict());
}

@Test
void testCreateCategoryTrimsName() throws Exception {
    mockMvc.perform(
        post("/api/categories")
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson("  test  ", TransactionType.INCOME))
            .header("Authorization", "Bearer test")
    ).andExpectAll(status().isOk()).andDo(result -> {
        WebResponse<CreateCategoryResponse> response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<WebResponse<CreateCategoryResponse>>() {}
        );
        assertEquals("test", response.getData().getName());
    });
}

@Test
void testUpdateCategoryDuplicate() throws Exception {
    saveCategory(user, "Makan", TransactionType.EXPENSE, false);
    Category target = saveCategory(user, "Transport", TransactionType.EXPENSE, false);

    UpdateCategoryRequest request = new UpdateCategoryRequest();
    request.setName("makan");
    request.setType(TransactionType.EXPENSE);

    mockMvc.perform(
        put("/api/categories/" + target.getId())
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request))
            .header("Authorization", "Bearer test")
    ).andExpectAll(status().isConflict());
}

@Test
void testUpdateCategoryKeepingOwnName() throws Exception {
    Category target = saveCategory(user, "Makan", TransactionType.EXPENSE, false);

    UpdateCategoryRequest request = new UpdateCategoryRequest();
    request.setName("Makan");
    request.setType(TransactionType.EXPENSE);

    mockMvc.perform(
        put("/api/categories/" + target.getId())
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request))
            .header("Authorization", "Bearer test")
    ).andExpectAll(status().isOk());
}

}

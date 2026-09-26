package com.ilham.personal_finance_api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.ilham.personal_finance_api.AbstractIntegrationTest;
import com.ilham.personal_finance_api.dto.TransactionResponse;
import com.ilham.personal_finance_api.dto.TransactionType;
import com.ilham.personal_finance_api.dto.WebResponse;
import com.ilham.personal_finance_api.entity.Category;
import com.ilham.personal_finance_api.entity.Transaction;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.repository.CategoryRepository;
import com.ilham.personal_finance_api.repository.TransactionRepository;
import com.ilham.personal_finance_api.repository.UserRepository;
import com.ilham.personal_finance_api.security.BCrypt;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
public class TransactionSortControllerTest extends AbstractIntegrationTest {

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

    private User user;

    /*
     * Fixture:
     *   code      name   category  amount  date
     *   TRX-0001  Gamma  Food      300     2026-08-20
     *   TRX-0002  Alpha  Salary    100     2026-08-05
     *   TRX-0003  Beta   Bills     200     2026-08-10
     */
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
        user.setTokenExpiredAt(System.currentTimeMillis() + 1000000);
        userRepository.save(user);

        Category food = saveCategory("Food", TransactionType.EXPENSE);
        Category salary = saveCategory("Salary", TransactionType.INCOME);
        Category bills = saveCategory("Bills", TransactionType.EXPENSE);

        saveTransaction("TRX-0001", "Gamma", food, "300", LocalDate.of(2026, 8, 20));
        saveTransaction("TRX-0002", "Alpha", salary, "100", LocalDate.of(2026, 8, 5));
        saveTransaction("TRX-0003", "Beta", bills, "200", LocalDate.of(2026, 8, 10));
    }

    @ParameterizedTest(name = "sort={0} -> {1}")
    @CsvSource({
        "transactionCode,  TRX-0001|TRX-0002|TRX-0003",
        "-transactionCode, TRX-0003|TRX-0002|TRX-0001",
        "name,             TRX-0002|TRX-0003|TRX-0001",
        "-name,            TRX-0001|TRX-0003|TRX-0002",
        "date,             TRX-0001|TRX-0003|TRX-0002",
        "-date,            TRX-0002|TRX-0003|TRX-0001",
        "category,         TRX-0003|TRX-0001|TRX-0002",
        "-category,        TRX-0002|TRX-0001|TRX-0003",
        "amount,           TRX-0001|TRX-0003|TRX-0002",
        "-amount,          TRX-0002|TRX-0003|TRX-0001"
    })
    void testSortTransactionSuccess(String sort, String expectedCodes) throws Exception {
        mockMvc.perform(listRequest("0", "10").param("sort", sort))
            .andExpect(status().isOk())
            .andDo(result -> assertCodes(
                Arrays.asList(expectedCodes.split("\\|")),
                readList(result.getResponse().getContentAsString())
            ));
    }

    @Test
    void testSortTransactionDefaultIsNewest() throws Exception {
        mockMvc.perform(listRequest("0", "10"))
            .andExpect(status().isOk())
            .andDo(result -> assertCodes(
                List.of("TRX-0001", "TRX-0003", "TRX-0002"),
                readList(result.getResponse().getContentAsString())
            ));
    }

    @Test
    void testSortTransactionWithPagination() throws Exception {
        mockMvc.perform(listRequest("2", "2").param("sort", "amount"))
            .andExpect(status().isOk())
            .andDo(result -> {
                WebResponse<List<TransactionResponse>> response =
                    readList(result.getResponse().getContentAsString());
                assertCodes(List.of("TRX-0002"), response);
                assertEquals(2, response.getPaging().getCurrentPage());
                assertEquals(2, response.getPaging().getTotalPage());
            });
    }

    @Test
    void testSortTransactionWithTypeFilter() throws Exception {
        mockMvc.perform(listRequest("0", "10").param("sort", "-amount").param("type", "EXPENSE"))
            .andExpect(status().isOk())
            .andDo(result -> assertCodes(
                List.of("TRX-0003", "TRX-0001"),
                readList(result.getResponse().getContentAsString())
            ));
    }

    @Test
    void testSortTransactionInvalidValue() throws Exception {
        mockMvc.perform(listRequest("0", "10").param("sort", "foo"))
            .andExpect(status().isBadRequest())
            .andDo(result -> {
                WebResponse<String> response = objectMapper.readValue(
                    result.getResponse().getContentAsString(),
                    new TypeReference<WebResponse<String>>() {}
                );
                assertNotNull(response.getErrors());
            });
    }

    private MockHttpServletRequestBuilder listRequest(String skip, String limit) {
        return get("/api/transaction")
            .param("skip", skip)
            .param("limit", limit)
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer test");
    }

    private WebResponse<List<TransactionResponse>> readList(String body) {
        return objectMapper.readValue(body, new TypeReference<WebResponse<List<TransactionResponse>>>() {});
    }

    private void assertCodes(List<String> expected, WebResponse<List<TransactionResponse>> response) {
        assertNull(response.getErrors());
        assertNotNull(response.getData());
        List<String> actual = response.getData().stream()
            .map(TransactionResponse::getTransactionCode)
            .toList();
        assertEquals(expected, actual);
    }

    private Category saveCategory(String name, TransactionType type) {
        Category category = new Category();
        category.setName(name);
        category.setType(type.name());
        category.setUser(user);
        return categoryRepository.save(category);
    }

    private void saveTransaction(String code, String name, Category category, String amount, LocalDate date) {
        Transaction transaction = new Transaction();
        transaction.setTransactionCode(code);
        transaction.setTransactionName(name);
        transaction.setCategory(category);
        transaction.setUser(user);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setDescription("sort test");
        transaction.setTransactionDate(date.atStartOfDay());
        transactionRepository.save(transaction);
    }
}

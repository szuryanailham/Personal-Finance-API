package com.ilham.personal_finance_api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.server.ResponseStatusException;

import com.ilham.personal_finance_api.AbstractIntegrationTest;
import com.ilham.personal_finance_api.dto.AccountResponse;
import com.ilham.personal_finance_api.dto.AccountType;
import com.ilham.personal_finance_api.dto.CreateTransferRequest;
import com.ilham.personal_finance_api.dto.FinancialSetupRequest;
import com.ilham.personal_finance_api.dto.FinancialSetupResponse;
import com.ilham.personal_finance_api.dto.TransactionType;
import com.ilham.personal_finance_api.entity.Category;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.repository.CategoryRepository;
import com.ilham.personal_finance_api.repository.TransactionRepository;
import com.ilham.personal_finance_api.repository.TransferRepository;
import com.ilham.personal_finance_api.repository.UserRepository;
import com.ilham.personal_finance_api.security.BCrypt;
import com.ilham.personal_finance_api.services.FinancialSetupService;
import com.ilham.personal_finance_api.services.TransferService;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
public class TransferControllerTest extends AbstractIntegrationTest {

    private static final String TOKEN = "test";
    private static final String OTHER_TOKEN = "other";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private FinancialSetupService financialSetupService;

    @Autowired
    private TransferService transferService;

    private User user;
    private UUID mainId;
    private UUID savingsId;
    private UUID otherMainId;

    @BeforeEach
    void setUp() {
        transferRepository.deleteAll();
        transactionRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        user = createUser("test@gmail.com", TOKEN);
        FinancialSetupResponse setup = completeSetup(user, "1000000", "3000000");
        mainId = accountOf(setup, AccountType.MAIN).getId();
        savingsId = accountOf(setup, AccountType.SAVINGS).getId();

        User other = createUser("other@gmail.com", OTHER_TOKEN);
        otherMainId = accountOf(completeSetup(other, "5000000", "0"), AccountType.MAIN).getId();
    }

    private User createUser(String email, String token) {
        User newUser = new User();
        newUser.setFirstName("test");
        newUser.setLastName("test");
        newUser.setEmail(email);
        newUser.setPassword(BCrypt.hashpw("test12345", BCrypt.gensalt()));
        newUser.setToken(token);
        newUser.setTokenExpiredAt(System.currentTimeMillis() + 1000000);
        return userRepository.save(newUser);
    }

    private FinancialSetupResponse completeSetup(User owner, String main, String savings) {
        FinancialSetupRequest request = new FinancialSetupRequest();
        request.setBalance(new BigDecimal(main));
        request.setSavings(new BigDecimal(savings));
        return financialSetupService.complete(owner, request);
    }

    // ===================== helpers =====================

    private AccountResponse accountOf(FinancialSetupResponse setup, AccountType type) {
        return setup.getAccounts().stream()
            .filter(account -> account.getAccountType() == type)
            .findFirst()
            .orElseThrow();
    }

    private ResultActions transfer(Object fromAccountId, Object toAccountId, Object amount) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("fromAccountId", fromAccountId);
        body.put("toAccountId", toAccountId);
        body.put("amount", amount);
        body.put("note", "test transfer");
        return mockMvc.perform(post("/api/transfers")
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + TOKEN)
            .content(objectMapper.writeValueAsString(body)));
    }

    private void assertAmount(String expected, BigDecimal actual, String label) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), label + " expected " + expected + " but was " + actual);
    }

    private void assertBalances(String main, String savings, String total) {
        FinancialSetupResponse setup = financialSetupService.get(user);
        assertAmount(main, accountOf(setup, AccountType.MAIN).getCurrentBalance(), "MAIN");
        assertAmount(savings, accountOf(setup, AccountType.SAVINGS).getCurrentBalance(), "SAVINGS");
        assertAmount(total, setup.getTotalBalance(), "total");
    }

    // ===================== success =====================

    @Test
    void testTransferFromSavingsToMain() throws Exception {
        transfer(savingsId, mainId, 500000)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").isNotEmpty())
            .andExpect(jsonPath("$.data.fromAccountId").value(savingsId.toString()))
            .andExpect(jsonPath("$.data.toAccountId").value(mainId.toString()))
            .andExpect(jsonPath("$.data.amount").value(500000))
            .andExpect(jsonPath("$.data.note").value("test transfer"))
            .andExpect(jsonPath("$.data.createdAt").isNotEmpty());

        assertBalances("1500000", "2500000", "4000000");
    }

    @Test
    void testTransferFromMainToSavings() throws Exception {
        transfer(mainId, savingsId, 400000).andExpect(status().isOk());

        assertBalances("600000", "3400000", "4000000");
    }

    @Test
    void testTransferWholeBalanceIsAllowed() throws Exception {
        transfer(mainId, savingsId, 1000000).andExpect(status().isOk());

        assertBalances("0", "4000000", "4000000");
    }

    @Test
    void testTransferDoesNotChangeIncomeExpenseStatistics() throws Exception {
        Category salary = new Category();
        salary.setName("Gaji");
        salary.setType(TransactionType.INCOME.name());
        salary.setUser(user);
        categoryRepository.save(salary);

        Map<String, Object> body = new HashMap<>();
        body.put("transactonName", "Gaji");
        body.put("amount", 2000000);
        body.put("categoryId", salary.getId());
        body.put("date", LocalDate.now().toString());
        mockMvc.perform(post("/api/transaction")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + TOKEN)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk());

        String statBefore = getContent("/api/transaction/stat");
        String statisticBefore = getContent("/api/transaction/statistic");
        String categoryStatBefore = getContent("/api/categories/statistic");

        transfer(savingsId, mainId, 500000).andExpect(status().isOk());
        transfer(mainId, savingsId, 100000).andExpect(status().isOk());

        assertEquals(statBefore, getContent("/api/transaction/stat"));
        assertEquals(statisticBefore, getContent("/api/transaction/statistic"));
        assertEquals(categoryStatBefore, getContent("/api/categories/statistic"));
        assertEquals(1, transactionRepository.count(), "transfer must not create income/expense transactions");
    }

    private String getContent(String path) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + TOKEN))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    // ===================== validation =====================

    @Test
    void testTransferRejectsZeroAmount() throws Exception {
        transfer(mainId, savingsId, 0).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors").value("Amount must be greater than 0"));
        assertEquals(0, transferRepository.count());
    }

    @Test
    void testTransferRejectsNegativeAmount() throws Exception {
        transfer(mainId, savingsId, -100).andExpect(status().isBadRequest());
        assertEquals(0, transferRepository.count());
    }

    @Test
    void testTransferRejectsMissingFields() throws Exception {
        transfer(mainId, savingsId, null).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors").value("Amount is required"));
        transfer(null, savingsId, 100).andExpect(status().isBadRequest());
        transfer(mainId, null, 100).andExpect(status().isBadRequest());
    }

    @Test
    void testTransferRejectsTooManyDecimals() throws Exception {
        transfer(mainId, savingsId, new BigDecimal("1.001")).andExpect(status().isBadRequest());
        assertEquals(0, transferRepository.count());
    }

    @Test
    void testTransferRejectsSameAccount() throws Exception {
        transfer(mainId, mainId, 100).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors").value("Source and destination accounts must be different"));
        assertEquals(0, transferRepository.count());
    }

    @Test
    void testTransferRejectsInsufficientBalanceWithoutPartialChanges() throws Exception {
        transfer(mainId, savingsId, 1000000.01).andExpect(status().isConflict())
            .andExpect(jsonPath("$.errors").value("Insufficient balance in source account"));

        assertEquals(0, transferRepository.count());
        assertBalances("1000000", "3000000", "4000000");
    }

    @Test
    void testTransferRejectsOtherUsersAccountWithoutLeaking() throws Exception {
        transfer(otherMainId, mainId, 100).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errors").value("Account not found"));
        transfer(mainId, otherMainId, 100).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errors").value("Account not found"));
        // akun yang tidak ada mendapat respons yang sama persis
        transfer(UUID.randomUUID(), mainId, 100).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errors").value("Account not found"));

        assertEquals(0, transferRepository.count());
    }

    @Test
    void testTransferUnauthorized() throws Exception {
        mockMvc.perform(post("/api/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/transfers")).andExpect(status().isUnauthorized());
    }

    // ===================== concurrency =====================

    @Test
    void testConcurrentTransfersCannotOverspend() throws Exception {
        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            CreateTransferRequest request = new CreateTransferRequest();
            request.setFromAccountId(mainId);
            request.setToAccountId(savingsId);
            request.setAmount(new BigDecimal("300000"));
            Callable<Boolean> task = () -> {
                start.await();
                try {
                    transferService.create(user, request);
                    return true;
                } catch (ResponseStatusException e) {
                    assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
                    return false;
                }
            };
            results.add(executor.submit(task));
        }
        start.countDown();

        int succeeded = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                succeeded++;
            }
        }
        executor.shutdown();

        assertEquals(3, succeeded, "only 3 x 300,000 fit in 1,000,000");
        assertEquals(3, transferRepository.count());
        assertBalances("100000", "3900000", "4000000");
    }

    @Test
    void testConcurrentOppositeTransfersDoNotDeadlock() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            boolean fromMain = i % 2 == 0;
            CreateTransferRequest request = new CreateTransferRequest();
            request.setFromAccountId(fromMain ? mainId : savingsId);
            request.setToAccountId(fromMain ? savingsId : mainId);
            request.setAmount(new BigDecimal("100000"));
            results.add(executor.submit(() -> {
                start.await();
                return transferService.create(user, request);
            }));
        }
        start.countDown();

        for (Future<?> result : results) {
            assertNotNull(result.get());
        }
        executor.shutdown();

        assertEquals(4, transferRepository.count());
        assertBalances("1000000", "3000000", "4000000");
    }

    // ===================== list =====================

    @Test
    void testGetTransfersReturnsOnlyOwnNewestFirst() throws Exception {
        transfer(savingsId, mainId, 100).andExpect(status().isOk());
        transfer(mainId, savingsId, 200).andExpect(status().isOk());

        CreateTransferRequest otherRequest = new CreateTransferRequest();
        User other = userRepository.findFirstByToken(OTHER_TOKEN).orElseThrow();
        UUID otherSavingsId = accountOf(financialSetupService.get(other), AccountType.SAVINGS).getId();
        otherRequest.setFromAccountId(otherMainId);
        otherRequest.setToAccountId(otherSavingsId);
        otherRequest.setAmount(new BigDecimal("300"));
        transferService.create(other, otherRequest);

        mockMvc.perform(get("/api/transfers")
                .param("skip", "0")
                .param("limit", "10")
                .header("Authorization", "Bearer " + TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.paging.totalItem").value(2))
            .andExpect(jsonPath("$.data[0].amount").value(200))
            .andExpect(jsonPath("$.data[1].amount").value(100));

        assertEquals(3, transferRepository.count());
    }

    @Test
    void testGetTransfersRejectsInvalidPaging() throws Exception {
        mockMvc.perform(get("/api/transfers")
                .param("limit", "0")
                .header("Authorization", "Bearer " + TOKEN))
            .andExpect(status().isBadRequest());
    }
}

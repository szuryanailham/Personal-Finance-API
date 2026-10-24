package com.ilham.personal_finance_api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.ilham.personal_finance_api.AbstractIntegrationTest;
import com.ilham.personal_finance_api.dto.AccountResponse;
import com.ilham.personal_finance_api.dto.AccountType;
import com.ilham.personal_finance_api.dto.FinancialSetupRequest;
import com.ilham.personal_finance_api.dto.FinancialSetupResponse;
import com.ilham.personal_finance_api.dto.TransactionType;
import com.ilham.personal_finance_api.dto.WebResponse;
import com.ilham.personal_finance_api.entity.Category;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.exception.DataAlreadyExistedException;
import com.ilham.personal_finance_api.repository.AccountRepository;
import com.ilham.personal_finance_api.repository.CategoryRepository;
import com.ilham.personal_finance_api.repository.PendingRegistration;
import com.ilham.personal_finance_api.repository.TransactionRepository;
import com.ilham.personal_finance_api.repository.TransferRepository;
import com.ilham.personal_finance_api.repository.UserRepository;
import com.ilham.personal_finance_api.security.BCrypt;
import com.ilham.personal_finance_api.security.GoogleTokenVerifier;
import com.ilham.personal_finance_api.security.GoogleUserInfo;
import com.ilham.personal_finance_api.services.FinancialSetupService;
import com.ilham.personal_finance_api.services.UserService;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
public class FinancialSetupControllerTest extends AbstractIntegrationTest {

    private static final String TOKEN = "test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private FinancialSetupService financialSetupService;

    @Autowired
    private UserService userService;

    @MockitoBean
    private GoogleTokenVerifier googleTokenVerifier;

    private User user;

    @BeforeEach
    void setUp() {
        transferRepository.deleteAll();
        transactionRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        // dibuat langsung lewat repository: belum punya akun (seperti user lama sebelum migrasi)
        user = createUser("test@gmail.com", TOKEN);
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

    private Category createCategory(String name, TransactionType type) {
        Category category = new Category();
        category.setName(name);
        category.setType(type.name());
        category.setUser(user);
        return categoryRepository.save(category);
    }

    // ===================== helpers =====================

    private MockHttpServletRequestBuilder withAuth(MockHttpServletRequestBuilder request) {
        return request.accept(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + TOKEN);
    }

    private FinancialSetupResponse perform(MockHttpServletRequestBuilder request, ResultMatcher expectedStatus) throws Exception {
        String content = mockMvc.perform(request)
            .andExpect(expectedStatus)
            .andReturn().getResponse().getContentAsString();

        WebResponse<FinancialSetupResponse> response = objectMapper.readValue(
            content,
            new TypeReference<WebResponse<FinancialSetupResponse>>() {}
        );
        return response.getData();
    }

    private FinancialSetupResponse getSetup() throws Exception {
        return perform(withAuth(get("/api/financial-setup")), status().isOk());
    }

    private MockHttpServletRequestBuilder putSetup(String json) {
        return withAuth(put("/api/financial-setup"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json);
    }

    private String errorOf(MockHttpServletRequestBuilder request, ResultMatcher expectedStatus) throws Exception {
        String content = mockMvc.perform(request)
            .andExpect(expectedStatus)
            .andReturn().getResponse().getContentAsString();
        WebResponse<Object> response = objectMapper.readValue(content, new TypeReference<WebResponse<Object>>() {});
        assertNotNull(response.getErrors());
        return response.getErrors();
    }

    private AccountResponse account(FinancialSetupResponse setup, AccountType type) {
        return setup.getAccounts().stream()
            .filter(account -> account.getAccountType() == type)
            .findFirst()
            .orElseThrow();
    }

    private void assertAmount(String expected, BigDecimal actual, String label) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), label + " expected " + expected + " but was " + actual);
    }

    private void assertState(FinancialSetupResponse setup, boolean completed, String main, String savings) {
        assertEquals(completed, setup.isSetupCompleted(), "setupCompleted");
        assertEquals(2, setup.getAccounts().size(), "accounts");
        assertAmount(main, account(setup, AccountType.MAIN).getOpeningBalance(), "MAIN opening");
        assertAmount(savings, account(setup, AccountType.SAVINGS).getOpeningBalance(), "SAVINGS opening");
        assertAmount(main, account(setup, AccountType.MAIN).getCurrentBalance(), "MAIN current");
        assertAmount(savings, account(setup, AccountType.SAVINGS).getCurrentBalance(), "SAVINGS current");
        assertAmount(new BigDecimal(main).add(new BigDecimal(savings)).toPlainString(), setup.getTotalBalance(), "total");
    }

    private long accountCount(User owner) {
        return accountRepository.findAllByUserOrderByAccountTypeAsc(owner).size();
    }

    // ===================== GET =====================

    @Test
    void testGetNewUserIsNotCompletedWithZeroAccounts() throws Exception {
        FinancialSetupResponse setup = getSetup();

        assertState(setup, false, "0", "0");
        assertEquals(AccountType.MAIN, setup.getAccounts().get(0).getAccountType());
        assertEquals("Main Balance", setup.getAccounts().get(0).getName());
        assertEquals("Savings", setup.getAccounts().get(1).getName());
        assertNotNull(setup.getAccounts().get(0).getId());
    }

    @Test
    void testGetRepeatedDoesNotDuplicateAccounts() throws Exception {
        getSetup();
        getSetup();
        assertEquals(2, accountCount(user));
    }

    @Test
    void testUnauthorized() throws Exception {
        mockMvc.perform(get("/api/financial-setup")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/financial-setup").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/financial-setup/skip").header("Authorization", "Bearer wrong"))
            .andExpect(status().isUnauthorized());
    }

    // ===================== PUT =====================

    @Test
    void testCompleteWithBothValues() throws Exception {
        FinancialSetupResponse setup = perform(putSetup("{\"balance\":5000000,\"savings\":2000000}"), status().isOk());

        assertState(setup, true, "5000000", "2000000");
        assertState(getSetup(), true, "5000000", "2000000");
    }

    @Test
    void testCompleteWithOnlyMain() throws Exception {
        assertState(perform(putSetup("{\"balance\":750000.50}"), status().isOk()), true, "750000.50", "0");
    }

    @Test
    void testCompleteWithOnlySavings() throws Exception {
        assertState(perform(putSetup("{\"savings\":3000000}"), status().isOk()), true, "0", "3000000");
    }

    @Test
    void testCompleteWithExplicitNulls() throws Exception {
        assertState(perform(putSetup("{\"balance\":null,\"savings\":null}"), status().isOk()), true, "0", "0");
    }

    @Test
    void testCompleteWithEmptyObject() throws Exception {
        assertState(perform(putSetup("{}"), status().isOk()), true, "0", "0");
    }

    @Test
    void testCompleteWithoutBody() throws Exception {
        assertState(perform(withAuth(put("/api/financial-setup")), status().isOk()), true, "0", "0");
    }

    @Test
    void testCompleteRejectsNegativeBalance() throws Exception {
        assertEquals("Balance must not be negative", errorOf(putSetup("{\"balance\":-1}"), status().isBadRequest()));
        assertEquals("Savings must not be negative", errorOf(putSetup("{\"savings\":-0.01}"), status().isBadRequest()));

        assertState(getSetup(), false, "0", "0");
    }

    @Test
    void testCompleteRejectsTooManyDigits() throws Exception {
        errorOf(putSetup("{\"balance\":12345678901234}"), status().isBadRequest());
        errorOf(putSetup("{\"savings\":1.234}"), status().isBadRequest());

        assertState(getSetup(), false, "0", "0");
    }

    @Test
    void testCompleteTwiceReturnsConflictAndKeepsValues() throws Exception {
        perform(putSetup("{\"balance\":5000000,\"savings\":2000000}"), status().isOk());

        String error = errorOf(putSetup("{\"balance\":1,\"savings\":1}"), status().isConflict());

        assertEquals("Financial setup has already been completed", error);
        assertState(getSetup(), true, "5000000", "2000000");
        assertEquals(2, accountCount(user));
    }

    @Test
    void testCompleteAfterSkipReturnsConflict() throws Exception {
        perform(withAuth(post("/api/financial-setup/skip")), status().isOk());

        errorOf(putSetup("{\"balance\":5000000}"), status().isConflict());
        assertState(getSetup(), true, "0", "0");
    }

    // ===================== skip =====================

    @Test
    void testSkipCreatesZeroAccountsAndCompletes() throws Exception {
        assertState(perform(withAuth(post("/api/financial-setup/skip")), status().isOk()), true, "0", "0");
        assertEquals(2, accountCount(user));
    }

    @Test
    void testSkipIsIdempotent() throws Exception {
        perform(withAuth(post("/api/financial-setup/skip")), status().isOk());
        assertState(perform(withAuth(post("/api/financial-setup/skip")), status().isOk()), true, "0", "0");
        assertEquals(2, accountCount(user));
    }

    @Test
    void testSkipAfterCompleteDoesNotOverwrite() throws Exception {
        perform(putSetup("{\"balance\":5000000,\"savings\":2000000}"), status().isOk());

        assertState(perform(withAuth(post("/api/financial-setup/skip")), status().isOk()), true, "5000000", "2000000");
    }

    // ===================== concurrency =====================

    @Test
    void testConcurrentCompleteAppliesOnlyOnce() throws Exception {
        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        for (int i = 1; i <= threads; i++) {
            FinancialSetupRequest request = new FinancialSetupRequest();
            request.setBalance(BigDecimal.valueOf(i * 1000L));
            request.setSavings(BigDecimal.valueOf(i * 100L));
            Callable<Boolean> task = () -> {
                start.await();
                try {
                    financialSetupService.complete(user, request);
                    return true;
                } catch (DataAlreadyExistedException e) {
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

        assertEquals(1, succeeded, "exactly one setup request must win");
        assertEquals(2, accountCount(user));

        FinancialSetupResponse setup = getSetup();
        assertTrue(setup.isSetupCompleted());
        BigDecimal main = account(setup, AccountType.MAIN).getOpeningBalance();
        BigDecimal savings = account(setup, AccountType.SAVINGS).getOpeningBalance();
        // nilai MAIN & SAVINGS harus berasal dari request yang sama (i * 1000 dan i * 100)
        assertAmount(main.divide(BigDecimal.TEN).toPlainString(), savings, "values from one request");
    }

    // ===================== registration / login =====================

    @Test
    void testManualRegistrationCreatesAccountsAndReloginDoesNotDuplicate() throws Exception {
        userService.createVerifiedUser(new PendingRegistration(
            "Budi", "Santoso", "budi@gmail.com", BCrypt.hashpw("password123", BCrypt.gensalt()), null));
        User registered = userRepository.findByEmail("budi@gmail.com").orElseThrow();
        assertEquals(2, accountCount(registered));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("email", "budi@gmail.com", "password", "password123"))))
                .andExpect(status().isOk());
        }

        assertEquals(2, accountCount(registered));
        assertFalse(userRepository.existsByIdAndFinancialSetupCompletedAtIsNotNull(registered.getId()));
    }

    @Test
    void testGoogleReloginKeepsAccountsAndOpeningBalances() throws Exception {
        String email = "google-user@gmail.com";
        when(googleTokenVerifier.verify("id-token")).thenReturn(new GoogleUserInfo(email, "Budi", "Santoso"));

        googleLogin().andExpect(status().isOk());
        User googleUser = userRepository.findByEmail(email).orElseThrow();
        assertEquals(2, accountCount(googleUser));

        FinancialSetupRequest request = new FinancialSetupRequest();
        request.setBalance(new BigDecimal("1000000"));
        request.setSavings(new BigDecimal("3000000"));
        financialSetupService.complete(googleUser, request);

        googleLogin().andExpect(status().isOk());
        googleLogin().andExpect(status().isOk());

        User relogged = userRepository.findByEmail(email).orElseThrow();
        mockMvc.perform(get("/api/financial-setup").header("Authorization", "Bearer " + relogged.getToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.setupCompleted").value(true))
            .andExpect(jsonPath("$.data.accounts.length()").value(2))
            .andExpect(jsonPath("$.data.totalBalance").value(4000000));
    }

    private ResultActions googleLogin() throws Exception {
        return mockMvc.perform(post("/api/auth/google")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"idToken\":\"id-token\"}"));
    }

    // ===================== balances =====================

    @Test
    void testIncomeAndExpenseOnlyChangeMain() throws Exception {
        perform(putSetup("{\"balance\":1000000,\"savings\":3000000}"), status().isOk());
        Category salary = createCategory("Gaji", TransactionType.INCOME);
        Category food = createCategory("Makanan", TransactionType.EXPENSE);

        createTransaction(salary, "200000").andExpect(status().isOk());
        createTransaction(food, "50000").andExpect(status().isOk());

        FinancialSetupResponse setup = getSetup();
        assertAmount("1150000", account(setup, AccountType.MAIN).getCurrentBalance(), "MAIN");
        assertAmount("3000000", account(setup, AccountType.SAVINGS).getCurrentBalance(), "SAVINGS");
        assertAmount("4150000", setup.getTotalBalance(), "total");
        // saldo awal tidak berubah karena transaksi
        assertAmount("1000000", account(setup, AccountType.MAIN).getOpeningBalance(), "MAIN opening");
    }

    @Test
    void testSavingAddsToSavingsWithoutTouchingMain() throws Exception {
        perform(putSetup("{\"balance\":1000000}"), status().isOk());
        Category saving = createCategory("Tabungan", TransactionType.SAVING);

        createTransaction(saving, "300000").andExpect(status().isOk());

        FinancialSetupResponse setup = getSetup();
        assertAmount("1000000", account(setup, AccountType.MAIN).getCurrentBalance(), "MAIN");
        assertAmount("300000", account(setup, AccountType.SAVINGS).getCurrentBalance(), "SAVINGS");
        assertAmount("1300000", setup.getTotalBalance(), "total");
    }

    @Test
    void testChangingCategoryTypeMovesTransactionToMatchingAccount() throws Exception {
        perform(putSetup("{\"balance\":1000000}"), status().isOk());
        Category category = createCategory("Lain-lain", TransactionType.EXPENSE);
        createTransaction(category, "50000").andExpect(status().isOk());

        category.setType(TransactionType.SAVING.name());
        categoryRepository.save(category);

        FinancialSetupResponse setup = getSetup();
        assertAmount("1000000", account(setup, AccountType.MAIN).getCurrentBalance(), "MAIN");
        assertAmount("50000", account(setup, AccountType.SAVINGS).getCurrentBalance(), "SAVINGS");
    }

    @Test
    void testDeletedTransactionIsExcludedFromBalance() throws Exception {
        perform(putSetup("{\"balance\":1000000}"), status().isOk());
        Category food = createCategory("Makanan", TransactionType.EXPENSE);
        createTransaction(food, "50000").andExpect(status().isOk());
        String code = transactionRepository.findAll().get(0).getTransactionCode();

        mockMvc.perform(withAuth(delete("/api/transaction/" + code)))
            .andExpect(status().isOk());

        assertAmount("1000000", account(getSetup(), AccountType.MAIN).getCurrentBalance(), "MAIN");
    }

    private ResultActions createTransaction(Category category, String amount) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("transactonName", "test");
        body.put("amount", new BigDecimal(amount));
        body.put("categoryId", category.getId());
        body.put("date", LocalDate.now().toString());
        String json = objectMapper.writeValueAsString(body);
        return mockMvc.perform(withAuth(post("/api/transaction"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
    }
}

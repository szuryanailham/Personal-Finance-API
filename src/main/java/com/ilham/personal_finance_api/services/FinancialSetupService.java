package com.ilham.personal_finance_api.services;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ilham.personal_finance_api.dto.AccountResponse;
import com.ilham.personal_finance_api.dto.AccountType;
import com.ilham.personal_finance_api.dto.FinancialSetupRequest;
import com.ilham.personal_finance_api.dto.FinancialSetupResponse;
import com.ilham.personal_finance_api.entity.Account;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.exception.DataAlreadyExistedException;
import com.ilham.personal_finance_api.repository.UserRepository;
import com.ilham.personal_finance_api.services.AccountBalanceService.AccountBalance;

// setup saldo awal MAIN & SAVINGS; hanya bisa diselesaikan (atau dilewati) satu kali per user
@Service
public class FinancialSetupService {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountBalanceService accountBalanceService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private Clock clock;

    // [MAIN] get setup state
    @Transactional
    public FinancialSetupResponse get(User user) {
        return toResponse(user, accountService.getAccounts(user));
    }

    // [MAIN] complete setup; 409 jika sudah pernah selesai / dilewati
    @Transactional
    public FinancialSetupResponse complete(User user, FinancialSetupRequest request) {
        validationService.validate(request);
        accountService.ensureDefaultAccounts(user);

        if (!markCompleted(user)) {
            throw new DataAlreadyExistedException("Financial setup has already been completed");
        }

        List<Account> accounts = accountService.getAccounts(user);
        accountService.findByType(accounts, AccountType.MAIN).setOpeningBalance(orZero(request.getBalance()));
        accountService.findByType(accounts, AccountType.SAVINGS).setOpeningBalance(orZero(request.getSavings()));
        return toResponse(user, accounts);
    }

    // [MAIN] skip setup; idempoten, saldo awal tetap 0 dan setup yang sudah selesai tidak ditimpa
    @Transactional
    public FinancialSetupResponse skip(User user) {
        accountService.ensureDefaultAccounts(user);
        markCompleted(user);
        return toResponse(user, accountService.getAccounts(user));
    }

    // [HELPER] false berarti setup sudah selesai sebelumnya
    private boolean markCompleted(User user) {
        return userRepository.markFinancialSetupCompleted(user.getId(), LocalDateTime.now(clock)) > 0;
    }

    // [HELPER]
    private BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    // [HELPER]
    private FinancialSetupResponse toResponse(User user, List<Account> accounts) {
        List<AccountBalance> balances = accountBalanceService.getBalances(user, accounts);

        FinancialSetupResponse response = new FinancialSetupResponse();
        response.setSetupCompleted(userRepository.existsByIdAndFinancialSetupCompletedAtIsNotNull(user.getId()));
        response.setAccounts(balances.stream().map(this::toAccountResponse).toList());
        response.setTotalBalance(accountBalanceService.total(balances));
        return response;
    }

    // [HELPER]
    private AccountResponse toAccountResponse(AccountBalance balance) {
        Account account = balance.account();
        AccountResponse response = new AccountResponse();
        response.setId(account.getId());
        response.setAccountType(account.getAccountType());
        response.setName(account.getName());
        response.setOpeningBalance(account.getOpeningBalance());
        response.setCurrentBalance(balance.currentBalance());
        return response;
    }
}

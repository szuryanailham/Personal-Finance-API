package com.ilham.personal_finance_api.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ilham.personal_finance_api.dto.AccountType;
import com.ilham.personal_finance_api.entity.Account;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.repository.AccountRepository;

@Service
public class AccountService {

    @Autowired
    private AccountRepository accountRepository;

    // [MAIN] buat akun MAIN & SAVINGS jika belum ada; aman dipanggil berulang / bersamaan
    @Transactional
    public void ensureDefaultAccounts(User user) {
        for (AccountType type : AccountType.values()) {
            accountRepository.insertIfMissing(user.getId(), type.getDefaultName(), type.name());
        }
    }

    // [MAIN] akun milik user (MAIN lalu SAVINGS); akun default dibuat bila belum ada
    @Transactional
    public List<Account> getAccounts(User user) {
        List<Account> accounts = accountRepository.findAllByUserOrderByAccountTypeAsc(user);
        if (accounts.size() == AccountType.values().length) {
            return accounts;
        }

        ensureDefaultAccounts(user);
        return accountRepository.findAllByUserOrderByAccountTypeAsc(user);
    }

    // [HELPER]
    public Account findByType(List<Account> accounts, AccountType type) {
        return accounts.stream()
            .filter(account -> account.getAccountType() == type)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Default account " + type + " is missing"));
    }
}

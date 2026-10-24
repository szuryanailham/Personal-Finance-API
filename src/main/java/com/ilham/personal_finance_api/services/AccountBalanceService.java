package com.ilham.personal_finance_api.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ilham.personal_finance_api.dto.AccountAmount;
import com.ilham.personal_finance_api.dto.AccountType;
import com.ilham.personal_finance_api.dto.TransactionType;
import com.ilham.personal_finance_api.entity.Account;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.repository.TransactionRepository;
import com.ilham.personal_finance_api.repository.TransferRepository;

/**
 * Satu-satunya tempat perhitungan saldo akun. Akun sebuah transaksi ditentukan oleh type category-nya:
 *
 * MAIN    = opening + INCOME - EXPENSE + transfer masuk - transfer keluar
 * SAVINGS = opening + SAVING           + transfer masuk - transfer keluar
 *
 * Transfer hanya memindahkan dana antar akun, sehingga tidak mengubah total saldo user.
 */
@Service
public class AccountBalanceService {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransferRepository transferRepository;

    public record AccountBalance(Account account, BigDecimal currentBalance) {}

    // [MAIN] accounts harus milik user (dari AccountService.getAccounts)
    @Transactional(readOnly = true)
    public List<AccountBalance> getBalances(User user, List<Account> accounts) {
        Map<UUID, BigDecimal> outgoing = toMap(transferRepository.sumOutgoingByAccount(user));
        Map<UUID, BigDecimal> incoming = toMap(transferRepository.sumIncomingByAccount(user));

        return accounts.stream()
            .map(account -> new AccountBalance(account, account.getOpeningBalance()
                .add(transactionTotal(user, account.getAccountType()))
                .add(incoming.getOrDefault(account.getId(), BigDecimal.ZERO))
                .subtract(outgoing.getOrDefault(account.getId(), BigDecimal.ZERO))))
            .toList();
    }

    // [MAIN]
    @Transactional(readOnly = true)
    public BigDecimal getBalance(User user, List<Account> accounts, Account account) {
        return getBalances(user, accounts).stream()
            .filter(balance -> balance.account().getId().equals(account.getId()))
            .map(balance -> balance.currentBalance())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Account " + account.getId() + " is not owned by user"));
    }

    // [HELPER]
    public BigDecimal total(List<AccountBalance> balances) {
        return balances.stream()
            .map(balance -> balance.currentBalance())
            .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));
    }

    // [HELPER] pengaruh transaksi (INCOME / EXPENSE / SAVING) terhadap saldo akun
    private BigDecimal transactionTotal(User user, AccountType accountType) {
        return switch (accountType) {
            case MAIN -> sumByType(user, TransactionType.INCOME).subtract(sumByType(user, TransactionType.EXPENSE));
            case SAVINGS -> sumByType(user, TransactionType.SAVING);
        };
    }

    // [HELPER]
    private BigDecimal sumByType(User user, TransactionType type) {
        return transactionRepository.sumAmountByType(user, type.name());
    }

    // [HELPER]
    private Map<UUID, BigDecimal> toMap(List<AccountAmount> totals) {
        return totals.stream()
            .collect(Collectors.toMap(total -> total.accountId(), total -> total.amount()));
    }
}

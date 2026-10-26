package com.ilham.personal_finance_api.services;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ilham.personal_finance_api.dto.CreateTransferRequest;
import com.ilham.personal_finance_api.dto.TransferResponse;
import com.ilham.personal_finance_api.entity.Account;
import com.ilham.personal_finance_api.entity.Transfer;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.repository.AccountRepository;
import com.ilham.personal_finance_api.repository.TransferRepository;

// transfer internal antar akun milik user yang sama; tidak dihitung sebagai income / expense
@Service
public class TransferService {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountBalanceService accountBalanceService;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private Clock clock;

    // [MAIN] create transfer
    // kedua akun dikunci (PESSIMISTIC_WRITE) sebelum cek saldo, sehingga transfer bersamaan
    // dari akun yang sama diproses bergantian dan tidak bisa membuat saldo minus.
    @Transactional
    public TransferResponse create(User user, CreateTransferRequest request) {
        validationService.validate(request);

        UUID fromAccountId = request.getFromAccountId();
        UUID toAccountId = request.getToAccountId();
        if (fromAccountId.equals(toAccountId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source and destination accounts must be different");
        }

        // akun milik user lain diperlakukan sama dengan akun yang tidak ada (tidak membocorkan keberadaannya)
        List<Account> locked = accountRepository.findAllByUserAndIdInForUpdate(user, List.of(fromAccountId, toAccountId));
        if (locked.size() != 2) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }

        Account source = findById(locked, fromAccountId);
        Account destination = findById(locked, toAccountId);

        BigDecimal available = accountBalanceService.getBalance(user, accountService.getAccounts(user), source);
        if (available.compareTo(request.getAmount()) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient balance in source account");
        }

        Transfer transfer = new Transfer();
        transfer.setUser(user);
        transfer.setFromAccount(source);
        transfer.setToAccount(destination);
        transfer.setAmount(request.getAmount());
        transfer.setNote(request.getNote());
        transfer.setCreatedAt(LocalDateTime.now(clock));
        transferRepository.save(transfer);

        return toResponse(transfer);
    }

    // [MAIN] get all transfer (terbaru lebih dulu)
    @Transactional(readOnly = true)
    public Page<TransferResponse> getAll(User user, int skip, int limit) {
        if (skip < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Skip must be greater than or equal to 0");
        }

        if (limit <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be greater than 0");
        }

        Pageable pageable = PageRequest.of(skip / limit, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        return transferRepository.findAllByUser(user, pageable).map(this::toResponse);
    }

    // [HELPER]
    private Account findById(List<Account> accounts, UUID id) {
        return accounts.stream()
            .filter(account -> account.getId().equals(id))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    // [HELPER]
    private TransferResponse toResponse(Transfer transfer) {
        TransferResponse response = new TransferResponse();
        response.setId(transfer.getId());
        response.setFromAccountId(transfer.getFromAccount().getId());
        response.setToAccountId(transfer.getToAccount().getId());
        response.setAmount(transfer.getAmount());
        response.setNote(transfer.getNote());
        response.setCreatedAt(transfer.getCreatedAt());
        return response;
    }
}

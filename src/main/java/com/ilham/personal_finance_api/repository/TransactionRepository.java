package com.ilham.personal_finance_api.repository;


import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ilham.personal_finance_api.entity.Transaction;
import com.ilham.personal_finance_api.entity.User;

public interface TransactionRepository extends JpaRepository<Transaction, String> {
    boolean existsByTransactionCode(String transactionCode);
    Optional<Transaction> findByUserAndTransactionCode(User user, String transactionCode);
    Page<Transaction> findAllByUser(User user, Pageable pageable);
}



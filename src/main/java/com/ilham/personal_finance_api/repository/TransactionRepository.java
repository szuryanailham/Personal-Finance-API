package com.ilham.personal_finance_api.repository;


import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.ilham.personal_finance_api.entity.Transaction;
import com.ilham.personal_finance_api.entity.User;

public interface TransactionRepository extends JpaRepository<Transaction, String>, JpaSpecificationExecutor<Transaction> {
    boolean existsByTransactionCode(String transactionCode);
    Optional<Transaction> findByUserAndTransactionCode(User user, String transactionCode);
}



package com.ilham.personal_finance_api.repository;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ilham.personal_finance_api.entity.Transaction;
import com.ilham.personal_finance_api.entity.User;

public interface TransactionRepository extends JpaRepository<Transaction, String>, JpaSpecificationExecutor<Transaction> {
    boolean existsByTransactionCode(String transactionCode);
    Optional<Transaction> findByUserAndTransactionCode(User user, String transactionCode);
    List<Transaction> findByUserAndTransactionDateBetweenAndIsDeletedFalse(User user, LocalDateTime start, LocalDateTime end);
    List<Transaction> findByUserAndTransactionDateBetween( User user, LocalDate startDate, LocalDate endDate);
    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM Transaction t
        WHERE t.user = :user
        AND t.category.type = :type
        AND t.isDeleted = false
        AND t.transactionDate >= :start
        AND t.transactionDate < :end
        """)
    BigDecimal sumAmountByTypeAndPeriod(
        @Param("user") User user,
        @Param("type") String type,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );
}



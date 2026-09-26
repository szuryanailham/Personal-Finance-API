package com.ilham.personal_finance_api.repository;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ilham.personal_finance_api.dto.TransactionStatisticResponse.TransactionPeriodItemResponse;
import com.ilham.personal_finance_api.entity.Transaction;
import com.ilham.personal_finance_api.entity.User;

public interface TransactionRepository extends JpaRepository<Transaction, String>, JpaSpecificationExecutor<Transaction> {
    boolean existsByTransactionCode(String transactionCode);
    Optional<Transaction> findTopByTransactionCodeStartingWithOrderByTransactionCodeDesc(String prefix);
    Optional<Transaction> findByUserAndTransactionCode(User user, String transactionCode);
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

    @Query("""
        SELECT new com.ilham.personal_finance_api.dto.TransactionStatisticResponse$TransactionPeriodItemResponse(
            CAST(t.transactionDate AS LocalDate),
            COALESCE(SUM(CASE WHEN t.category.type = 'INCOME' THEN t.amount ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN t.category.type = 'EXPENSE' THEN t.amount ELSE 0 END), 0)
        )
        FROM Transaction t
        WHERE t.user = :user
        AND t.isDeleted = false
        AND t.transactionDate >= :start
        AND t.transactionDate < :end
        GROUP BY CAST(t.transactionDate AS LocalDate)
        ORDER BY CAST(t.transactionDate AS LocalDate)
        """)
    List<TransactionPeriodItemResponse> getSumDailyIncomeAndExpenseByPeriod(
        @Param("user") User user,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );
}



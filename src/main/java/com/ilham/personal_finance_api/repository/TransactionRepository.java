package com.ilham.personal_finance_api.repository;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ilham.personal_finance_api.dto.CategoryStatisticResponse.CategoryStatisticItemResponse;
import com.ilham.personal_finance_api.dto.TransactionStatisticResponse.TransactionPeriodItemResponse;
import com.ilham.personal_finance_api.entity.Transaction;
import com.ilham.personal_finance_api.entity.User;

public interface TransactionRepository extends JpaRepository<Transaction, String> {
    boolean existsByTransactionCode(String transactionCode);
    Optional<Transaction> findTopByTransactionCodeStartingWithOrderByTransactionCodeDesc(String prefix);
    Optional<Transaction> findByUserAndTransactionCodeAndIsDeletedFalse(User user, String transactionCode);
    List<Transaction> findAllByUserAndTransactionCodeIn(User user, Collection<String> transactionCodes);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Transaction t
        SET t.isDeleted = true
        WHERE t.user = :user
        AND t.transactionCode IN :transactionCodes
        AND t.isDeleted = false
        """)
    int softDeleteAllByUserAndTransactionCodeIn(
        @Param("user") User user,
        @Param("transactionCodes") Collection<String> transactionCodes
    );

    @Query("""
        SELECT t
        FROM Transaction t
        WHERE t.user = :user
        AND t.isDeleted = false
        AND (:search IS NULL
            OR LOWER(t.transactionName) LIKE :search
            OR LOWER(t.description) LIKE :search)
        AND (CAST(:start AS LocalDateTime) IS NULL OR t.transactionDate >= :start)
        AND (CAST(:end AS LocalDateTime) IS NULL OR t.transactionDate < :end)
        AND (:type IS NULL OR t.category.type = :type)
        AND (:categoryId IS NULL OR t.category.id = :categoryId)
        """)
    Page<Transaction> findAllActive(
        @Param("user") User user,
        @Param("search") String search,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end,
        @Param("type") String type,
        @Param("categoryId") UUID categoryId,
        Pageable pageable
    );

    // untuk export: hanya transaksi aktif (is_deleted = false), category ikut di-fetch agar tidak N+1
    @Query("""
        SELECT t
        FROM Transaction t
        JOIN FETCH t.category
        WHERE t.user = :user
        AND t.isDeleted = false
        AND (CAST(:start AS LocalDateTime) IS NULL OR t.transactionDate >= :start)
        AND (CAST(:end AS LocalDateTime) IS NULL OR t.transactionDate < :end)
        ORDER BY t.transactionDate ASC, t.transactionCode ASC
        """)
    List<Transaction> findAllActiveForExport(
        @Param("user") User user,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end,
        Limit limit
    );

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

    @Query("""
        SELECT new com.ilham.personal_finance_api.dto.CategoryStatisticResponse$CategoryStatisticItemResponse(
            c.id,
            c.name,
            COALESCE(SUM(t.amount), 0),
            COUNT(t)
        )
        FROM Transaction t
        JOIN t.category c
        WHERE t.user = :user
        AND t.isDeleted = false
        AND c.type = :type
        AND t.transactionDate >= :start
        AND t.transactionDate < :end
        GROUP BY c.id, c.name
        ORDER BY SUM(t.amount) DESC, c.name ASC
        """)
    List<CategoryStatisticItemResponse> getSumAmountPerCategoryByTypeAndPeriod(
        @Param("user") User user,
        @Param("type") String type,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );
}



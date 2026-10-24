package com.ilham.personal_finance_api.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ilham.personal_finance_api.dto.AccountAmount;
import com.ilham.personal_finance_api.entity.Transfer;
import com.ilham.personal_finance_api.entity.User;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    @EntityGraph(attributePaths = {"fromAccount", "toAccount"})
    Page<Transfer> findAllByUser(User user, Pageable pageable);

    long countByUser(User user);

    @Query("""
        SELECT new com.ilham.personal_finance_api.dto.AccountAmount(t.fromAccount.id, COALESCE(SUM(t.amount), 0))
        FROM Transfer t
        WHERE t.user = :user
        GROUP BY t.fromAccount.id
        """)
    List<AccountAmount> sumOutgoingByAccount(@Param("user") User user);

    @Query("""
        SELECT new com.ilham.personal_finance_api.dto.AccountAmount(t.toAccount.id, COALESCE(SUM(t.amount), 0))
        FROM Transfer t
        WHERE t.user = :user
        GROUP BY t.toAccount.id
        """)
    List<AccountAmount> sumIncomingByAccount(@Param("user") User user);
}

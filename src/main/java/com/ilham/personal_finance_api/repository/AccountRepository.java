package com.ilham.personal_finance_api.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ilham.personal_finance_api.entity.Account;
import com.ilham.personal_finance_api.entity.User;

import jakarta.persistence.LockModeType;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    List<Account> findAllByUserOrderByAccountTypeAsc(User user);

    // urutan id tetap agar dua transfer yang berlawanan arah tidak saling deadlock
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT a
        FROM Account a
        WHERE a.user = :user
        AND a.id IN :ids
        ORDER BY a.id
        """)
    List<Account> findAllByUserAndIdInForUpdate(
        @Param("user") User user,
        @Param("ids") Collection<UUID> ids
    );

    // idempoten & aman untuk request bersamaan: unique (user_id, account_type) mencegah duplikat
    @Modifying(flushAutomatically = true)
    @Query(value = """
        INSERT INTO accounts (id, user_id, name, account_type, opening_balance)
        VALUES (gen_random_uuid(), :userId, :name, :accountType, 0)
        ON CONFLICT (user_id, account_type) DO NOTHING
        """, nativeQuery = true)
    int insertIfMissing(
        @Param("userId") UUID userId,
        @Param("name") String name,
        @Param("accountType") String accountType
    );
}

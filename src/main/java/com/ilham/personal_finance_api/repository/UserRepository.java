package com.ilham.personal_finance_api.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ilham.personal_finance_api.entity.User;


public interface UserRepository extends JpaRepository<User, String> {
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    boolean existsByEmail(@Param("email") String email);
    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmail(@Param("email") String email);
    Optional<User> findFirstByToken(String token);
    boolean existsByIdAndFinancialSetupCompletedAtIsNotNull(UUID id);

    // guard "hanya sekali": 0 baris berarti setup sudah selesai (row lock menahan request bersamaan)
    @Modifying(flushAutomatically = true)
    @Query("""
        UPDATE User u
        SET u.financialSetupCompletedAt = :completedAt
        WHERE u.id = :id
        AND u.financialSetupCompletedAt IS NULL
        """)
    int markFinancialSetupCompleted(
        @Param("id") UUID id,
        @Param("completedAt") LocalDateTime completedAt
    );
}

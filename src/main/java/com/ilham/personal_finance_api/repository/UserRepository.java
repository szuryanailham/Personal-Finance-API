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
    @Query(value = """
        SELECT new com.ilham.personal_finance_api.dto.AdminUserResponse(
            u.id, u.firstName, u.lastName, u.email, COUNT(t.id),
            SUM(CASE WHEN t.id IS NOT NULL AND t.isDeleted = false THEN 1L ELSE 0L END))
        FROM User u LEFT JOIN u.transactions t
        GROUP BY u.id, u.firstName, u.lastName, u.email
        ORDER BY LOWER(u.email), u.id
        """, countQuery = "SELECT COUNT(u) FROM User u")
    org.springframework.data.domain.Page<com.ilham.personal_finance_api.dto.AdminUserResponse> findAdminUsers(
        org.springframework.data.domain.Pageable pageable);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    boolean existsByEmail(@Param("email") String email);
    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmail(@Param("email") String email);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findForPasswordResetByEmail(@Param("email") String email);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.passwordResetTokenHash = :hash")
    Optional<User> findForPasswordResetByHash(@Param("hash") String hash);

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

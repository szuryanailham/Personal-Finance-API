package com.ilham.personal_finance_api.entity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    private String email;

    @Column(name = "is_guidance_completed", nullable = false)
    private boolean isGuidanceCompleted = false;

    private String password;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "auth_provider", nullable = false)
    private AuthProvider authProvider = AuthProvider.PASSWORD;


    private String token;

    @Column(name = "token_expired_at")
    private Long tokenExpiredAt;

    // null = setup saldo awal belum diselesaikan / dilewati
    @Column(name = "financial_setup_completed_at")
    private LocalDateTime financialSetupCompletedAt;


    @OneToMany(mappedBy = "user")
    private List<Transaction> transactions;

    
}

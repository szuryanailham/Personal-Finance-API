package com.ilham.personal_finance_api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ilham.personal_finance_api.entity.User;


public interface UserRepository extends JpaRepository<User, String> {
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);
    Optional<User> findFirstByToken(String token);
    
}

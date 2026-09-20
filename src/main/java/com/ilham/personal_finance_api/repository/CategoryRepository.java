package com.ilham.personal_finance_api.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import com.ilham.personal_finance_api.entity.Category;
import com.ilham.personal_finance_api.entity.User;

import org.springframework.data.domain.Page;

import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    boolean existsByNameAndType(String name, String type);
    Page<Category> findAllByUser(User user, Pageable pageable);
    Optional<Category> findByIdAndUser(UUID id, User user);

}
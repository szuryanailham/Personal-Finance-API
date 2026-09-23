package com.ilham.personal_finance_api.services;


import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Page;
import com.ilham.personal_finance_api.entity.Category;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.exception.DataAlreadyExistedException;
import com.ilham.personal_finance_api.dto.CategoryResponse;
import com.ilham.personal_finance_api.dto.CreateCategoryRequest;
import com.ilham.personal_finance_api.dto.CreateCategoryResponse;
import com.ilham.personal_finance_api.dto.UpdateCategoryRequest;
import com.ilham.personal_finance_api.repository.CategoryRepository;;



@Service 
public class CategoryService {

    @Autowired 
    private CategoryRepository categoryRepository;

    @Autowired 
    private ValidationService validationService;

    @Transactional 
    public CreateCategoryResponse create(User user ,CreateCategoryRequest request) {

            validationService.validate(request);

            if (categoryRepository.existsByNameAndType(
            request.getName(),
            request.getType().name()
            )) {

                throw new DataAlreadyExistedException(
                    "Category name is already existed"
                );
            }

                    Category category = new Category();
                    category.setName(request.getName());
                    category.setUser(user);
                    category.setType(request.getType().name());
                    categoryRepository.save(category);

                    return CreateCategoryResponse.builder()
                    .name(category.getName())
                    .type(category.getType())
                    .build();
                
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(User user, UUID id) {
        
        Category category = categoryRepository.findByIdAndUser(id, user)
       .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
         return toCategoryResponse(category);
    }



    @Transactional (readOnly = true)
    public Page<CategoryResponse>  getAll(User user,  int skip , int limit) {
        if (skip < 0) {
                 throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                "Skip must be greater than or equal to 0"
            );
        }

        if (limit <= 0) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Limit must be greater than 0"
            );
        }

        Pageable pageable = PageRequest.of(skip/ limit, limit);
        
            Page<Category> categories =
            categoryRepository.findAllByUser(user, pageable);

        return categories.map(this::toCategoryResponse);
    }



    private CategoryResponse toCategoryResponse(Category category) {
         return CategoryResponse.builder().id(category.getId()).name(category.getName()).type(category.getType()).build();
    }

    @Transactional
    public void delete(User user, UUID categoryID) {
        Category category = categoryRepository.findByIdAndUser(categoryID, user)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        categoryRepository.delete(category);
    }

    @Transactional
    public CategoryResponse update(User user, UUID categoryId, UpdateCategoryRequest request) {
        validationService.validate(request);

        Category category = categoryRepository.findByIdAndUser(categoryId, user)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        category.setName(request.getName());
        category.setType(request.getType().name());
        categoryRepository.save(category);
        return toCategoryResponse(category);
    }    

}

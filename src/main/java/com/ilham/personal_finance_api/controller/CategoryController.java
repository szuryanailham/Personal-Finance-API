package com.ilham.personal_finance_api.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RestController;

import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.model.CategoryResponse;
import com.ilham.personal_finance_api.model.CreateCategoryRequest;
import com.ilham.personal_finance_api.model.CreateCategoryResponse;
import com.ilham.personal_finance_api.model.PaginationResponse;
import com.ilham.personal_finance_api.model.UpdateCategoryRequest;
import com.ilham.personal_finance_api.model.WebResponse;
import com.ilham.personal_finance_api.services.CategoryService;

import jakarta.validation.Valid;

@RestController 
public class CategoryController {

    @Autowired 
    private CategoryService categoryService;

  @PostMapping(
    path = "api/categories",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE
  )

  public WebResponse<CreateCategoryResponse> create(User user ,@Valid @RequestBody  CreateCategoryRequest request) {
    CreateCategoryResponse response = categoryService.create(user,request);
    return new WebResponse<>(
        response,
        "Category created successfully",
        null,
        null
    );
  }

    @GetMapping(
    path = "api/categories/{categoryId}",
    produces = MediaType.APPLICATION_JSON_VALUE
  )

  public WebResponse<CategoryResponse>get(User user , @PathVariable("categoryId") UUID categoryId) {
    CategoryResponse categoryResponse = categoryService.get(user, categoryId);
    return WebResponse.<CategoryResponse>builder().data(categoryResponse).build();
  }

      @GetMapping(
    path = "api/categories",
    produces = MediaType.APPLICATION_JSON_VALUE
  )



  public WebResponse<List<CategoryResponse>> getAll(User user,
      @RequestParam int skip,
      @RequestParam int limit) {
    Page<CategoryResponse> categories = categoryService.getAll( user, skip, limit);
    return WebResponse.<List<CategoryResponse>>builder()
        .data(categories.getContent())
        .paging(PaginationResponse.builder()
            .currentPage(categories.getNumber() + 1)
            .totalPage(categories.getTotalPages())
            .size(categories.getSize())
            .build())
        .build();
  }

@DeleteMapping(
    path = "/api/categories/{categoryId}",
    produces = MediaType.APPLICATION_JSON_VALUE
)
public WebResponse<String> delete(User user, @PathVariable("categoryId") UUID categoryId
) {
    categoryService.delete(user, categoryId);

    return WebResponse.<String>builder()
        .message("Category deleted successfully")
        .build();
}


@PutMapping (
    path = "/api/categories/{categoryId}",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE
)


public WebResponse<CategoryResponse>update( User user ,
  @Valid @RequestBody  UpdateCategoryRequest request,
  @PathVariable("categoryId") UUID categoryId
) {
    CategoryResponse categoryResponse = categoryService.update(user,categoryId, request);
  return  WebResponse.<CategoryResponse>builder().data(categoryResponse).build();
}


}

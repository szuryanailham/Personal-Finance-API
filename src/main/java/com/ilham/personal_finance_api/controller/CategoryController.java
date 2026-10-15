package com.ilham.personal_finance_api.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
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
import com.ilham.personal_finance_api.dto.CategoryResponse;
import com.ilham.personal_finance_api.dto.CategoryStatPeriod;
import com.ilham.personal_finance_api.dto.CategoryStatisticResponse;
import com.ilham.personal_finance_api.dto.CreateCategoryRequest;
import com.ilham.personal_finance_api.dto.CreateCategoryResponse;
import com.ilham.personal_finance_api.dto.PaginationResponse;
import com.ilham.personal_finance_api.dto.UpdateCategoryRequest;
import com.ilham.personal_finance_api.dto.WebResponse;
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
    WebResponse<CreateCategoryResponse> webResponse = new WebResponse<>();
    webResponse.setData(response);
    webResponse.setMessage("Category created successfully");
    return webResponse;
  }

    @GetMapping(
    path = "api/categories/{categoryId}",
    produces = MediaType.APPLICATION_JSON_VALUE
  )

  public WebResponse<CategoryResponse>get(User user , @PathVariable("categoryId") UUID categoryId) {
    CategoryResponse categoryResponse = categoryService.get(user, categoryId);
    WebResponse<CategoryResponse> response = new WebResponse<>();
    response.setData(categoryResponse);
    return response;
  }

      @GetMapping(
    path = "api/categories",
    produces = MediaType.APPLICATION_JSON_VALUE
  )



  public WebResponse<List<CategoryResponse>> getAll(User user,
      @RequestParam int skip,
      @RequestParam int limit) {
    Page<CategoryResponse> categories = categoryService.getAll( user, skip, limit);
    PaginationResponse paging = new PaginationResponse();
    paging.setCurrentPage(categories.getNumber() + 1);
    paging.setTotalPage(categories.getTotalPages());
    paging.setSize(categories.getSize());
    paging.setTotalItem(categories.getTotalElements());

    WebResponse<List<CategoryResponse>> response = new WebResponse<>();
    response.setData(categories.getContent());
    response.setPaging(paging);
    return response;
  }

@DeleteMapping(
    path = "/api/categories/{categoryId}",
    produces = MediaType.APPLICATION_JSON_VALUE
)
public WebResponse<String> delete(User user, @PathVariable("categoryId") UUID categoryId
) {
    categoryService.delete(user, categoryId);

    WebResponse<String> response = new WebResponse<>();
    response.setMessage("Category deleted successfully");
    return response;
}


@GetMapping(
    path = "/api/categories/statistic",
    produces = MediaType.APPLICATION_JSON_VALUE
)
public WebResponse<CategoryStatisticResponse> getStatistic(User user,
    @RequestParam(required = false) String period,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
) {
    CategoryStatisticResponse response = categoryService.getExpenseStatistic(
        user, CategoryStatPeriod.fromValue(period), startDate, endDate);
    WebResponse<CategoryStatisticResponse> webResponse = new WebResponse<>();
    webResponse.setData(response);
    webResponse.setMessage("Expense statistics retrieved successfully");
    return webResponse;
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
    WebResponse<CategoryResponse> response = new WebResponse<>();
    response.setData(categoryResponse);
    return response;
}


}

package com.ilham.personal_finance_api.services;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
import com.ilham.personal_finance_api.dto.CategoryStatPeriod;
import com.ilham.personal_finance_api.dto.CategoryStatisticResponse;
import com.ilham.personal_finance_api.dto.CategoryStatisticResponse.CategoryStatisticItemResponse;
import com.ilham.personal_finance_api.dto.CategoryStatisticResponse.PeriodResponse;
import com.ilham.personal_finance_api.dto.CreateCategoryRequest;
import com.ilham.personal_finance_api.dto.CreateCategoryResponse;
import com.ilham.personal_finance_api.dto.TransactionType;
import com.ilham.personal_finance_api.dto.UpdateCategoryRequest;
import com.ilham.personal_finance_api.repository.CategoryRepository;
import com.ilham.personal_finance_api.repository.TransactionRepository;



@Service 
public class CategoryService {

    @Autowired 
    private CategoryRepository categoryRepository;

    @Autowired 
    private ValidationService validationService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private Clock clock;

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int PERCENTAGE_SCALE = 2;

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
            categoryRepository.findAllByUserAndIsDeletedFalse(user, pageable);

        return categories.map(this::toCategoryResponse);
    }



    private CategoryResponse toCategoryResponse(Category category) {
         return CategoryResponse.builder().id(category.getId()).name(category.getName()).type(category.getType()).build();
    }

    @Transactional
    public void delete(User user, UUID categoryID) {
        Category category = categoryRepository.findByIdAndUser(categoryID, user)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        category.setDeleted(true);
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

    @Transactional(readOnly = true)
    public CategoryStatisticResponse getExpenseStatistic(User user, CategoryStatPeriod period,
            LocalDate startDate, LocalDate endDate) {
        DateRange range = resolveRange(period, startDate, endDate);

        List<CategoryStatisticItemResponse> items = transactionRepository.getSumAmountPerCategoryByTypeAndPeriod(
            user,
            TransactionType.EXPENSE.name(),
            range.startDateTime(),
            range.endDateTimeExclusive()
        );

        BigDecimal totalExpense = items.stream()
            .map(CategoryStatisticItemResponse::getTotalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CategoryStatisticResponse.builder()
            .period(new PeriodResponse(range.start(), range.end()))
            .totalExpense(totalExpense)
            .categories(items.stream().map(item -> withPercentage(item, totalExpense)).toList())
            .build();
    }

    // [HELPER] rentang kalender inklusif; startDate/endDate hanya dipakai untuk CUSTOM
    private DateRange resolveRange(CategoryStatPeriod period, LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now(clock);
        return switch (period) {
            case YEARLY -> new DateRange(today.withDayOfYear(1), today.withDayOfYear(today.lengthOfYear()));
            case MONTHLY -> new DateRange(today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()));
            case WEEKLY -> new DateRange(today.minusDays(6), today);
            case TODAY -> new DateRange(today, today);
            case CUSTOM -> resolveCustomRange(startDate, endDate);
        };
    }

    // [HELPER]
    private DateRange resolveCustomRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate and endDate are required for CUSTOM period");
        }

        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must be before or equal to endDate");
        }

        return new DateRange(startDate, endDate);
    }

    private CategoryStatisticItemResponse withPercentage(CategoryStatisticItemResponse item, BigDecimal totalExpense) {
        BigDecimal percentage = totalExpense.signum() == 0
            ? BigDecimal.ZERO
            : item.getTotalAmount()
                .multiply(ONE_HUNDRED)
                .divide(totalExpense, PERCENTAGE_SCALE, RoundingMode.HALF_UP);

        return item.toBuilder().percentage(percentage).build();
    }

    private record DateRange(LocalDate start, LocalDate end) {
        LocalDateTime startDateTime() {
            return start.atStartOfDay();
        }

        LocalDateTime endDateTimeExclusive() {
            return end.plusDays(1).atStartOfDay();
        }
    }

}

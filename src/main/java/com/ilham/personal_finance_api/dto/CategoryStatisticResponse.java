package com.ilham.personal_finance_api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryStatisticResponse {

    private PeriodResponse period;
    private BigDecimal totalExpense;
    private List<CategoryStatisticItemResponse> categories;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeriodResponse {
        private LocalDate startDate;
        // inclusive
        private LocalDate endDate;
    }

    @Getter
    @Setter
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryStatisticItemResponse {
        private UUID categoryId;
        private String categoryName;
        private BigDecimal totalAmount;
        private BigDecimal percentage;
        private Long transactionCount;

        // dipakai oleh JPQL constructor projection; percentage dihitung di service
        public CategoryStatisticItemResponse(UUID categoryId, String categoryName,
                BigDecimal totalAmount, Long transactionCount) {
            this(categoryId, categoryName, totalAmount, null, transactionCount);
        }
    }
}

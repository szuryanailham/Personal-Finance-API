package com.ilham.personal_finance_api.repository;

import org.springframework.data.jpa.domain.Specification;

import com.ilham.personal_finance_api.dto.TransactionFilter;
import com.ilham.personal_finance_api.entity.Transaction;
import com.ilham.personal_finance_api.entity.User;

public class TransactionSpecification {

    private TransactionSpecification() {
    }

    public static Specification<Transaction> filterBy(User user, TransactionFilter filter) {
        return (root, query, criteriaBuilder) -> {
            var predicate = criteriaBuilder.equal(root.get("user"), user);

            if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
                String pattern = "%" + filter.getSearch().toLowerCase() + "%";
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("transactionName")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), pattern)
                ));
            }

            if (filter.getDate() != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.between(
                    root.get("transactionDate"),
                    filter.getDate().atStartOfDay(),
                    filter.getDate().atTime(23, 59, 59)
                ));
            }

            if (filter.getType() != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(
                    root.get("category").get("type"),
                    filter.getType().name()
                ));
            }

            return predicate;
        };
    }
}

package com.ilham.personal_finance_api.dto;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;

@Data
@NoArgsConstructor
public abstract class CategoryRequest {

    @NotBlank(message = "Name of Category is required")
    @Size(max = 100, message = "Name of Category must not exceed 100 characters")
    private String name;

    @NotNull(message = "Type of Category is required")
    private TransactionType type;

    @Min(0)
    @Max(1_000_000_000_000L)
    private Long budget;

    @Pattern(regexp = "utensils|car|shopping-bag|house|ticket|heart-pulse|graduation-cap|briefcase|plane|smartphone|wallet|other",
            message = "Icon must be a supported category icon")
    private String icon;

    @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "Color must use the #RRGGBB format")
    private String color;

    @JsonSetter("budget")
    public void readBudget(JsonNode value) {
        if (value == null || value.isNull()) {
            budget = null;
            return;
        }
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new IllegalArgumentException("Budget must be an integer");
        }
        budget = value.longValue();
    }
}

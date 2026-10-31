package com.ilham.personal_finance_api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdateGuidanceRequest {
    @NotNull(message = "isGuidanceCompleted is required")
    @JsonProperty("isGuidanceCompleted")
    private Boolean guidanceCompleted;
}

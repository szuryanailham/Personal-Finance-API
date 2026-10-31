package com.ilham.personal_finance_api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GuidanceResponse {
    @JsonProperty("isGuidanceCompleted")
    private boolean guidanceCompleted;
}

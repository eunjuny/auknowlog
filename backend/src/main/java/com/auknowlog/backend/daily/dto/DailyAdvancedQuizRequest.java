package com.auknowlog.backend.daily.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DailyAdvancedQuizRequest(
        @NotBlank @Size(max = 255) String topic,
        @Min(1) @Max(20) int numberOfQuestions
) { }

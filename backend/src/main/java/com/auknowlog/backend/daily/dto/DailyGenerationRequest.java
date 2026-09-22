package com.auknowlog.backend.daily.dto;

import jakarta.validation.constraints.Size;

public record DailyGenerationRequest(@Size(max = 2048, message = "기사 URL은 2,048자 이하여야 합니다.") String articleUrl) { }

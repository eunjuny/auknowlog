package com.auknowlog.backend.common.web;

import com.auknowlog.backend.common.exception.GeminiOverloadedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GeminiOverloadedException.class)
    public ResponseEntity<Map<String, Object>> handleGeminiOverloaded(GeminiOverloadedException e) {
        // 프론트에서 메시지를 그대로 보여주기 좋게 JSON으로 반환
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "message", e.getMessage(),
                        "code", 503
                ));
    }
}



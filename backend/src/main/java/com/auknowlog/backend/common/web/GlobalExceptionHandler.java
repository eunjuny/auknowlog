package com.auknowlog.backend.common.web;

import com.auknowlog.backend.common.exception.OpenAiUnavailableException;
import com.auknowlog.backend.notification.RemoteAccessMailUnavailableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OpenAiUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleOpenAiUnavailable(OpenAiUnavailableException e) {
        // 프론트에서 메시지를 그대로 보여주기 좋게 JSON으로 반환
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "message", e.getMessage(),
                        "code", 503
                ));
    }

    @ExceptionHandler(RemoteAccessMailUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleRemoteAccessMailUnavailable(RemoteAccessMailUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("message", e.getMessage(), "code", 503));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRequest(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("요청값을 확인해주세요.");
        return ResponseEntity.badRequest().body(Map.of("message", message, "code", 400));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage(), "code", 400));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage(), "code", 404));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("message", "파일은 최대 10MB까지 업로드할 수 있습니다.", "code", 413));
    }
}

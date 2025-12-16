package com.auknowlog.backend.common.exception;

/**
 * Gemini API가 503(UNAVAILABLE / overloaded) 같은 일시 장애를 반환했을 때
 * 사용자에게 "잠시 후 재시도"를 명확히 전달하기 위한 전용 예외입니다.
 */
public class GeminiOverloadedException extends RuntimeException {

    public GeminiOverloadedException(String message) {
        super(message);
    }

    public GeminiOverloadedException(String message, Throwable cause) {
        super(message, cause);
    }
}



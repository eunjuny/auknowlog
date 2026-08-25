package com.auknowlog.backend.common.exception;

/** Raised when OpenAI returns a temporary error after bounded retries. */
public class OpenAiUnavailableException extends RuntimeException {

    public OpenAiUnavailableException(String message) {
        super(message);
    }

    public OpenAiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

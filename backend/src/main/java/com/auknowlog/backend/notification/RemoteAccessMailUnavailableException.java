package com.auknowlog.backend.notification;

/** SMTP 설정 누락 또는 메일 서버가 발송 요청을 거절했을 때의 안전한 오류 표현이다. */
public class RemoteAccessMailUnavailableException extends RuntimeException {

    public RemoteAccessMailUnavailableException(String message) {
        super(message);
    }

    public RemoteAccessMailUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.auknowlog.backend.ai.service;

@org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)
public class AiBudgetExceededException extends RuntimeException {
    public AiBudgetExceededException() { super("오늘의 AI 토큰 예산 또는 호출 횟수를 초과할 수 있어 차단했습니다. 내일 다시 시도하거나 관리자에게 문의해주세요."); }
}

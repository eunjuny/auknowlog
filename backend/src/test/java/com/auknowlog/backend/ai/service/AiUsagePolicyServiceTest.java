package com.auknowlog.backend.ai.service;

import com.auknowlog.backend.ai.entity.AiGenerationLog;
import com.auknowlog.backend.ai.repository.AiGenerationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiUsagePolicyServiceTest {

    private AiGenerationLogRepository repository;
    private AiUsagePolicyService service;

    @BeforeEach
    void setUp() {
        repository = mock(AiGenerationLogRepository.class);
        service = new AiUsagePolicyService(repository);
        ReflectionTestUtils.setField(service, "dailyTokenBudget", 100L);
        ReflectionTestUtils.setField(service, "enforceDailyTokenBudget", true);
    }

    @Test
    void blocksRequestBeforeModelCallWhenProjectedTokensExceedDailyBudget() {
        AiGenerationLog existing = mock(AiGenerationLog.class);
        when(existing.getTotalTokens()).thenReturn(80L);
        when(repository.findByCreatedAtGreaterThanEqual(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.assertWithinBudget("퀴즈 생성", "1234", 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("안전 예산을 초과");
    }

    @Test
    void exposesTodayActualTokensAndRemainingBudget() {
        AiGenerationLog existing = mock(AiGenerationLog.class);
        when(existing.getTotalTokens()).thenReturn(35L);
        when(repository.findByCreatedAtGreaterThanEqual(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(existing));

        var snapshot = service.snapshot();

        assertThat(snapshot.enforcementEnabled()).isTrue();
        assertThat(snapshot.dailyTokenBudget()).isEqualTo(100);
        assertThat(snapshot.todayTokens()).isEqualTo(35);
        assertThat(snapshot.remainingTokens()).isEqualTo(65);
        assertThat(snapshot.usedPercent()).isEqualTo(35);
    }

    @Test
    void estimatesPromptTokensConservativelyFromCharacterLength() {
        assertThat(AiUsagePolicyService.estimateTokens("12345")).isEqualTo(2);
        assertThat(AiUsagePolicyService.estimateTokens(" ")).isZero();
    }
}

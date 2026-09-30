package com.auknowlog.backend.ai.service;

import com.auknowlog.backend.ai.entity.AiGenerationLog;
import com.auknowlog.backend.ai.repository.AiGenerationLogRepository;
import com.auknowlog.backend.auth.service.CurrentUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.time.Duration;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class AiGenerationLedgerOwnershipTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void attributesAuthenticatedCalls() {
        var repo = mock(AiGenerationLogRepository.class);
        var user = mock(CurrentUserService.class);
        when(user.currentUserId()).thenReturn(42L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("learner", "unused", List.of()));
        new AiGenerationLedgerService(repo, user).recordQuizSuccess("test-model", null, Duration.ofMillis(5));
        var captor = org.mockito.ArgumentCaptor.forClass(AiGenerationLog.class);
        verify(repo).save(captor.capture());
        assertEquals(42L, captor.getValue().getOwnerId());
    }

    @Test void scheduledCallsRemainUnattributed() {
        var repo = mock(AiGenerationLogRepository.class);
        var user = mock(CurrentUserService.class);
        new AiGenerationLedgerService(repo, user).recordDailyLearningFailure("test-model", "timeout", Duration.ZERO);
        var captor = org.mockito.ArgumentCaptor.forClass(AiGenerationLog.class);
        verify(repo).save(captor.capture());
        assertNull(captor.getValue().getOwnerId());
        verifyNoInteractions(user);
    }
}

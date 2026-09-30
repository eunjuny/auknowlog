package com.auknowlog.backend.auth;

import com.auknowlog.backend.ai.service.*;
import com.auknowlog.backend.auth.config.SecurityConfig;
import com.auknowlog.backend.auth.service.CurrentUserService;
import com.auknowlog.backend.notification.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AiBudgetController.class,LearningNotificationController.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties="auknowlog.security.enabled=true")
class AccountOperationsAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockitoBean AiBudgetReservationService budgets;
    @MockitoBean CurrentUserService users;
    @MockitoBean JdbcTemplate jdbc;
    @MockitoBean LearningNotificationService notifications;
    @MockitoBean JwtDecoder decoder;
    @Test void personalSettingsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/account/notifications")).andExpect(status().isUnauthorized());
        verifyNoInteractions(notifications);
    }
    @Test void userCannotModifyAnotherUsersAiPolicy() throws Exception {
        mvc.perform(put("/api/admin/users/2/ai-budget").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .contentType("application/json").content("{\"tokenLimit\":20000,\"callLimit\":100}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(jdbc,budgets);
    }
    @Test void adminCanChangeBoundedPolicy() throws Exception {
        when(jdbc.update(anyString(),eq(20000L),eq(100),eq(2L))).thenReturn(1);
        when(budgets.snapshot(2L)).thenReturn(java.util.Map.of("tokenLimit",20000));
        mvc.perform(put("/api/admin/users/2/ai-budget").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType("application/json").content("{\"tokenLimit\":20000,\"callLimit\":100}"))
                .andExpect(status().isOk());
    }
    @Test void rejectsNegativeQuotasAndInvalidRecipientBeforeService() throws Exception {
        mvc.perform(put("/api/admin/users/2/ai-budget").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType("application/json").content("{\"tokenLimit\":-1,\"callLimit\":100}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/account/notifications").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .contentType("application/json").content("{\"email\":\"not-an-email\",\"sendHour\":8}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(jdbc,notifications);
    }
}

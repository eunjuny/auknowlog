package com.auknowlog.backend.auth;

import com.auknowlog.backend.admin.AdminMonitoringController;
import com.auknowlog.backend.ai.service.AiUsagePolicyService;
import com.auknowlog.backend.auth.config.SecurityConfig;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminMonitoringController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "auknowlog.security.enabled=true")
class AdminMonitoringAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockitoBean JdbcTemplate jdbc;
    @MockitoBean AiUsagePolicyService policy;
    @MockitoBean JwtDecoder decoder;

    @Test void anonymousCannotQueryGlobalData() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        verifyNoInteractions(jdbc);
    }
    @Test void userCannotQueryAnyAdminRoute() throws Exception {
        for (String path : new String[]{"/users", "/summary", "/records/attempts", "/records/ai", "/attempts/1"}) {
            mvc.perform(get("/api/admin" + path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(jdbc);
    }
    @Test void adminCanQueryUsersWithBoundedPagination() throws Exception {
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(java.util.List.of());
        mvc.perform(get("/api/admin/users?size=999&page=-1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("total").value(0));
        verify(jdbc).queryForList(anyString(), eq(100), eq(0));
    }
}

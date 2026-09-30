package com.auknowlog.backend.auth;

import com.auknowlog.backend.admin.AdminMonitoringController;
import com.auknowlog.backend.ai.service.AiUsagePolicyService;
import com.auknowlog.backend.auth.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verifyNoInteractions;

@WebMvcTest(AdminMonitoringController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "auknowlog.security.enabled=false")
class AdminMonitoringLocalModeTest {
    @Autowired MockMvc mvc;
    @MockitoBean JdbcTemplate jdbc;
    @MockitoBean AiUsagePolicyService policy;
    @Test void localModeNeverExposesGlobalAdminRecords() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isForbidden());
        verifyNoInteractions(jdbc);
    }
}

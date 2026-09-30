package com.auknowlog.backend.auth;

import com.auknowlog.backend.auth.config.SecurityConfig;
import com.auknowlog.backend.quality.controller.QualityEvaluationController;
import com.auknowlog.backend.quality.service.DuplicateEvaluationDatasetService;
import com.auknowlog.backend.quality.service.QualityEvaluationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QualityEvaluationController.class)
@Import(SecurityConfig.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = "auknowlog.security.enabled=true")
class SecurityAuthorizationTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean QualityEvaluationService qualityEvaluationService;
    @MockitoBean DuplicateEvaluationDatasetService duplicateEvaluationDatasetService;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/quality-evaluations/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsUserRoleFromAdminQualityApi() throws Exception {
        mockMvc.perform(get("/api/quality-evaluations/summary")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsAdminRoleToUseQualityApi() throws Exception {
        mockMvc.perform(get("/api/quality-evaluations/summary")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }
}

package com.auknowlog.backend.auth;

import com.auknowlog.backend.admin.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class AdminAuditFilterTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void deniedAdminRouteIsAuditedAs403Not200() {
        var audit=mock(AdminAuditService.class);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                Jwt.withTokenValue("fixture").header("alg","RS256").subject("fixture-sub").build()));
        var request=new MockHttpServletRequest("GET","/api/admin/users");
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> new AdminAuditFilter(audit)
                .doFilter(request,new MockHttpServletResponse(),(req,res) -> { throw new org.springframework.security.access.AccessDeniedException("fixture"); }));
        verify(audit).record("GET","/api/admin/users",null,403);
    }
    @Test void anonymousAndPersonalRoutesAreNotRecorded() throws Exception {
        var audit=mock(AdminAuditService.class);
        new AdminAuditFilter(audit).doFilter(new MockHttpServletRequest("GET","/api/admin/users"),new MockHttpServletResponse(),(req,res)->{});
        verifyNoInteractions(audit);
    }
}

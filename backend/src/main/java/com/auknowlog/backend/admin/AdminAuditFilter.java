package com.auknowlog.backend.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

public class AdminAuditFilter extends OncePerRequestFilter {
    private final AdminAuditService audit;
    public AdminAuditFilter(AdminAuditService audit) { this.audit = audit; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        int outcome = 500;
        try { chain.doFilter(request, response); outcome = response.getStatus(); }
        catch (org.springframework.security.access.AccessDeniedException denied) { outcome = 403; throw denied; }
        finally {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (request.getRequestURI().startsWith("/api/admin/") && auth != null && auth.getPrincipal() instanceof Jwt) {
                try { audit.record(request.getMethod(), request.getRequestURI(), request.getParameter("userId"), outcome); }
                catch (RuntimeException ignored) { logger.error("Administrator audit persistence failed; investigate database health."); }
            }
        }
    }
}

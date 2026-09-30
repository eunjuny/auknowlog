package com.auknowlog.backend.auth.service;

import com.auknowlog.backend.auth.entity.AppUser;
import com.auknowlog.backend.auth.repository.AppUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    private final AppUserRepository repository;
    private final AppUserProvisioningService provisioningService;

    public CurrentUserService(AppUserRepository repository, AppUserProvisioningService provisioningService) {
        this.repository = repository;
        this.provisioningService = provisioningService;
    }

    public AppUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String subject = jwt.getSubject();
            String username = value(jwt, "preferred_username", subject);
            return repository.findByKeycloakSubject(subject)
                    .orElseGet(() -> provisioningService.provision(subject, username,
                            value(jwt, "name", username), jwt.getClaimAsString("email")));
        }
        return repository.findByUsername("eunjuny")
                .orElseThrow(() -> new IllegalStateException("로컬 개발 사용자가 준비되지 않았습니다."));
    }

    public Long currentUserId() { return currentUser().getId(); }

    private String value(Jwt jwt, String claim, String fallback) {
        String value = jwt.getClaimAsString(claim);
        return value == null || value.isBlank() ? fallback : value;
    }
}

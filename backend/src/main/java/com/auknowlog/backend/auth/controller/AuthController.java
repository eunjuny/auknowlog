package com.auknowlog.backend.auth.controller;

import com.auknowlog.backend.auth.dto.CurrentUserResponse;
import com.auknowlog.backend.auth.entity.AppUser;
import com.auknowlog.backend.auth.service.CurrentUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final CurrentUserService currentUserService;
    private final boolean securityEnabled;

    public AuthController(CurrentUserService currentUserService,
                          @Value("${auknowlog.security.enabled:false}") boolean securityEnabled) {
        this.currentUserService = currentUserService;
        this.securityEnabled = securityEnabled;
    }

    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {
        AppUser user = currentUserService.currentUser();
        Set<String> roles = authentication == null ? Set.of("USER") : authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                .collect(Collectors.toUnmodifiableSet());
        return new CurrentUserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(), roles);
    }

    @GetMapping("/mode")
    public java.util.Map<String, Boolean> mode() {
        return java.util.Map.of("enabled", securityEnabled);
    }
}

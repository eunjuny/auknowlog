package com.auknowlog.backend.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${auknowlog.security.enabled:false}") boolean enabled,
            org.springframework.beans.factory.ObjectProvider<com.auknowlog.backend.admin.AdminAuditFilter> audit) throws Exception {
        http.csrf(csrf -> csrf.disable());
        if (!enabled) {
            return http.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/admin/**").denyAll()
                    .anyRequest().permitAll()).build();
        }
        if (audit.getIfAvailable() != null) {
            http.addFilterBefore(audit.getObject(), org.springframework.security.web.access.intercept.AuthorizationFilter.class);
        }
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                        .requestMatchers("/api/notifications/remote-access/email").permitAll()
                        .requestMatchers("/api/quality-evaluations/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").hasAnyRole("USER", "ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resource -> resource.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(keycloakRoleConverter())))
                .build();
    }

    private Converter<Jwt, ? extends AbstractAuthenticationToken> keycloakRoleConverter() {
        return jwt -> new JwtAuthenticationToken(jwt, realmRoles(jwt), username(jwt));
    }

    private Collection<GrantedAuthority> realmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(String::valueOf)
                .filter(role -> role.equals("USER") || role.equals("ADMIN"))
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }

    private String username(Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        return username == null || username.isBlank() ? jwt.getSubject() : username;
    }
}

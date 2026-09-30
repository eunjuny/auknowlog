package com.auknowlog.backend.auth.dto;

import java.util.Set;

public record CurrentUserResponse(Long id, String username, String displayName, String email, Set<String> roles) {}

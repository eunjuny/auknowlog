package com.auknowlog.backend.auth.repository;

import com.auknowlog.backend.auth.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByKeycloakSubject(String keycloakSubject);
    Optional<AppUser> findByUsername(String username);
}

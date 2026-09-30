package com.auknowlog.backend.auth.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_user")
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "keycloak_subject", nullable = false, unique = true, length = 128)
    private String keycloakSubject;
    @Column(nullable = false, unique = true, length = 128)
    private String username;
    @Column(name = "display_name") private String displayName;
    @Column(length = 320) private String email;
    @Column(nullable = false) private boolean active;
    @Column(nullable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;

    protected AppUser() {}

    public AppUser(String subject, String username, String displayName, String email) {
        this.keycloakSubject = subject;
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.active = true;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void synchronize(String subject, String displayName, String email) {
        this.keycloakSubject = subject;
        this.displayName = displayName;
        this.email = email;
        this.active = true;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getKeycloakSubject() { return keycloakSubject; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public boolean isActive() { return active; }
}

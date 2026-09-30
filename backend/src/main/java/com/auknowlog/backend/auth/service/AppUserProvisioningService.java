package com.auknowlog.backend.auth.service;

import com.auknowlog.backend.auth.entity.AppUser;
import com.auknowlog.backend.auth.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserProvisioningService {
    private final AppUserRepository repository;
    public AppUserProvisioningService(AppUserRepository repository) { this.repository = repository; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AppUser provision(String subject, String username, String displayName, String email) {
        return repository.findByKeycloakSubject(subject).map(user -> {
            user.synchronize(subject, displayName, email);
            return user;
        }).orElseGet(() -> repository.findByUsername(username).map(user -> {
            if (!user.getKeycloakSubject().startsWith("local-")) {
                throw new IllegalStateException("이미 다른 인증 계정에 연결된 사용자명입니다.");
            }
            user.synchronize(subject, displayName, email);
            return user;
        }).orElseGet(() -> repository.save(new AppUser(subject, username, displayName, email))));
    }
}

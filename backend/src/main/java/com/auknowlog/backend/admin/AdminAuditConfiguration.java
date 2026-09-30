package com.auknowlog.backend.admin;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

@Configuration
public class AdminAuditConfiguration {
    @Bean AdminAuditFilter adminAuditFilter(AdminAuditService service) { return new AdminAuditFilter(service); }
    @Bean FilterRegistrationBean<AdminAuditFilter> disableServletAuditRegistration(AdminAuditFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false); // Run only inside the security chain, with a verified principal.
        return registration;
    }
}

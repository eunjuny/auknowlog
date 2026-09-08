package com.auknowlog.backend.dashboard.controller;

import com.auknowlog.backend.dashboard.dto.DashboardSummary;
import com.auknowlog.backend.dashboard.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public DashboardSummary summary() {
        return dashboardService.getSummary();
    }
}

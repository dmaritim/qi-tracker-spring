package com.qitracker.controller;

import com.qitracker.dto.DashboardDtos.DashboardResponse;
import com.qitracker.security.CurrentUser;
import com.qitracker.service.DashboardService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    // Open to any signed-in user, per the "view dashboard of any other project" requirement.
    @GetMapping("/api/projects/{projectId}/dashboard")
    public DashboardResponse dashboard(@PathVariable Long projectId, Authentication authentication) {
        return dashboardService.build(projectId, CurrentUser.get(authentication));
    }
}

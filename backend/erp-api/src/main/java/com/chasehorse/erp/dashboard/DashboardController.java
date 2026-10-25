package com.chasehorse.erp.dashboard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chasehorse.erp.security.Actor;
import com.chasehorse.erp.security.CurrentActor;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/stats")
    public DashboardService.Stats stats(@CurrentActor Actor actor) {
        return dashboard.stats(actor);
    }
}

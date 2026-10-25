package com.chasehorse.erp.integration;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chasehorse.erp.security.Actor;
import com.chasehorse.erp.security.CurrentActor;

@RestController
@RequestMapping("/api/v1/integrations")
public class IntegrationController {
    public record Status(String key, String name, String status) {
    }

    private final List<ExternalIntegration> integrations;

    public IntegrationController(List<ExternalIntegration> integrations) {
        this.integrations = integrations;
    }

    @GetMapping
    public List<Status> list(@CurrentActor Actor actor) {
        actor.requireStaff();
        return integrations.stream().map(i -> new Status(i.key(), i.displayName(), i.status())).toList();
    }
}

package com.chasehorse.erp.evidence;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.chasehorse.erp.evidence.EvidenceDtos.*;
import com.chasehorse.erp.security.Actor;
import com.chasehorse.erp.security.CurrentActor;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class EvidenceController {
    private final EvidenceService evidence;

    public EvidenceController(EvidenceService evidence) {
        this.evidence = evidence;
    }

    @PostMapping("/tasks/{taskId}/evidence")
    @ResponseStatus(HttpStatus.CREATED)
    public BeginUploadResponse begin(@CurrentActor Actor actor, @PathVariable UUID taskId, @Valid @RequestBody BeginUploadRequest body) {
        return evidence.begin(actor, taskId, body);
    }

    @PostMapping("/evidence/{evidenceId}/confirmation")
    public Evidence confirm(@CurrentActor Actor actor, @PathVariable UUID evidenceId) {
        return evidence.confirm(actor, evidenceId);
    }

    @GetMapping("/tasks/{taskId}/evidence")
    public List<Evidence> forTask(@CurrentActor Actor actor, @PathVariable UUID taskId) {
        return evidence.forTask(actor, taskId);
    }

    @GetMapping("/evidence/{evidenceId}/url")
    public SignedUrl url(@CurrentActor Actor actor, @PathVariable UUID evidenceId) {
        return evidence.downloadUrl(actor, evidenceId);
    }
}

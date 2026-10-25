package com.chasehorse.erp.checklist;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.chasehorse.erp.checklist.ChecklistDtos.*;
import com.chasehorse.erp.security.Actor;
import com.chasehorse.erp.security.CurrentActor;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class ChecklistController {
    private final ChecklistService checklists;

    public ChecklistController(ChecklistService checklists) {
        this.checklists = checklists;
    }

    @GetMapping("/checklist-templates")
    public List<Template> list(@CurrentActor Actor actor) {
        return checklists.list(actor);
    }

    @GetMapping("/checklist-templates/{id}")
    public Template get(@CurrentActor Actor actor, @PathVariable UUID id) {
        return checklists.get(actor, id);
    }

    @PostMapping("/checklist-templates")
    @ResponseStatus(HttpStatus.CREATED)
    public Template create(@CurrentActor Actor actor, @Valid @RequestBody SaveTemplateRequest body) {
        return checklists.save(actor, null, body);
    }

    /** Creates a NEW immutable version; tasks already created keep the version they were created with. */
    @PutMapping("/checklist-templates/{id}")
    public Template update(@CurrentActor Actor actor, @PathVariable UUID id, @Valid @RequestBody SaveTemplateRequest body) {
        return checklists.save(actor, id, body);
    }

    @GetMapping("/tasks/{taskId}/checklist")
    public List<TaskChecklistItem> forTask(@CurrentActor Actor actor, @PathVariable UUID taskId) {
        return checklists.forTask(actor, taskId);
    }

    @PutMapping("/tasks/{taskId}/checklist/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void respond(@CurrentActor Actor actor, @PathVariable UUID taskId, @PathVariable UUID itemId,
                        @Valid @RequestBody ResponseRequest body) {
        checklists.respond(actor, taskId, itemId, body);
    }
}

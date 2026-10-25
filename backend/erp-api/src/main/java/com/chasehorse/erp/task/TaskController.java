package com.chasehorse.erp.task;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.chasehorse.erp.common.Paging;
import com.chasehorse.erp.config.AppProperties;
import com.chasehorse.erp.security.Actor;
import com.chasehorse.erp.security.CurrentActor;
import com.chasehorse.erp.task.TaskDtos.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {
    private final TaskService tasks;
    private final int maxPage;

    public TaskController(TaskService tasks, AppProperties props) {
        this.tasks = tasks;
        this.maxPage = props.limits() == null || props.limits().maxPageSize() <= 0 ? 200 : props.limits().maxPageSize();
    }

    @GetMapping
    public List<TaskDto> list(@CurrentActor Actor actor, @RequestParam(required = false) String status,
                              @RequestParam(required = false) UUID customerOrgId,
                              @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return tasks.list(actor, status, customerOrgId, Paging.of(page, size, maxPage));
    }

    @GetMapping("/mine")
    public List<TaskDto> mine(@CurrentActor Actor actor, @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "50") int size) {
        return tasks.mine(actor, Paging.of(page, size, maxPage));
    }

    @GetMapping("/nearby")
    public List<NearbyTask> nearby(@CurrentActor Actor actor, @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "50") int size) {
        return tasks.nearby(actor, Paging.of(page, size, maxPage));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskDto create(@CurrentActor Actor actor, @Valid @RequestBody CreateTaskRequest body) {
        return tasks.create(actor, body);
    }

    @GetMapping("/{id}")
    public TaskDto get(@CurrentActor Actor actor, @PathVariable UUID id) {
        return tasks.get(actor, id);
    }

    @GetMapping("/{id}/history")
    public List<HistoryEntry> history(@CurrentActor Actor actor, @PathVariable UUID id) {
        return tasks.history(actor, id);
    }

    @PutMapping("/{id}/reward")
    public TaskDto reward(@CurrentActor Actor actor, @PathVariable UUID id, @Valid @RequestBody RewardRequest body) {
        return tasks.setReward(actor, id, body.rewardPaise());
    }

    @PostMapping("/{id}/assignment")
    public TaskDto assign(@CurrentActor Actor actor, @PathVariable UUID id, @Valid @RequestBody AssignRequest body) {
        return tasks.assign(actor, id, body.employeeId());
    }

    @PostMapping("/{id}/cancellation")
    public TaskDto cancel(@CurrentActor Actor actor, @PathVariable UUID id, @Valid @RequestBody ReasonRequest body) {
        return tasks.cancel(actor, id, body.reason());
    }

    @PostMapping("/{id}/acceptance")
    public TaskDto accept(@CurrentActor Actor actor, @PathVariable UUID id) {
        return tasks.accept(actor, id);
    }

    @PostMapping("/{id}/rejection")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@CurrentActor Actor actor, @PathVariable UUID id, @Valid @RequestBody(required = false) OptionalReasonRequest body) {
        tasks.rejectOffer(actor, id, body == null ? null : body.reason());
    }

    @PostMapping("/{id}/start")
    public TaskDto start(@CurrentActor Actor actor, @PathVariable UUID id) {
        return tasks.start(actor, id);
    }

    @PostMapping("/{id}/submission")
    public SubmissionResult submit(@CurrentActor Actor actor, @PathVariable UUID id) {
        return tasks.submit(actor, id);
    }
}

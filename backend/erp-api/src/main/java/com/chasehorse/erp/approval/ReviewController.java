package com.chasehorse.erp.approval;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

import com.chasehorse.erp.approval.ReviewDtos.*;
import com.chasehorse.erp.common.Paging;
import com.chasehorse.erp.config.AppProperties;
import com.chasehorse.erp.security.Actor;
import com.chasehorse.erp.security.CurrentActor;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class ReviewController {
    private final ReviewService reviews;
    private final int maxPage;

    public ReviewController(ReviewService reviews, AppProperties props) {
        this.reviews = reviews;
        this.maxPage = props.limits() == null || props.limits().maxPageSize() <= 0 ? 200 : props.limits().maxPageSize();
    }

    @GetMapping("/reviews")
    public List<ReviewSummary> queue(@CurrentActor Actor actor, @RequestParam String stage,
                                     @RequestParam(defaultValue = "pending") String state,
                                     @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return reviews.queue(actor, stage, state, Paging.of(page, size, maxPage));
    }

    @GetMapping("/tasks/{taskId}/review")
    public ReviewPackage reviewPackage(@CurrentActor Actor actor, @PathVariable UUID taskId) {
        return reviews.reviewPackage(actor, taskId);
    }

    @PostMapping("/tasks/{taskId}/decisions")
    public DecisionResult decide(@CurrentActor Actor actor, @PathVariable UUID taskId, @Valid @RequestBody DecisionRequest body) {
        return reviews.decide(actor, taskId, body);
    }

    @PostMapping("/tasks/{taskId}/ai-verification")
    public AiRequestResult requestAi(@CurrentActor Actor actor, @PathVariable UUID taskId) {
        return reviews.requestAi(actor, taskId);
    }
}

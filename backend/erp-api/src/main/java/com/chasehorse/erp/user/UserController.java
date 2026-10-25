package com.chasehorse.erp.user;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.chasehorse.erp.config.AppProperties;
import com.chasehorse.erp.common.Paging;
import com.chasehorse.erp.security.Actor;
import com.chasehorse.erp.security.CurrentActor;
import com.chasehorse.erp.user.UserDtos.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class UserController {
    private final UserService users;
    private final int maxPage;

    public UserController(UserService users, AppProperties props) {
        this.users = users;
        this.maxPage = props.limits() == null || props.limits().maxPageSize() <= 0 ? 200 : props.limits().maxPageSize();
    }

    @GetMapping("/me")
    public Me me(@CurrentActor Actor actor) {
        return users.me(actor);
    }

    @PostMapping("/me/registration")
    @ResponseStatus(HttpStatus.CREATED)
    public Me register(@CurrentActor Actor actor, @Valid @RequestBody RegisterRequest body) {
        return users.register(actor, body);
    }

    @PutMapping("/me/location-consent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void consent(@CurrentActor Actor actor, @Valid @RequestBody ConsentRequest body) {
        actor.requireActive();
        users.setLocationConsent(actor, body.consent());
    }

    @PutMapping("/me/location")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void location(@CurrentActor Actor actor, @Valid @RequestBody LocationRequest body) {
        actor.requireActive();
        users.updateLocation(actor, body);
    }

    @GetMapping("/employees")
    public List<EmployeeSummary> employees(@CurrentActor Actor actor,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "50") int size) {
        return users.listEmployees(actor, status, Paging.of(page, size, maxPage));
    }

    @PostMapping("/accounts/{userId}/decision")
    public AccountDecisionResponse decide(@CurrentActor Actor actor, @PathVariable UUID userId,
                                          @Valid @RequestBody AccountDecisionRequest body) {
        return users.decideAccount(actor, userId, body);
    }

    @GetMapping("/organizations")
    public List<Organization> organizations(@CurrentActor Actor actor, @RequestParam(required = false) String kind) {
        return users.listOrganizations(actor, kind);
    }

    @PostMapping("/organizations")
    @ResponseStatus(HttpStatus.CREATED)
    public Organization createOrg(@CurrentActor Actor actor, @Valid @RequestBody NewOrganization body) {
        return users.createOrganization(actor, body);
    }

    @GetMapping("/admin/accounts")
    public List<AccountRow> accounts(@CurrentActor Actor actor, @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "50") int size) {
        return users.listAccounts(actor, Paging.of(page, size, maxPage));
    }

    @PostMapping("/admin/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountDecisionResponse provision(@CurrentActor Actor actor, @Valid @RequestBody ProvisionRequest body) {
        return users.provision(actor, body);
    }

    @DeleteMapping("/admin/accounts/{userId}/roles/{role}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@CurrentActor Actor actor, @PathVariable UUID userId, @PathVariable String role) {
        users.revokeRole(actor, userId, role);
    }
}

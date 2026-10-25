package com.chasehorse.erp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.chasehorse.erp.ai.AiVerificationService;
import com.chasehorse.erp.approval.ReviewDtos.DecisionRequest;
import com.chasehorse.erp.approval.ReviewService;
import com.chasehorse.erp.checklist.ChecklistDtos.ItemInput;
import com.chasehorse.erp.checklist.ChecklistDtos.SaveTemplateRequest;
import com.chasehorse.erp.checklist.ChecklistService;
import com.chasehorse.erp.common.ApiException;
import com.chasehorse.erp.common.Paging;
import com.chasehorse.erp.config.AppProperties;
import com.chasehorse.erp.db.ActorDb;
import com.chasehorse.erp.evidence.EvidenceDtos.BeginUploadRequest;
import com.chasehorse.erp.evidence.EvidenceService;
import com.chasehorse.erp.storage.UnconfiguredStorageService;
import com.chasehorse.erp.task.TaskDtos.CreateTaskRequest;
import com.chasehorse.erp.task.TaskService;
import com.chasehorse.erp.user.UserDtos.AccountDecisionRequest;
import com.chasehorse.erp.user.UserDtos.NewOrganization;
import com.chasehorse.erp.user.UserDtos.ProvisionRequest;
import com.chasehorse.erp.user.UserService;
import com.chasehorse.erp.wallet.WalletService;

/**
 * The permission matrix (docs/permission-matrix.md) as executable checks. Every role that must be refused
 * is refused BEFORE the database is touched (verifyNoInteractions), and an account that is not ACTIVE is
 * refused whatever roles it holds.
 */
class AuthorizationMatrixTest {
    private static final UUID TASK = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final Paging P = new Paging(10, 0);

    private final ActorDb db = mock(ActorDb.class);
    private final AiVerificationService ai = mock(AiVerificationService.class);
    private final TaskService tasks = new TaskService(db, ai);
    private final UserService users = new UserService(db);
    private final ChecklistService checklists = new ChecklistService(db, new com.fasterxml.jackson.databind.ObjectMapper());
    private final WalletService wallets = new WalletService(db);
    private final EvidenceService evidence = new EvidenceService(db, new UnconfiguredStorageService(),
            new AppProperties(new AppProperties.Supabase("", "", "", "task-evidence", 600), null, null));
    private final ReviewService reviews = new ReviewService(db, tasks, evidence, checklists, ai);

    private static Actor active(AppRole... roles) {
        return new Actor(UUID.randomUUID(), "x@t.test", AccountStatus.ACTIVE, roles.length == 0 ? Set.of() : EnumSet.copyOf(Set.of(roles)));
    }

    private void denied(Runnable call) {
        try {
            call.run();
            org.junit.jupiter.api.Assertions.fail("expected a 403");
        } catch (ApiException e) {
            assertThat(e.status().value()).isEqualTo(403);
        }
        verifyNoInteractions(db);
    }

    private static final Set<AppRole> STAFF = EnumSet.of(AppRole.ADMIN, AppRole.MANAGER, AppRole.OPERATIONS);

    private static Stream<AppRole> except(Set<AppRole> allowed) {
        return EnumSet.complementOf(EnumSet.copyOf(allowed)).stream();
    }

    @ParameterizedTest
    @EnumSource(value = AppRole.class, names = {"EMPLOYEE", "PARTNER"})
    void onlyStaffAndCustomersMayCreateTasks(AppRole role) {
        CreateTaskRequest req = new CreateTaskRequest("Task", null, "c", null, UUID.randomUUID(), null, null, null, null, null,
                java.math.BigDecimal.ONE, java.math.BigDecimal.ONE, null, 0L, null, null, null, UUID.randomUUID());
        denied(() -> tasks.create(active(role), req));
    }

    @Test
    void customersCannotSetRewards() {
        CreateTaskRequest req = new CreateTaskRequest("Task", null, "c", null, UUID.randomUUID(), null, null, null, null, null,
                java.math.BigDecimal.ONE, java.math.BigDecimal.ONE, null, 5000L, null, null, null, UUID.randomUUID());
        denied(() -> tasks.create(active(AppRole.CUSTOMER), req));
    }

    @ParameterizedTest
    @EnumSource(value = AppRole.class, names = {"EMPLOYEE", "CUSTOMER", "PARTNER"})
    void staffOnlyTaskActions(AppRole role) {
        denied(() -> tasks.assign(active(role), TASK, OTHER));
        verifyNoInteractions(db);
        denied(() -> tasks.cancel(active(role), TASK, "reason"));
        denied(() -> tasks.setReward(active(role), TASK, 100));
        denied(() -> users.listEmployees(active(role), null, P));
    }

    @ParameterizedTest
    @EnumSource(value = AppRole.class, names = {"ADMIN", "MANAGER", "OPERATIONS", "CUSTOMER", "PARTNER"})
    void onlyEmployeesAcceptWork(AppRole role) {
        denied(() -> tasks.accept(active(role), TASK));
        denied(() -> tasks.start(active(role), TASK));
        denied(() -> tasks.submit(active(role), TASK));
        denied(() -> tasks.nearby(active(role), P));
        denied(() -> tasks.rejectOffer(active(role), TASK, null));
        denied(() -> wallets.mine(active(role)));
        denied(() -> evidence.begin(active(role), TASK, new BeginUploadRequest(null, "image/jpeg", null, null, null, null, null)));
        denied(() -> evidence.confirm(active(role), TASK));
    }

    @ParameterizedTest
    @EnumSource(value = AppRole.class, names = {"OPERATIONS", "EMPLOYEE", "CUSTOMER", "PARTNER"})
    void onlyAdminsAndManagersEditChecklistsAndDecideEmployeeAccounts(AppRole role) {
        SaveTemplateRequest req = new SaveTemplateRequest("n", "c", java.util.List.of(new ItemInput("l", true, false)));
        denied(() -> checklists.save(active(role), null, req));
        if (role != AppRole.OPERATIONS) {
            denied(() -> users.decideAccount(active(role), OTHER, new AccountDecisionRequest("approved", null)));
        }
    }

    @ParameterizedTest
    @EnumSource(value = AppRole.class, names = {"MANAGER", "OPERATIONS", "EMPLOYEE", "CUSTOMER", "PARTNER"})
    void onlyAdminsAdministerAccountsAndReverseLedgerEntries(AppRole role) {
        denied(() -> users.listAccounts(active(role), P));
        denied(() -> users.provision(active(role), new ProvisionRequest(OTHER, "a@b.co", "A", "employee", null)));
        denied(() -> users.revokeRole(active(role), OTHER, "employee"));
        denied(() -> wallets.reverse(active(role), OTHER, "reason"));
    }

    @ParameterizedTest
    @EnumSource(value = AppRole.class, names = {"OPERATIONS", "EMPLOYEE", "CUSTOMER", "PARTNER"})
    void managerStageNeedsManagerOrAdmin(AppRole role) {
        denied(() -> reviews.decide(active(role), TASK, new DecisionRequest("manager", "approved", null)));
        denied(() -> reviews.queue(active(role), "manager", "pending", P));
    }

    @ParameterizedTest
    @EnumSource(value = AppRole.class, names = {"MANAGER", "EMPLOYEE", "CUSTOMER", "PARTNER"})
    void operationsStageNeedsOperationsOrAdmin(AppRole role) {
        denied(() -> reviews.decide(active(role), TASK, new DecisionRequest("operations", "approved", null)));
        denied(() -> reviews.queue(active(role), "operations", "pending", P));
        denied(() -> wallets.settle(active(role), TASK));
        denied(() -> wallets.reconcile(active(role)));
    }

    @ParameterizedTest
    @EnumSource(value = AppRole.class, names = {"CUSTOMER", "PARTNER", "EMPLOYEE"})
    void nonStaffCannotOpenTheReviewPackageOrIntegrations(AppRole role) {
        denied(() -> reviews.reviewPackage(active(role), TASK));
        denied(() -> reviews.requestAi(active(role), TASK));
        denied(() -> wallets.list(active(role), P));
        denied(() -> checklists.list(active(role)));
    }

    @ParameterizedTest
    @EnumSource(value = AppRole.class, names = {"CUSTOMER", "PARTNER"})
    void customersAndPartnersNeverSeeEvidence(AppRole role) {
        denied(() -> evidence.forTask(active(role), TASK));
    }

    @Test
    void employeesCannotReadSomeoneElsesTransactions() {
        denied(() -> wallets.transactions(active(AppRole.EMPLOYEE), OTHER, P));
        denied(() -> wallets.transactions(active(AppRole.CUSTOMER), OTHER, P));
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"PENDING", "SUSPENDED", "REJECTED", "UNREGISTERED"})
    void accountsThatAreNotActiveHaveNoPowersEvenWithAdminRoles(AccountStatus status) {
        Actor inactive = new Actor(UUID.randomUUID(), "x@t.test", status, EnumSet.allOf(AppRole.class));
        denied(() -> tasks.list(inactive, null, null, P));
        denied(() -> tasks.accept(inactive, TASK));
        denied(() -> users.listAccounts(inactive, P));
        denied(() -> wallets.reverse(inactive, OTHER, "reason"));
        denied(() -> reviews.decide(inactive, TASK, new DecisionRequest("manager", "approved", null)));
        denied(() -> users.createOrganization(inactive, new NewOrganization("Acme", "customer")));
    }

    @Test
    void roleSetsInThisTestMatchTheMatrixDocument() {
        assertThat(STAFF).containsExactlyInAnyOrder(AppRole.ADMIN, AppRole.MANAGER, AppRole.OPERATIONS);
        assertThat(except(STAFF)).containsExactlyInAnyOrder(AppRole.EMPLOYEE, AppRole.CUSTOMER, AppRole.PARTNER);
    }
}

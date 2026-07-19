package com.triobase.service.workflow.service;

import com.triobase.common.dto.authz.AuthzGuardResult;
import com.triobase.service.workflow.entity.ProcessInstance;
import com.triobase.service.workflow.entity.Task;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardResultComposerTest {

    private final GuardResultComposer composer = new GuardResultComposer();

    @Test
    void pendingStatusPassesAndProcessedTaskReturnsDocumentStatusGuard() {
        Task pending = task("PENDING");
        Task approved = task("APPROVED");

        assertNull(composer.checkPendingTask(pending));
        AuthzGuardResult guard = composer.checkPendingTask(approved);

        assertEquals(GuardResultComposer.GUARD_DOCUMENT_STATUS, guard.getGuardCode());
        assertEquals("TASK_ALREADY_PROCESSED", guard.getReasonCode());
        assertFalse(guard.isAllowed());
    }

    @Test
    void claimedByAnotherUserReturnsWorkflowCandidateGuard() {
        Task task = task("PENDING");
        task.setAssigneeId("user-2");

        AuthzGuardResult guard = composer.checkClaimedByAnotherUser(task, "user-1");

        assertEquals(GuardResultComposer.GUARD_WORKFLOW_CANDIDATE, guard.getGuardCode());
        assertEquals("TASK_CLAIMED_BY_ANOTHER_USER", guard.getReasonCode());
        assertFalse(guard.isAllowed());
    }

    @Test
    void selfApprovalReturnsNoSelfApprovalGuard() {
        ProcessInstance processInstance = new ProcessInstance();
        processInstance.setInitiatorId("user-1");

        AuthzGuardResult guard = composer.checkNoSelfApproval(task("PENDING"), processInstance, "user-1");

        assertEquals(GuardResultComposer.GUARD_NO_SELF_APPROVAL, guard.getGuardCode());
        assertEquals("SELF_APPROVAL_DENIED", guard.getReasonCode());
        assertFalse(guard.isAllowed());
    }

    @Test
    void composeFiltersNullAndDetectsDeniedGuard() {
        AuthzGuardResult allowed = composer.allowed("DOCUMENT_STATUS");
        AuthzGuardResult denied = composer.notCandidate(task("PENDING"), "user-1");

        List<AuthzGuardResult> results = composer.compose(null, allowed, denied);

        assertThat(results).containsExactly(allowed, denied);
        assertTrue(composer.hasDeniedGuard(results));
        assertFalse(composer.hasDeniedGuard(List.of(allowed)));
    }

    private Task task(String status) {
        Task task = new Task();
        task.setId("task-1");
        task.setStatus(status);
        return task;
    }
}

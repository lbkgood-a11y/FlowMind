package com.triobase.service.workflow.service;

import com.triobase.common.dto.authz.AuthzGuardResult;
import com.triobase.service.workflow.entity.ProcessInstance;
import com.triobase.service.workflow.entity.Task;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GuardResultComposer {

    public static final String GUARD_NO_SELF_APPROVAL = "NO_SELF_APPROVAL";
    public static final String GUARD_WORKFLOW_CANDIDATE = "WORKFLOW_CANDIDATE";
    public static final String GUARD_DOCUMENT_STATUS = "DOCUMENT_STATUS";

    public AuthzGuardResult checkPendingTask(Task task) {
        if (task == null || "PENDING".equals(task.getStatus())) {
            return null;
        }
        return denied(GUARD_DOCUMENT_STATUS, "TASK_ALREADY_PROCESSED", "任务已处理，不能重复操作");
    }

    public AuthzGuardResult checkClaimedByAnotherUser(Task task, String operatorId) {
        if (task == null || !StringUtils.hasText(task.getAssigneeId()) || operatorId == null
                || operatorId.equals(task.getAssigneeId())) {
            return null;
        }
        return denied(GUARD_WORKFLOW_CANDIDATE, "TASK_CLAIMED_BY_ANOTHER_USER", "任务已被其他用户认领");
    }

    public AuthzGuardResult notCandidate(Task task, String operatorId) {
        return denied(GUARD_WORKFLOW_CANDIDATE, "TASK_NOT_CANDIDATE", "当前用户不是该任务候选人或处理人");
    }

    public AuthzGuardResult checkNoSelfApproval(Task task, ProcessInstance processInstance, String operatorId) {
        if (task == null || processInstance == null || operatorId == null) {
            return null;
        }
        if (operatorId.equals(processInstance.getInitiatorId())) {
            return denied(GUARD_NO_SELF_APPROVAL, "SELF_APPROVAL_DENIED", "流程发起人不可审批自己的单据");
        }
        return null;
    }

    public AuthzGuardResult checkWorkflowCandidate(Task task, String operatorId) {
        return checkClaimedByAnotherUser(task, operatorId);
    }

    public AuthzGuardResult denied(String guardCode, String reasonCode, String reasonMessage) {
        AuthzGuardResult result = new AuthzGuardResult();
        result.setGuardCode(guardCode);
        result.setAllowed(false);
        result.setReasonCode(reasonCode);
        result.setReasonMessage(reasonMessage);
        return result;
    }

    public AuthzGuardResult allowed(String guardCode) {
        if (!StringUtils.hasText(guardCode)) {
            return null;
        }
        AuthzGuardResult result = new AuthzGuardResult();
        result.setGuardCode(guardCode);
        result.setAllowed(true);
        return result;
    }

    public List<AuthzGuardResult> compose(AuthzGuardResult... results) {
        List<AuthzGuardResult> list = new ArrayList<>();
        for (AuthzGuardResult result : results) {
            if (result != null) {
                list.add(result);
            }
        }
        return list;
    }

    public boolean hasDeniedGuard(List<AuthzGuardResult> results) {
        if (results == null || results.isEmpty()) {
            return false;
        }
        return results.stream().anyMatch(r -> !r.isAllowed());
    }
}

package com.triobase.common.action.runtime;

import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.enums.ActionStatus;

import java.util.Map;
import java.util.Set;

public final class ActionStatusMachine {

    private static final Map<ActionStatus, Set<ActionStatus>> ALLOWED_TRANSITIONS = Map.of(
            ActionStatus.CREATED, Set.of(ActionStatus.VALIDATING, ActionStatus.REJECTED, ActionStatus.CANCELLED),
            ActionStatus.VALIDATING, Set.of(ActionStatus.REJECTED, ActionStatus.AUTHORIZED),
            ActionStatus.AUTHORIZED, Set.of(ActionStatus.REJECTED, ActionStatus.ACCEPTED,
                    ActionStatus.RUNNING, ActionStatus.SUCCEEDED, ActionStatus.FAILED),
            ActionStatus.ACCEPTED, Set.of(ActionStatus.RUNNING, ActionStatus.SUCCEEDED,
                    ActionStatus.FAILED, ActionStatus.CANCELLED),
            ActionStatus.RUNNING, Set.of(ActionStatus.SUCCEEDED, ActionStatus.FAILED,
                    ActionStatus.CANCELLED, ActionStatus.COMPENSATING),
            ActionStatus.COMPENSATING, Set.of(ActionStatus.COMPENSATED, ActionStatus.FAILED)
    );

    private ActionStatusMachine() {
    }

    public static boolean canTransition(ActionStatus current, ActionStatus next) {
        if (current == null || next == null) {
            return false;
        }
        if (current == next) {
            return true;
        }
        if (current.terminal()) {
            return false;
        }
        return ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(next);
    }

    public static void requireTransition(ActionStatus current, ActionStatus next) {
        if (!canTransition(current, next)) {
            throw new ActionRuntimeException(
                    40943,
                    ActionErrorCategory.CONFLICT,
                    current != null && current.terminal()
                            ? "ACTION_STATUS_TERMINAL"
                            : "ACTION_STATUS_TRANSITION_INVALID",
                    "status",
                    null);
        }
    }

    /**
     * Classify a runtime status string into a coarse-grained group for UI rendering.
     *
     * @param status           the raw status string (nullable)
     * @param terminalStatuses set of statuses that map to {@code TERMINAL}
     * @return {@code TERMINAL}, {@code IN_PROGRESS}, {@code BUSINESS}, or {@code null} when status is blank
     */
    public static String classifyStatusGroup(String status, Set<String> terminalStatuses) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.toUpperCase();
        for (String terminal : terminalStatuses) {
            if (normalized.contains(terminal.toUpperCase())) {
                return "TERMINAL";
            }
        }
        if (normalized.contains("RUNNING") || normalized.contains("PENDING")
                || normalized.contains("ACTIVE")) {
            return "IN_PROGRESS";
        }
        return "BUSINESS";
    }
}

import type { ActionApi } from '#/api/action-client';

import { preferences } from '@vben/preferences';
import { useUserStore } from '@vben/stores';

import {
  createActionIdempotencyKey,
  dispatchActionCandidate,
  validateActionCandidate,
  validateActionCandidates,
} from '#/api/action-client';

interface CreateCandidateOptions {
  candidateId?: string;
  correlationId?: string;
  proposedBy?: string;
  reason?: string;
  source?: 'AGENT' | 'LUI';
}

function useActionCandidates() {
  const userStore = useUserStore();

  function createCandidate(
    input: ActionApi.ActionCandidate,
    options: CreateCandidateOptions = {},
  ): ActionApi.ActionCandidate {
    const source = options.source ?? input.source ?? 'LUI';
    const userInfo = userStore.userInfo;
    const tenantId =
      input.context?.tenantId ?? input.actor?.tenantId ?? input.target?.tenantId;

    return {
      ...input,
      actor: {
        type: 'USER',
        id: userInfo?.userId,
        displayName: userInfo?.realName || userInfo?.username,
        tenantId,
        ...input.actor,
      },
      candidateId:
        options.candidateId ??
        input.candidateId ??
        createActionIdempotencyKey('action-candidate', input.actionType),
      context: {
        locale: preferences.app.locale,
        tenantId,
        ...input.context,
        attributes: {
          ...input.context?.attributes,
          naturalLanguageCorrelationId: options.correlationId,
        },
        correlationId: options.correlationId ?? input.context?.correlationId,
      },
      proposedBy: options.proposedBy ?? input.proposedBy ?? source,
      reason: options.reason ?? input.reason,
      source,
    };
  }

  function validateCandidate(candidate: ActionApi.ActionCandidate) {
    return validateActionCandidate(candidate);
  }

  function validateCandidates(candidates: ActionApi.ActionCandidate[]) {
    return validateActionCandidates(candidates);
  }

  function dispatchCandidate<TData = Record<string, unknown>>(
    candidate: ActionApi.ActionCandidate,
  ) {
    return dispatchActionCandidate<TData>(candidate);
  }

  return {
    createCandidate,
    dispatchCandidate,
    validateCandidates,
    validateCandidate,
  };
}

export { useActionCandidates };

import type { ActionApi } from '#/api/action-client';
import type { AgentApi } from '#/api/agent';

import { computed, ref } from 'vue';

import { defineStore } from 'pinia';

import { resolveAgentComponent } from '#/registry/agent-component-registry';

interface AgentMessage {
  id: string;
  role: 'assistant' | 'user';
  text: string;
}

interface MissingInput extends Record<string, unknown> {
  missingSlots: string[];
  prompt: string;
}

interface Evidence {
  evidenceId: string;
  excerpt?: string;
  title: string;
  uri?: string;
}

export const useAgentStore = defineStore('agent', () => {
  const open = ref(false);
  const activeRun = ref<AgentApi.Run>();
  const messages = ref<AgentMessage[]>([]);
  const pendingCandidate = ref<ActionApi.ActionCandidate>();
  const missingInput = ref<MissingInput>();
  const evidence = ref<Evidence[]>([]);
  const actionResult = ref<ActionApi.GlobalActionResult>();
  const refreshScopes = ref<string[]>([]);
  const refreshVersion = ref(0);
  const error = ref<{ code: string; message: string }>();
  const lastSequence = ref(0);

  const busy = computed(() =>
    ['CREATED', 'RUNNING'].includes(activeRun.value?.status ?? ''),
  );

  function begin(run: AgentApi.Run, text: string) {
    activeRun.value = run;
    lastSequence.value = run.lastSequence ?? 0;
    pendingCandidate.value = undefined;
    missingInput.value = undefined;
    actionResult.value = undefined;
    error.value = undefined;
    messages.value.push({
      id: `user:${run.runId}`,
      role: 'user',
      text,
    });
  }

  function applyEvent(event: AgentApi.Event) {
    if (event.sequence <= lastSequence.value) return;
    lastSequence.value = event.sequence;
    switch (event.eventType) {
      case 'action.candidate': {
        const resolved = resolveAgentComponent<{
          candidate: ActionApi.ActionCandidate;
        }>('AgentActionCandidate', event.data);
        pendingCandidate.value = resolved.props.candidate;
        break;
      }
      case 'action.status': {
        const resolved = resolveAgentComponent<{
          result: ActionApi.GlobalActionResult;
        }>('AgentActionResult', event.data);
        actionResult.value = resolved.props.result;
        break;
      }
      case 'confirmation.required': {
        if (event.data.candidate) {
          const resolved = resolveAgentComponent<{
            candidate: ActionApi.ActionCandidate;
          }>('AgentActionCandidate', { candidate: event.data.candidate });
          pendingCandidate.value = resolved.props.candidate;
        }
        setStatus('WAITING_CONFIRMATION');
        break;
      }
      case 'evidence.ready': {
        const resolved = resolveAgentComponent<{
          evidence: Evidence[];
        }>('AgentEvidence', event.data);
        evidence.value = resolved.props.evidence;
        break;
      }
      case 'message.delta': {
        if (typeof event.data.text === 'string' && event.data.text) {
          messages.value.push({
            id: event.eventId,
            role: 'assistant',
            text: event.data.text,
          });
        }
        break;
      }
      case 'run.cancelled':
        setStatus('CANCELLED');
        pendingCandidate.value = undefined;
        missingInput.value = undefined;
        break;
      case 'run.completed':
        setStatus('COMPLETED');
        pendingCandidate.value = undefined;
        missingInput.value = undefined;
        break;
      case 'run.failed': {
        const raw = event.data.error;
        const props =
          raw && typeof raw === 'object'
            ? (raw as Record<string, unknown>)
            : { code: 'AGENT_RUN_FAILED', message: 'AI 助手运行失败' };
        const resolved = resolveAgentComponent<{ code: string; message: string }>(
          'AgentError',
          {
            code: String(props.code || 'AGENT_RUN_FAILED'),
            message: String(props.message || 'AI 助手运行失败'),
          },
        );
        error.value = resolved.props;
        setStatus('FAILED');
        break;
      }
      case 'slot.missing': {
        const resolved = resolveAgentComponent<MissingInput>(
          'AgentMissingInput',
          event.data,
        );
        missingInput.value = resolved.props;
        setStatus('WAITING_INPUT');
        break;
      }
      default:
        // Forward-compatible: safely ignore unknown event types.
        break;
    }
  }

  function setRun(run: AgentApi.Run) {
    activeRun.value = run;
  }

  function setActionResult(result: ActionApi.GlobalActionResult) {
    resolveAgentComponent('AgentActionResult', { result });
    actionResult.value = result;
    refreshScopes.value = [...new Set(result.refreshScopes ?? [])];
    refreshVersion.value += 1;
  }

  function setError(code: string, message: string) {
    resolveAgentComponent('AgentError', { code, message });
    error.value = { code, message };
  }

  function setStatus(status: AgentApi.RunStatus) {
    if (activeRun.value) activeRun.value.status = status;
  }

  function clearConversation() {
    activeRun.value = undefined;
    messages.value = [];
    pendingCandidate.value = undefined;
    missingInput.value = undefined;
    evidence.value = [];
    actionResult.value = undefined;
    refreshScopes.value = [];
    error.value = undefined;
    lastSequence.value = 0;
  }

  return {
    actionResult,
    activeRun,
    applyEvent,
    begin,
    busy,
    clearConversation,
    error,
    evidence,
    lastSequence,
    messages,
    missingInput,
    open,
    pendingCandidate,
    refreshScopes,
    refreshVersion,
    setActionResult,
    setError,
    setRun,
  };
});

export type { AgentMessage, Evidence, MissingInput };

import type { ActionApi } from '#/api/action-client';

type AgentComponentKey =
  | 'AgentActionCandidate'
  | 'AgentActionResult'
  | 'AgentError'
  | 'AgentEvidence'
  | 'AgentMissingInput';

interface AgentComponentRegistration<
  TProps extends Record<string, unknown>,
> {
  key: AgentComponentKey;
  validate: (props: Record<string, unknown>) => props is TProps;
}

interface AgentActionCandidateProps extends Record<string, unknown> {
  candidate: ActionApi.ActionCandidate;
}

interface AgentActionResultProps extends Record<string, unknown> {
  result: ActionApi.GlobalActionResult;
}

interface AgentMissingInputProps extends Record<string, unknown> {
  missingSlots: string[];
  prompt: string;
}

interface AgentEvidenceProps extends Record<string, unknown> {
  evidence: Array<{
    evidenceId: string;
    excerpt?: string;
    title: string;
    uri?: string;
  }>;
}

interface AgentErrorProps extends Record<string, unknown> {
  code: string;
  message: string;
}

const registry: Record<
  AgentComponentKey,
  AgentComponentRegistration<any>
> = {
  AgentActionCandidate: {
    key: 'AgentActionCandidate',
    validate: (props): props is AgentActionCandidateProps =>
      safeProps(props) &&
      record(props.candidate) &&
      text(props.candidate.actionType) &&
      record(props.candidate.payload),
  },
  AgentActionResult: {
    key: 'AgentActionResult',
    validate: (props): props is AgentActionResultProps =>
      safeProps(props) &&
      record(props.result) &&
      text(props.result.status),
  },
  AgentError: {
    key: 'AgentError',
    validate: (props): props is AgentErrorProps =>
      safeProps(props) && text(props.code) && text(props.message),
  },
  AgentEvidence: {
    key: 'AgentEvidence',
    validate: (props): props is AgentEvidenceProps =>
      safeProps(props) &&
      Array.isArray(props.evidence) &&
      props.evidence.every(
        (item) => record(item) && text(item.evidenceId) && text(item.title),
      ),
  },
  AgentMissingInput: {
    key: 'AgentMissingInput',
    validate: (props): props is AgentMissingInputProps =>
      safeProps(props) &&
      text(props.prompt) &&
      Array.isArray(props.missingSlots) &&
      props.missingSlots.every(text),
  },
};

function resolveAgentComponent<TProps extends Record<string, unknown>>(
  key: string,
  props: Record<string, unknown>,
) {
  const registration = registry[key as AgentComponentKey];
  if (!registration) throw new Error('AGENT_COMPONENT_NOT_REGISTERED');
  if (!registration.validate(props)) {
    throw new Error('AGENT_COMPONENT_PROPS_INVALID');
  }
  return { key: registration.key, props: props as TProps };
}

function safeProps(props: Record<string, unknown>) {
  return Object.keys(props).every(
    (key) =>
      !/^on[A-Z]/.test(key) &&
      !['dangerouslySetInnerHTML', 'html', 'innerHTML', 'script'].includes(key),
  );
}

function record(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

function text(value: unknown): value is string {
  return typeof value === 'string' && value.trim() !== '';
}

export type {
  AgentActionCandidateProps,
  AgentActionResultProps,
  AgentComponentKey,
  AgentErrorProps,
  AgentEvidenceProps,
  AgentMissingInputProps,
};
export { resolveAgentComponent };

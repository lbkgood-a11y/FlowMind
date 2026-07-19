import type { ActionApi } from '#/api/action-client';

type ActionComponentKey = 'ActionCandidateConfirmation' | 'ActionResultSummary';

interface ActionComponentRegistration<TProps extends Record<string, unknown>> {
  key: ActionComponentKey;
  validate: (props: Record<string, unknown>) => props is TProps;
}

interface ActionCandidateConfirmationProps extends Record<string, unknown> {
  actionType: string;
  candidateId: string;
  confirmation?: ActionApi.ActionConfirmation;
  message?: string;
  title: string;
}

interface ActionResultSummaryProps extends Record<string, unknown> {
  actionId: string;
  actionType?: string;
  message?: string;
  status: ActionApi.ActionStatus | string;
}

const registry: Record<ActionComponentKey, ActionComponentRegistration<any>> = {
  ActionCandidateConfirmation: {
    key: 'ActionCandidateConfirmation',
    validate: (props): props is ActionCandidateConfirmationProps =>
      safeProps(props) &&
      text(props.actionType) &&
      text(props.candidateId) &&
      text(props.title),
  },
  ActionResultSummary: {
    key: 'ActionResultSummary',
    validate: (props): props is ActionResultSummaryProps =>
      safeProps(props) && text(props.actionId) && text(props.status),
  },
};

function resolveActionComponent<TProps extends Record<string, unknown>>(
  key: string,
  props: Record<string, unknown>,
) {
  const registration = registry[key as ActionComponentKey];
  if (!registration) {
    throw new Error('ACTION_COMPONENT_NOT_REGISTERED');
  }
  if (!registration.validate(props)) {
    throw new Error('ACTION_COMPONENT_PROPS_INVALID');
  }
  return {
    key: registration.key,
    props: props as TProps,
  };
}

function safeProps(props: Record<string, unknown>) {
  return Object.keys(props).every(
    (key) =>
      !/^on[A-Z]/.test(key) &&
      key !== 'innerHTML' &&
      key !== 'dangerouslySetInnerHTML' &&
      key !== 'html' &&
      key !== 'script',
  );
}

function text(value: unknown): value is string {
  return typeof value === 'string' && value.trim() !== '';
}

export type {
  ActionCandidateConfirmationProps,
  ActionComponentKey,
  ActionResultSummaryProps,
};
export { resolveActionComponent };

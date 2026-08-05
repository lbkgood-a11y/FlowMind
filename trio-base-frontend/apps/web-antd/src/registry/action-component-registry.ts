import type { ActionApi } from '#/api/action-client';

import { z } from 'zod';

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

const safeBase = z.object({}).catchall(z.unknown()).superRefine((props, context) => {
  for (const key of Object.keys(props)) {
    if (/^on[A-Z]/.test(key) || ['innerHTML', 'dangerouslySetInnerHTML', 'html', 'script'].includes(key)) {
      context.addIssue({ code: z.ZodIssueCode.custom, message: 'unsafe component property', path: [key] });
    }
  }
});
const candidateSchema = safeBase.and(z.object({
  actionType: z.string().trim().min(1), candidateId: z.string().trim().min(1), title: z.string().trim().min(1),
}));
const resultSchema = safeBase.and(z.object({
  actionId: z.string().trim().min(1), status: z.string().trim().min(1),
}));

const registry: Record<ActionComponentKey, ActionComponentRegistration<any>> = {
  ActionCandidateConfirmation: {
    key: 'ActionCandidateConfirmation',
    validate: (props): props is ActionCandidateConfirmationProps => candidateSchema.safeParse(props).success,
  },
  ActionResultSummary: {
    key: 'ActionResultSummary',
    validate: (props): props is ActionResultSummaryProps => resultSchema.safeParse(props).success,
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

export type {
  ActionCandidateConfirmationProps,
  ActionComponentKey,
  ActionResultSummaryProps,
};
export { resolveActionComponent };

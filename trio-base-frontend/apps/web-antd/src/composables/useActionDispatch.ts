import type { ActionApi } from '#/api/action-client';

import { computed, ref } from 'vue';

import { preferences } from '@vben/preferences';
import { useUserStore } from '@vben/stores';

import { message, Modal } from 'ant-design-vue';

import { submitAction } from '#/api/action-client';
import {
  formatActionError,
  isActionSucceeded,
  isActionTerminal,
} from '#/api/action-status';

export interface ActionConfirmationOptions {
  content?: string;
  danger?: boolean;
  okText?: string;
  title?: string;
}

export interface DispatchActionOptions<TData = Record<string, unknown>> {
  confirmation?: ActionConfirmationOptions | boolean;
  failureMessage?: string | ((result: ActionApi.GlobalActionResult<TData>) => string | undefined);
  onSettled?: (result: ActionApi.GlobalActionResult<TData>) => Promise<void> | void;
  onSuccess?: (result: ActionApi.GlobalActionResult<TData>) => Promise<void> | void;
  refresh?: () => Promise<void> | void;
  successMessage?: string | ((result: ActionApi.GlobalActionResult<TData>) => string | undefined);
  throwOnFailure?: boolean;
}

class ActionDispatchError<TData = Record<string, unknown>> extends Error {
  public readonly result: ActionApi.GlobalActionResult<TData>;

  constructor(result: ActionApi.GlobalActionResult<TData>) {
    super(formatActionError(result));
    this.name = 'ActionDispatchError';
    this.result = result;
  }
}

function useActionDispatch() {
  const userStore = useUserStore();
  const loading = ref(false);
  const result = ref<ActionApi.GlobalActionResult>();
  const error = ref<unknown>();
  const status = computed(() => result.value?.status);
  const actionId = computed(() => result.value?.actionId);

  async function dispatchAction<TData = Record<string, unknown>>(
    request: ActionApi.GlobalActionRequest,
    options: DispatchActionOptions<TData> = {},
  ) {
    const confirmed = await confirmIfNeeded(options.confirmation);
    if (!confirmed) {
      throw new Error('ACTION_CONFIRMATION_CANCELLED');
    }

    loading.value = true;
    error.value = undefined;
    try {
      const submitted = await submitAction<TData>(withClientDefaults(request));
      result.value = submitted as ActionApi.GlobalActionResult;
      const failed =
        isActionTerminal(submitted.status) && !isActionSucceeded(submitted.status);

      if (failed) {
        const failureText = resolveMessage(options.failureMessage, submitted)
          || formatActionError(submitted);
        message.error(failureText);
        if (options.throwOnFailure !== false) {
          throw new ActionDispatchError<TData>(submitted);
        }
      } else {
        const successText = resolveMessage(options.successMessage, submitted);
        if (successText) {
          message.success(successText);
        }
        await options.onSuccess?.(submitted);
        await options.refresh?.();
      }

      await options.onSettled?.(submitted);
      return submitted;
    } catch (dispatchError) {
      error.value = dispatchError;
      throw dispatchError;
    } finally {
      loading.value = false;
    }
  }

  function withClientDefaults(
    request: ActionApi.GlobalActionRequest,
  ): ActionApi.GlobalActionRequest {
    const userInfo = userStore.userInfo;
    const targetTenantId = request.target?.tenantId;
    const contextTenantId = request.context?.tenantId;
    const actorTenantId = request.actor?.tenantId;
    const tenantId = contextTenantId ?? actorTenantId ?? targetTenantId;

    return {
      ...request,
      actor: {
        type: 'USER',
        id: userInfo?.userId,
        displayName: userInfo?.realName || userInfo?.username,
        tenantId,
        ...request.actor,
      },
      context: {
        locale: preferences.app.locale,
        tenantId,
        ...request.context,
      },
      source: request.source ?? 'GUI',
    };
  }

  return {
    actionId,
    dispatchAction,
    error,
    loading,
    result,
    status,
  };
}

async function confirmIfNeeded(
  confirmation?: ActionConfirmationOptions | boolean,
) {
  if (!confirmation) {
    return true;
  }
  const options =
    confirmation === true
      ? ({ title: '确认执行该操作？' } satisfies ActionConfirmationOptions)
      : confirmation;
  return new Promise<boolean>((resolve) => {
    Modal.confirm({
      cancelText: '取消',
      content: options.content,
      okButtonProps: { danger: options.danger },
      okText: options.okText ?? '确认',
      onCancel: () => resolve(false),
      onOk: () => resolve(true),
      title: options.title ?? '确认执行该操作？',
    });
  });
}

function resolveMessage<TData>(
  value:
    | string
    | ((result: ActionApi.GlobalActionResult<TData>) => string | undefined)
    | undefined,
  result: ActionApi.GlobalActionResult<TData>,
) {
  return typeof value === 'function' ? value(result) : value;
}

function isActionDispatchError<TData = Record<string, unknown>>(
  error: unknown,
): error is ActionDispatchError<TData> {
  return error instanceof ActionDispatchError;
}

export { ActionDispatchError, isActionDispatchError, useActionDispatch };

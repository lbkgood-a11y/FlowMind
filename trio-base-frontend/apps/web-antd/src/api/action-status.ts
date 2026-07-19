import type { ActionApi } from './action-client';

const ACTION_TERMINAL_STATUSES = new Set<ActionApi.ActionStatus>([
  'CANCELLED',
  'COMPENSATED',
  'FAILED',
  'REJECTED',
  'SUCCEEDED',
]);

function isActionTerminal(status?: string): boolean {
  return ACTION_TERMINAL_STATUSES.has(status as ActionApi.ActionStatus);
}

function isActionSucceeded(status?: string): boolean {
  return status === 'SUCCEEDED' || status === 'COMPENSATED';
}

function isActionRejected(status?: string): boolean {
  return status === 'REJECTED' || status === 'FAILED' || status === 'CANCELLED';
}

function actionStatusLabel(status?: string): string {
  const labels: Record<string, string> = {
    ACCEPTED: '已受理',
    AUTHORIZED: '已授权',
    CANCELLED: '已取消',
    COMPENSATED: '已补偿',
    COMPENSATING: '补偿中',
    CREATED: '已创建',
    FAILED: '执行失败',
    REJECTED: '已拒绝',
    RUNNING: '执行中',
    SUCCEEDED: '执行成功',
    VALIDATING: '校验中',
  };
  return status ? labels[status] ?? status : '未知状态';
}

function actionStatusColor(status?: string): string {
  if (isActionSucceeded(status)) {
    return 'success';
  }
  if (isActionRejected(status)) {
    return 'error';
  }
  if (status === 'ACCEPTED' || status === 'RUNNING' || status === 'VALIDATING') {
    return 'processing';
  }
  return 'default';
}

function getFirstActionError(
  result?: ActionApi.GlobalActionResult<any>,
): ActionApi.ActionError | undefined {
  return result?.errors?.find((error) => error?.code || error?.message);
}

function getActionErrorCode(
  result?: ActionApi.GlobalActionResult<any>,
): string | undefined {
  return getFirstActionError(result)?.code;
}

function getActionErrorDetails<T = Record<string, unknown>>(
  result?: ActionApi.GlobalActionResult<any>,
): T | undefined {
  return getFirstActionError(result)?.details as T | undefined;
}

function formatActionError(result?: ActionApi.GlobalActionResult<any>): string {
  const error = getFirstActionError(result);
  return (
    result?.message ||
    error?.message ||
    error?.code ||
    actionStatusLabel(result?.status) ||
    '操作失败'
  );
}

function requireActionData<T>(
  result: ActionApi.GlobalActionResult<any> | undefined,
  key: string,
): T {
  const data = result?.data as Record<string, unknown> | undefined;
  if (data && data[key] !== undefined && data[key] !== null) {
    return data[key] as T;
  }
  throw new Error(`ACTION_RESULT_${key.toUpperCase()}_MISSING`);
}

export {
  ACTION_TERMINAL_STATUSES,
  actionStatusColor,
  actionStatusLabel,
  formatActionError,
  getActionErrorCode,
  getActionErrorDetails,
  getFirstActionError,
  isActionRejected,
  isActionSucceeded,
  isActionTerminal,
  requireActionData,
};

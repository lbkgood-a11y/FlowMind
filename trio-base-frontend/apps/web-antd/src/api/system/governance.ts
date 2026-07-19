import { requestClient } from '#/api/request';

export namespace SystemGovernanceApi {
  export interface PageResult<T> {
    page: number;
    records: T[];
    size: number;
    total: number;
  }

  export interface AuditLog {
    actionCorrelationId?: string;
    actionId?: string;
    actionIdempotencyKey?: string;
    actionName?: string;
    actionSource?: string;
    actionStatus?: string;
    actionSummary?: string;
    actionTargetId?: string;
    actionTargetType?: string;
    actionType?: string;
    clientIp?: string;
    errorMessage?: string;
    httpMethod?: string;
    id: string;
    latencyMs?: number;
    moduleName?: string;
    operatedAt?: string;
    permissionCode?: string;
    queryString?: string;
    requestPath?: string;
    requestSummary?: string;
    resultStatus?: string;
    statusCode?: number;
    traceId?: string;
    userAgent?: string;
    userId?: string;
    username?: string;
  }

  export interface LoginLog {
    clientIp?: string;
    failureReason?: string;
    id: string;
    loginAt?: string;
    loginResult: string;
    traceId?: string;
    userAgent?: string;
    userId?: string;
    username?: string;
  }

  export interface UserSession {
    accessJti?: string;
    clientIp?: string;
    expiresAt?: string;
    id: string;
    issuedAt?: string;
    lastActiveAt?: string;
    logoutAt?: string;
    refreshExpiresAt?: string;
    revokedAt?: string;
    revokedBy?: string;
    sessionStatus: string;
    userAgent?: string;
    userId: string;
    username: string;
  }

  export interface DictType {
    description?: string;
    dictCode: string;
    dictName: string;
    id: string;
    sortOrder?: number;
    status?: 0 | 1;
    systemFlag?: 0 | 1;
  }

  export interface DictItem {
    cssClass?: string;
    description?: string;
    dictCode: string;
    dictTypeId: string;
    id: string;
    itemLabel: string;
    itemValue: string;
    metadata?: string;
    sortOrder?: number;
    status?: 0 | 1;
    systemFlag?: 0 | 1;
    tagType?: string;
  }

  export interface SaveDictTypeParams {
    description?: string;
    dictCode: string;
    dictName: string;
    sortOrder?: number;
    status?: 0 | 1;
    systemFlag?: 0 | 1;
  }

  export interface SaveDictItemParams {
    cssClass?: string;
    description?: string;
    dictCode?: string;
    dictTypeId?: string;
    itemLabel: string;
    itemValue: string;
    metadata?: string;
    sortOrder?: number;
    status?: 0 | 1;
    systemFlag?: 0 | 1;
    tagType?: string;
  }

  export interface SystemConfig {
    configGroup: string;
    configKey: string;
    configType: string;
    configValue?: string;
    defaultValue?: string;
    description?: string;
    id: string;
    sensitive?: 0 | 1;
    sortOrder?: number;
    status?: 0 | 1;
    systemFlag?: 0 | 1;
    updatedAt?: string;
  }

  export interface UpdateSystemConfigParams {
    configGroup?: string;
    configType?: string;
    configValue?: string;
    defaultValue?: string;
    description?: string;
    sensitive?: 0 | 1;
    sortOrder?: number;
    status?: 0 | 1;
  }
}

async function getAuditLogPage(params?: Record<string, any>) {
  return requestClient.get<SystemGovernanceApi.PageResult<SystemGovernanceApi.AuditLog>>(
    '/audit-logs',
    { params },
  );
}

async function getAuditLogDetail(id: string) {
  return requestClient.get<SystemGovernanceApi.AuditLog>(`/audit-logs/${id}`);
}

async function getLoginLogPage(params?: Record<string, any>) {
  return requestClient.get<SystemGovernanceApi.PageResult<SystemGovernanceApi.LoginLog>>(
    '/sessions/login-logs',
    { params },
  );
}

async function getSessionPage(params?: Record<string, any>) {
  return requestClient.get<SystemGovernanceApi.PageResult<SystemGovernanceApi.UserSession>>(
    '/sessions',
    { params },
  );
}

async function revokeSession(id: string) {
  return requestClient.put<SystemGovernanceApi.UserSession>(
    `/sessions/${id}/revoke`,
  );
}

async function getDictTypes(params?: { keyword?: string; status?: 0 | 1 }) {
  return requestClient.get<SystemGovernanceApi.DictType[]>('/dictionaries/types', {
    params,
  });
}

async function createDictType(data: SystemGovernanceApi.SaveDictTypeParams) {
  return requestClient.post<SystemGovernanceApi.DictType>('/dictionaries/types', data);
}

async function updateDictType(id: string, data: SystemGovernanceApi.SaveDictTypeParams) {
  return requestClient.put<SystemGovernanceApi.DictType>(`/dictionaries/types/${id}`, data);
}

async function deleteDictType(id: string) {
  return requestClient.delete(`/dictionaries/types/${id}`);
}

async function getDictItems(params?: { dictCode?: string; status?: 0 | 1 }) {
  return requestClient.get<SystemGovernanceApi.DictItem[]>('/dictionaries/items', {
    params,
  });
}

async function getEnabledDictItems(dictCode: string) {
  return requestClient.get<SystemGovernanceApi.DictItem[]>(
    `/dictionaries/items/enabled/${dictCode}`,
  );
}

async function createDictItem(data: SystemGovernanceApi.SaveDictItemParams) {
  return requestClient.post<SystemGovernanceApi.DictItem>('/dictionaries/items', data);
}

async function updateDictItem(id: string, data: SystemGovernanceApi.SaveDictItemParams) {
  return requestClient.put<SystemGovernanceApi.DictItem>(`/dictionaries/items/${id}`, data);
}

async function deleteDictItem(id: string) {
  return requestClient.delete(`/dictionaries/items/${id}`);
}

async function getSystemConfigs(params?: {
  configGroup?: string;
  keyword?: string;
  status?: 0 | 1;
}) {
  return requestClient.get<SystemGovernanceApi.SystemConfig[]>('/system-configs', {
    params,
  });
}

async function updateSystemConfig(
  id: string,
  data: SystemGovernanceApi.UpdateSystemConfigParams,
) {
  return requestClient.put<SystemGovernanceApi.SystemConfig>(
    `/system-configs/${id}`,
    data,
  );
}

export {
  createDictItem,
  createDictType,
  deleteDictItem,
  deleteDictType,
  getAuditLogDetail,
  getAuditLogPage,
  getDictItems,
  getDictTypes,
  getEnabledDictItems,
  getLoginLogPage,
  getSessionPage,
  getSystemConfigs,
  revokeSession,
  updateDictItem,
  updateDictType,
  updateSystemConfig,
};

import { requestClient } from '#/api/request';

export namespace SystemTenantApi {
  export type TenantStatus = 'ACTIVE' | 'DISABLED' | 'SUSPENDED';

  export interface Tenant {
    attributesJson?: string;
    contactEmail?: string;
    contactName?: string;
    contactPhone?: string;
    createdAt?: string;
    createdBy?: string;
    expireAt?: string;
    industry?: string;
    isolationMode?: string;
    locale?: string;
    maxUsers?: number;
    planCode?: string;
    region?: string;
    shortName?: string;
    status: TenantStatus;
    suspendedReason?: string;
    tenantCode?: string;
    tenantId: string;
    tenantName: string;
    tenantType?: string;
    timezone?: string;
    updatedAt?: string;
    updatedBy?: string;
  }

  export interface TenantListParams {
    keyword?: string;
    page?: number;
    size?: number;
    status?: TenantStatus;
  }

  export interface TenantListResult {
    items: Tenant[];
    total: number;
  }

  export interface SaveTenantParams {
    attributesJson?: string;
    contactEmail?: string;
    contactName?: string;
    contactPhone?: string;
    expireAt?: string;
    industry?: string;
    locale?: string;
    maxUsers?: number;
    planCode?: string;
    region?: string;
    shortName?: string;
    tenantId?: string;
    tenantName: string;
    tenantType?: string;
    timezone?: string;
  }

  export interface TenantSetting {
    description?: string;
    enabled: boolean;
    id: string;
    sensitive: boolean;
    settingKey: string;
    settingValue?: string;
    tenantId: string;
    updatedAt?: string;
    valueType?: string;
  }

  export interface SaveTenantSettingParams {
    description?: string;
    enabled?: boolean;
    sensitive?: boolean;
    settingKey: string;
    settingValue?: string;
    valueType?: string;
  }
}

async function getTenantPage(params: SystemTenantApi.TenantListParams = {}) {
  const page = await requestClient.get<{
    page: number;
    records: SystemTenantApi.Tenant[];
    size: number;
    total: number;
  }>('/tenants', {
    params: {
      keyword: params.keyword,
      page: params.page ?? 1,
      size: params.size ?? 20,
      status: params.status,
    },
  });

  return {
    items: page.records,
    total: page.total,
  } satisfies SystemTenantApi.TenantListResult;
}

async function getCurrentTenant() {
  return requestClient.get<SystemTenantApi.Tenant>('/tenants/current');
}

async function getTenant(tenantId: string) {
  return requestClient.get<SystemTenantApi.Tenant>(
    `/tenants/${encodeURIComponent(tenantId)}`,
  );
}

async function createTenant(data: SystemTenantApi.SaveTenantParams) {
  return requestClient.post<SystemTenantApi.Tenant>('/tenants', data);
}

async function updateTenant(
  tenantId: string,
  data: SystemTenantApi.SaveTenantParams,
) {
  return requestClient.put<SystemTenantApi.Tenant>(
    `/tenants/${encodeURIComponent(tenantId)}`,
    data,
  );
}

async function updateTenantStatus(
  tenantId: string,
  status: SystemTenantApi.TenantStatus,
  reason?: string,
) {
  return requestClient.put<SystemTenantApi.Tenant>(
    `/tenants/${encodeURIComponent(tenantId)}/status`,
    { reason, status },
  );
}

async function getTenantSettings(tenantId: string) {
  return requestClient.get<SystemTenantApi.TenantSetting[]>(
    `/tenants/${encodeURIComponent(tenantId)}/settings`,
  );
}

async function saveTenantSetting(
  tenantId: string,
  settingKey: string,
  data: SystemTenantApi.SaveTenantSettingParams,
) {
  return requestClient.put<SystemTenantApi.TenantSetting>(
    `/tenants/${encodeURIComponent(tenantId)}/settings/${encodeURIComponent(settingKey)}`,
    data,
  );
}

async function deleteTenantSetting(tenantId: string, settingKey: string) {
  return requestClient.delete(
    `/tenants/${encodeURIComponent(tenantId)}/settings/${encodeURIComponent(settingKey)}`,
  );
}

export {
  createTenant,
  deleteTenantSetting,
  getCurrentTenant,
  getTenant,
  getTenantPage,
  getTenantSettings,
  saveTenantSetting,
  updateTenant,
  updateTenantStatus,
};

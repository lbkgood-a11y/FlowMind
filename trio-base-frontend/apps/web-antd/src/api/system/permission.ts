import { requestClient } from '#/api/request';

export namespace SystemPermissionApi {
  export interface SystemPermission {
    action: string;
    createdAt?: string;
    description?: string;
    id: string;
    resource: string;
  }
}

async function getPermissionList() {
  return requestClient.get<SystemPermissionApi.SystemPermission[]>('/permissions');
}

export { getPermissionList };

import { requestClient } from '#/api/request';

export namespace SystemMenuApi {
  export type MenuType = 'button' | 'catalog' | 'embedded' | 'link' | 'menu';

  export interface MenuListParams {
    keyword?: string;
    menuGroup?: string;
    menuType?: MenuType;
    status?: 0 | 1;
  }

  export interface SystemMenu {
    activeIcon?: string;
    activePath?: string;
    affixTab?: 0 | 1;
    badge?: string;
    badgeType?: string;
    badgeVariant?: string;
    children?: SystemMenu[];
    component?: string;
    createdAt?: string;
    description?: string;
    hideChildrenInMenu?: 0 | 1;
    hideInBreadcrumb?: 0 | 1;
    hideInMenu?: 0 | 1;
    hideInTab?: 0 | 1;
    icon?: string;
    id: string;
    keepAlive?: 0 | 1;
    menuGroup?: string;
    menuKey: string;
    menuName: string;
    menuType?: MenuType;
    parentId?: string;
    path?: string;
    permissionCode?: string;
    sortOrder?: number;
    status?: 0 | 1;
    updatedAt?: string;
    visible?: 0 | 1;
  }

  export interface SaveMenuParams {
    activeIcon?: string;
    activePath?: string;
    affixTab?: boolean;
    badge?: string;
    badgeType?: string;
    badgeVariant?: string;
    component?: string;
    description?: string;
    hideChildrenInMenu?: boolean;
    hideInBreadcrumb?: boolean;
    hideInMenu?: boolean;
    hideInTab?: boolean;
    icon?: string;
    keepAlive?: boolean;
    menuGroup?: string;
    menuKey: string;
    menuName: string;
    menuType: MenuType;
    parentId?: string;
    path?: string;
    permissionCode?: string;
    sortOrder?: number;
    status?: 0 | 1;
    visible?: boolean;
  }
}

async function getMenuList(params?: SystemMenuApi.MenuListParams) {
  return requestClient.get<SystemMenuApi.SystemMenu[]>('/menus', { params });
}

async function createMenu(data: SystemMenuApi.SaveMenuParams) {
  return requestClient.post<SystemMenuApi.SystemMenu>('/menus', data);
}

async function updateMenu(id: string, data: SystemMenuApi.SaveMenuParams) {
  return requestClient.put<SystemMenuApi.SystemMenu>(`/menus/${id}`, data);
}

async function updateMenuStatus(id: string, status: 0 | 1) {
  return requestClient.put<SystemMenuApi.SystemMenu>(
    `/menus/${id}/status`,
    undefined,
    {
      params: { status },
    },
  );
}

async function deleteMenu(id: string) {
  return requestClient.delete(`/menus/${id}`);
}

async function menuKeyExists(menuKey: string, excludeId?: string) {
  return requestClient.get<boolean>('/menus/exists/key', {
    params: { excludeId, menuKey },
  });
}

async function menuPathExists(path: string, excludeId?: string) {
  return requestClient.get<boolean>('/menus/exists/path', {
    params: { excludeId, path },
  });
}

export {
  createMenu,
  deleteMenu,
  getMenuList,
  menuKeyExists,
  menuPathExists,
  updateMenu,
  updateMenuStatus,
};

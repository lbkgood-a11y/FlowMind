import type { SystemMenuApi } from '#/api';

export type MenuTreeNode = SystemMenuApi.SystemMenu & {
  children?: MenuTreeNode[];
};

export type MenuFilters = {
  keyword?: string;
  menuType?: SystemMenuApi.MenuType;
  status?: 0 | 1;
};

export type MenuWorkbenchModel = {
  navigationTree: MenuTreeNode[];
  permissionsByMenuId: Map<string, SystemMenuApi.SystemMenu[]>;
  unassignedPermissions: SystemMenuApi.SystemMenu[];
};

export type ReorderResult =
  | { error: 'cross-level' | 'invalid'; items: [] }
  | { items: Array<{ menu: SystemMenuApi.SystemMenu; sortOrder: number }> };

export function isPermissionNode(menu: SystemMenuApi.SystemMenu) {
  return (
    menu.menuType === 'button' ||
    (menu.hideInMenu === 1 && !menu.path && !menu.component)
  );
}

export function buildMenuWorkbench(
  menus: SystemMenuApi.SystemMenu[],
): MenuWorkbenchModel {
  const byId = new Map(menus.map((menu) => [menu.id, menu]));
  const permissionsByMenuId = new Map<string, SystemMenuApi.SystemMenu[]>();
  const unassignedPermissions: SystemMenuApi.SystemMenu[] = [];
  const navigationMenus = menus.filter((menu) => !isPermissionNode(menu));

  for (const permission of menus.filter(isPermissionNode)) {
    let parent = permission.parentId ? byId.get(permission.parentId) : undefined;
    while (parent && isPermissionNode(parent)) {
      parent = parent.parentId ? byId.get(parent.parentId) : undefined;
    }
    if (!parent) {
      unassignedPermissions.push(permission);
      continue;
    }
    const siblings = permissionsByMenuId.get(parent.id) ?? [];
    siblings.push(permission);
    permissionsByMenuId.set(parent.id, siblings);
  }

  permissionsByMenuId.forEach(sortMenus);
  sortMenus(unassignedPermissions);
  return {
    navigationTree: buildMenuTree(navigationMenus),
    permissionsByMenuId,
    unassignedPermissions,
  };
}

export function buildMenuTree(menus: SystemMenuApi.SystemMenu[]) {
  const nodeMap = new Map<string, MenuTreeNode>();
  menus.forEach((menu) => nodeMap.set(menu.id, { ...menu, children: [] }));
  const roots: MenuTreeNode[] = [];
  nodeMap.forEach((node) => {
    const parent = node.parentId ? nodeMap.get(node.parentId) : undefined;
    if (parent) {
      parent.children?.push(node);
    } else {
      roots.push(node);
    }
  });
  sortTree(roots);
  return roots;
}

export function defaultExpandedKeys(tree: MenuTreeNode[]) {
  const firstExpandableRoot = tree.find((node) => node.children?.length);
  return firstExpandableRoot ? [firstExpandableRoot.id] : [];
}

export function allExpandableKeys(tree: MenuTreeNode[]): string[] {
  return tree.flatMap((node) => [
    ...(node.children?.length ? [node.id] : []),
    ...allExpandableKeys(node.children ?? []),
  ]);
}

export function isFullyExpanded(
  expandedKeys: Array<number | string>,
  expandableKeys: string[],
) {
  const expanded = new Set(expandedKeys.map(String));
  return expandableKeys.length > 0 && expandableKeys.every((key) => expanded.has(key));
}

export function filterMenuTree(tree: MenuTreeNode[], filters: MenuFilters) {
  const keyword = filters.keyword?.trim().toLowerCase();
  const ancestorKeys = new Set<string>();

  const visit = (nodes: MenuTreeNode[], ancestors: string[]): MenuTreeNode[] =>
    nodes.flatMap((node) => {
      const children = visit(node.children ?? [], [...ancestors, node.id]);
      const matchesKeyword =
        !keyword ||
        node.menuName.toLowerCase().includes(keyword) ||
        node.menuKey.toLowerCase().includes(keyword);
      const matchesType = !filters.menuType || node.menuType === filters.menuType;
      const nodeStatus = node.status ?? node.visible ?? 1;
      const matchesStatus = filters.status === undefined || nodeStatus === filters.status;
      if (!(matchesKeyword && matchesType && matchesStatus) && children.length === 0) {
        return [];
      }
      if (matchesKeyword && matchesType && matchesStatus) {
        ancestors.forEach((key) => ancestorKeys.add(key));
      }
      return [{ ...node, children: children.length ? children : undefined }];
    });

  const filteredTree = visit(tree, []);
  return { ancestorKeys: [...ancestorKeys], tree: filteredTree };
}

export function flattenMenuTree(
  tree: MenuTreeNode[],
  level = 0,
): Array<MenuTreeNode & { level: number }> {
  return tree.flatMap((node) => [
    { ...node, level },
    ...flattenMenuTree(node.children ?? [], level + 1),
  ]);
}

export function reorderSiblings(
  menus: SystemMenuApi.SystemMenu[],
  dragId: string,
  targetId: string,
  insertAfter: boolean,
): ReorderResult {
  const drag = menus.find((menu) => menu.id === dragId);
  const target = menus.find((menu) => menu.id === targetId);
  if (!drag || !target) {
    return { error: 'invalid', items: [] };
  }
  if ((drag.parentId ?? '') !== (target.parentId ?? '')) {
    return { error: 'cross-level', items: [] };
  }
  const siblings = menus
    .filter(
      (menu) =>
        !isPermissionNode(menu) &&
        (menu.parentId ?? '') === (drag.parentId ?? ''),
    )
    .sort((left, right) => (left.sortOrder ?? 100) - (right.sortOrder ?? 100));
  const fromIndex = siblings.findIndex((menu) => menu.id === dragId);
  const targetIndex = siblings.findIndex((menu) => menu.id === targetId);
  if (fromIndex < 0 || targetIndex < 0) {
    return { error: 'invalid', items: [] };
  }
  siblings.splice(fromIndex, 1);
  const adjustedTarget = siblings.findIndex((menu) => menu.id === targetId);
  siblings.splice(adjustedTarget + (insertAfter ? 1 : 0), 0, drag);
  return {
    items: siblings.map((menu, index) => ({ menu, sortOrder: (index + 1) * 10 })),
  };
}

function sortMenus(menus: SystemMenuApi.SystemMenu[]) {
  menus.sort(
    (left, right) =>
      (left.sortOrder ?? 100) - (right.sortOrder ?? 100) ||
      left.menuName.localeCompare(right.menuName),
  );
}

function sortTree(nodes: MenuTreeNode[]) {
  sortMenus(nodes);
  nodes.forEach((node) => {
    if (node.children?.length) {
      sortTree(node.children);
    } else {
      delete node.children;
    }
  });
}

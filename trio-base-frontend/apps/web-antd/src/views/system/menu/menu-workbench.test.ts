import type { SystemMenuApi } from '#/api';

import { describe, expect, it } from 'vitest';

import {
  allExpandableKeys,
  buildMenuWorkbench,
  buildPageOptions,
  capabilitiesForPage,
  capabilityCatalogErrorMessage,
  defaultExpandedKeys,
  filterMenuTree,
  isFullyExpanded,
  reorderSiblings,
} from './menu-workbench';

function menu(
  id: string,
  parentId?: string,
  menuType: SystemMenuApi.MenuType = 'menu',
): SystemMenuApi.SystemMenu {
  return {
    id,
    menuKey: id,
    menuName: id,
    menuType,
    parentId,
    sortOrder: 10,
    status: 1,
  };
}

describe('menu workbench model', () => {
  it('builds distinct backend page options and keeps ready pages selectable', () => {
    const capabilities = [
      { category: 'ACCESS', capabilityName: '进入菜单管理', id: 'one', pageCode: 'SYSTEM.MENU', pageName: '菜单管理', readiness: 'READY' },
      { category: 'OPERATION', capabilityName: '新增菜单', id: 'two', pageCode: 'SYSTEM.MENU', pageName: '菜单管理', readiness: 'PARTIAL' },
      { category: 'ACCESS', capabilityName: '进入用户管理', id: 'three', pageCode: 'SYSTEM.USER', pageName: '用户管理', readiness: 'BROKEN' },
    ] as any;

    expect(buildPageOptions(capabilities)).toEqual([
      { label: '菜单管理', readiness: 'READY', value: 'SYSTEM.MENU' },
      { label: '用户管理', readiness: 'BROKEN', value: 'SYSTEM.USER' },
    ]);
  });

  it('returns one page capabilities in declared order', () => {
    const capabilities = [
      { capabilityName: '删除', id: 'delete', pageCode: 'SYSTEM.MENU', sortOrder: 50 },
      { capabilityName: '查看', id: 'read', pageCode: 'SYSTEM.MENU', sortOrder: 20 },
      { capabilityName: '查看用户', id: 'users', pageCode: 'SYSTEM.USER', sortOrder: 20 },
    ] as any;

    expect(capabilitiesForPage(capabilities, 'SYSTEM.MENU').map((item) => item.id)).toEqual([
      'read',
      'delete',
    ]);
    expect(capabilitiesForPage(capabilities, undefined)).toEqual([]);
  });

  it('keeps catalog load failure distinct from an empty page catalog', () => {
    expect(capabilityCatalogErrorMessage()).toContain('加载失败');
    expect(capabilitiesForPage([], 'SYSTEM.MENU')).toEqual([]);
  });

  it('separates navigation and permission nodes', () => {
    const model = buildMenuWorkbench([
      menu('root', undefined, 'catalog'),
      menu('page', 'root'),
      menu('create', 'page', 'button'),
    ]);

    expect(model.navigationTree[0]?.children?.[0]?.id).toBe('page');
    expect(model.navigationTree[0]?.children?.[0]?.children).toBeUndefined();
    expect(model.permissionsByMenuId.get('page')?.[0]?.id).toBe('create');
  });

  it('keeps orphan permissions visible', () => {
    const model = buildMenuWorkbench([menu('orphan', 'missing', 'button')]);
    expect(model.unassignedPermissions.map((item) => item.id)).toEqual(['orphan']);
  });

  it('expands only the first root by default and can detect fully expanded state', () => {
    const tree = buildMenuWorkbench([
      menu('root', undefined, 'catalog'),
      menu('page', 'root'),
      menu('detail', 'page'),
      { ...menu('second', undefined, 'catalog'), sortOrder: 20 },
      menu('second-page', 'second'),
    ]).navigationTree;
    expect(defaultExpandedKeys(tree)).toEqual(['root']);
    expect(allExpandableKeys(tree)).toEqual(['root', 'page', 'second']);
    expect(isFullyExpanded(['root', 'page'], allExpandableKeys(tree))).toBe(false);
    expect(isFullyExpanded(['root', 'page', 'second'], allExpandableKeys(tree))).toBe(true);
  });

  it('retains ancestors and returns keys to expand for search matches', () => {
    const tree = buildMenuWorkbench([
      menu('root', undefined, 'catalog'),
      { ...menu('page', 'root'), menuName: '用户管理' },
    ]).navigationTree;
    const result = filterMenuTree(tree, { keyword: '用户' });
    expect(result.tree[0]?.children?.[0]?.id).toBe('page');
    expect(result.ancestorKeys).toEqual(['root']);
  });

  it('reorders siblings with interval sort values', () => {
    const result = reorderSiblings(
      [
        { ...menu('a', 'root'), sortOrder: 10 },
        { ...menu('b', 'root'), sortOrder: 20 },
        { ...menu('c', 'root'), sortOrder: 30 },
      ],
      'c',
      'a',
      false,
    );
    expect(result.items.map((item) => [item.menu.id, item.sortOrder])).toEqual([
      ['c', 10],
      ['a', 20],
      ['b', 30],
    ]);
  });

  it('rejects cross-level reorder', () => {
    expect(reorderSiblings([menu('a', 'one'), menu('b', 'two')], 'a', 'b', false)).toEqual({
      error: 'cross-level',
      items: [],
    });
  });
});

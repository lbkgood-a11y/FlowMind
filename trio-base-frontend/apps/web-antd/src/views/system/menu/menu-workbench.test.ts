import type { SystemMenuApi } from '#/api';

import { describe, expect, it } from 'vitest';

import {
  allExpandableKeys,
  buildMenuWorkbench,
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

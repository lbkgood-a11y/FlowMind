<script lang="ts" setup>
import type { SystemMenuApi } from '#/api';
import type { TableProps } from 'ant-design-vue';

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';
import { IconifyIcon, Plus } from '@vben/icons';

import {
  AutoComplete,
  Button,
  Checkbox,
  Divider,
  Drawer,
  Empty,
  Form,
  FormItem,
  Input,
  InputNumber,
  message,
  Popconfirm,
  Popover,
  Radio,
  RadioGroup,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
} from 'ant-design-vue';

import {
  createMenu,
  deleteMenu,
  getMenuList,
  menuKeyExists,
  menuPathExists,
  updateMenu,
  updateMenuStatus,
} from '#/api';
import { componentKeys } from '#/router/routes';

type MenuFormModel = {
  activeIcon?: string;
  activePath?: string;
  affixTab: boolean;
  badge?: string;
  badgeType?: string;
  badgeVariant?: string;
  component?: string;
  hideChildrenInMenu: boolean;
  hideInBreadcrumb: boolean;
  hideInMenu: boolean;
  hideInTab: boolean;
  icon?: string;
  keepAlive: boolean;
  menuGroup: string;
  menuKey: string;
  menuName: string;
  menuType: SystemMenuApi.MenuType;
  parentId?: string;
  path?: string;
  permissionCode?: string;
  sortOrder: number;
  status: 0 | 1;
};

type MenuQueryModel = {
  keyword?: string;
  menuGroup?: string;
  menuType?: SystemMenuApi.MenuType;
  status?: 0 | 1;
};

type MenuColumnKey =
  | 'action'
  | 'auth'
  | 'component'
  | 'group'
  | 'path'
  | 'status'
  | 'title'
  | 'type';

type MenuColumnSetting = {
  key: MenuColumnKey;
  title: string;
  visible: boolean;
};

const menuTypeOptions: Array<{
  color: string;
  label: string;
  value: SystemMenuApi.MenuType;
}> = [
  { color: 'blue', label: '目录', value: 'catalog' },
  { color: 'default', label: '菜单', value: 'menu' },
  { color: 'pink', label: '按钮', value: 'button' },
  { color: 'green', label: '内嵌', value: 'embedded' },
  { color: 'orange', label: '外链', value: 'link' },
];

const defaultColumnSettings: MenuColumnSetting[] = [
  { key: 'title', title: '标题', visible: true },
  { key: 'type', title: '类型', visible: true },
  { key: 'group', title: '分组', visible: false },
  { key: 'auth', title: '权限标识', visible: true },
  { key: 'path', title: '路由地址', visible: true },
  { key: 'component', title: '页面组件', visible: true },
  { key: 'status', title: '状态', visible: true },
  { key: 'action', title: '操作', visible: true },
];

const legacyIconMap: Record<string, string> = {
  FilePlus2: 'lucide:file-plus-2',
  FileText: 'lucide:file-text',
  KeySquare: 'lucide:key-square',
  LayoutDashboard: 'lucide:layout-dashboard',
  ListTree: 'lucide:list-tree',
  Shield: 'lucide:shield',
  Users: 'lucide:users',
};

const menus = ref<SystemMenuApi.SystemMenu[]>([]);
const allMenus = ref<SystemMenuApi.SystemMenu[]>([]);
const loading = ref(false);
const saving = ref(false);
const drawerOpen = ref(false);
const collapsed = ref(true);
const queryHidden = ref(false);
const blockFullscreen = ref(false);
const columnSettingOpen = ref(false);
const tableKey = ref(0);
const expandedRowKeys = ref<Array<number | string>>([]);
const autoExpandRows = ref(true);
const editingMenu = ref<SystemMenuApi.SystemMenu>();
const hydratingForm = ref(false);

const queryForm = reactive<MenuQueryModel>({
  keyword: '',
  menuGroup: undefined,
  menuType: undefined,
  status: undefined,
});

const columnSettings = reactive<MenuColumnSetting[]>(
  defaultColumnSettings.map((item) => ({ ...item })),
);
const columnDraft = ref<MenuColumnSetting[]>(
  defaultColumnSettings.map((item) => ({ ...item })),
);

const formModel = reactive<MenuFormModel>({
  activeIcon: '',
  activePath: '',
  affixTab: false,
  badge: '',
  badgeType: undefined,
  badgeVariant: undefined,
  component: '',
  hideChildrenInMenu: false,
  hideInBreadcrumb: false,
  hideInMenu: false,
  hideInTab: false,
  icon: '',
  keepAlive: false,
  menuGroup: 'general',
  menuKey: '',
  menuName: '',
  menuType: 'menu',
  parentId: undefined,
  path: '',
  permissionCode: '',
  sortOrder: 100,
  status: 1,
});

const menuTree = computed(() => buildTree(menus.value));
const allMenuTree = computed(() => buildTree(allMenus.value));
const componentOptions = computed(() =>
  componentKeys
    .filter((key) => !key.includes('/_core/'))
    .map((key) => ({
      label: key,
      value: key,
    })),
);
const menuGroupOptions = computed(() => {
  const groups = new Set(['general', 'system', 'admin', 'forms']);
  allMenus.value.forEach((item) => {
    if (item.menuGroup) {
      groups.add(item.menuGroup);
    }
  });
  return [...groups].map((group) => ({ label: group, value: group }));
});
const componentPlaceholder = computed(() => {
  if (formModel.menuType === 'embedded') {
    return '请输入内嵌页面地址';
  }
  if (formModel.menuType === 'link') {
    return '请输入外链地址';
  }
  if (formModel.menuType === 'catalog') {
    return '目录无需页面组件';
  }
  if (formModel.menuType === 'button') {
    return '按钮无需页面组件';
  }
  return '请选择或输入页面组件';
});
const parentOptions = computed(() => {
  const excluded = new Set<string>();
  if (editingMenu.value) {
    collectIds(
      findMenuById(allMenuTree.value, editingMenu.value.id) ?? editingMenu.value,
      excluded,
    );
  }
  return flattenMenus(allMenuTree.value)
    .filter((item) => !excluded.has(item.id) && item.menuType !== 'button')
    .map((item) => ({
      label: `${'　'.repeat(item.level)}${item.menuName} (${getTypeMeta(item.menuType).label})`,
      value: item.id,
    }));
});

const allDraftChecked = computed({
  get: () => columnDraft.value.every((item) => item.visible),
  set: (checked: boolean) => {
    columnDraft.value.forEach((item) => {
      item.visible = checked;
    });
  },
});

const baseColumns: Record<MenuColumnKey, NonNullable<TableProps['columns']>[number]> =
  {
    action: {
      align: 'center',
      fixed: 'right',
      key: 'action',
      title: '操作',
      width: 210,
    },
    auth: {
      align: 'center',
      dataIndex: 'permissionCode',
      key: 'auth',
      title: '权限标识',
      width: 180,
    },
    component: {
      dataIndex: 'component',
      key: 'component',
      title: '页面组件',
      width: 320,
    },
    group: {
      align: 'center',
      dataIndex: 'menuGroup',
      key: 'group',
      title: '分组',
      width: 100,
    },
    path: { dataIndex: 'path', key: 'path', title: '路由地址', width: 220 },
    status: { align: 'center', key: 'status', title: '状态', width: 110 },
    title: {
      dataIndex: 'menuName',
      fixed: 'left',
      key: 'title',
      title: '标题',
      width: 280,
    },
    type: { align: 'center', key: 'type', title: '类型', width: 100 },
  };

const columns = computed<TableProps['columns']>(() =>
  columnSettings
    .filter((item) => item.visible)
    .map((item) => baseColumns[item.key]),
);

function asMenu(record: Record<string, any>) {
  return record as SystemMenuApi.SystemMenu;
}

function buildTree(list: SystemMenuApi.SystemMenu[]) {
  const nodeMap = new Map<string, SystemMenuApi.SystemMenu>();
  list.forEach((item) => {
    nodeMap.set(item.id, { ...item, children: [] });
  });

  const roots: SystemMenuApi.SystemMenu[] = [];
  nodeMap.forEach((node) => {
    if (node.parentId && nodeMap.has(node.parentId)) {
      nodeMap.get(node.parentId)?.children?.push(node);
    } else {
      roots.push(node);
    }
  });

  const sortNodes = (nodes: SystemMenuApi.SystemMenu[]) => {
    nodes.sort((a, b) => (a.sortOrder ?? 100) - (b.sortOrder ?? 100));
    nodes.forEach((node) => {
      if (node.children?.length) {
        sortNodes(node.children);
      } else {
        delete node.children;
      }
    });
  };
  sortNodes(roots);
  return roots;
}

function flattenMenus(
  list: SystemMenuApi.SystemMenu[],
  level = 0,
): Array<SystemMenuApi.SystemMenu & { level: number }> {
  return list.flatMap((item) => [
    { ...item, level },
    ...flattenMenus(item.children ?? [], level + 1),
  ]);
}

function collectIds(menu: SystemMenuApi.SystemMenu, ids: Set<string>) {
  ids.add(menu.id);
  menu.children?.forEach((child) => collectIds(child, ids));
}

function findMenuById(
  list: SystemMenuApi.SystemMenu[],
  id: string,
): SystemMenuApi.SystemMenu | undefined {
  for (const item of list) {
    if (item.id === id) {
      return item;
    }
    const matched = findMenuById(item.children ?? [], id);
    if (matched) {
      return matched;
    }
  }
}

function collectExpandedKeys(list: SystemMenuApi.SystemMenu[]) {
  const keys: string[] = [];
  list.forEach((item) => {
    if (item.children?.length) {
      keys.push(item.id);
      keys.push(...collectExpandedKeys(item.children));
    }
  });
  return keys;
}

function boolValue(value?: 0 | 1) {
  return value === 1;
}

function isExternalUrl(value?: string) {
  const normalized = value?.trim();
  return !!normalized && (normalized.startsWith('http://') || normalized.startsWith('https://'));
}

function getTypeMeta(type?: SystemMenuApi.MenuType) {
  return (
    menuTypeOptions.find((item) => item.value === type) ?? {
      color: 'default',
      label: '菜单',
      value: 'menu',
    }
  );
}

function displayComponent(record: SystemMenuApi.SystemMenu) {
  if (record.menuType === 'embedded' || record.menuType === 'link') {
    return record.component || record.description || '-';
  }
  return record.component || '-';
}

function resolveMenuIcon(record: SystemMenuApi.SystemMenu) {
  if (record.menuType === 'button') {
    return 'lucide:shield-check';
  }
  if (!record.icon) {
    return 'lucide:menu';
  }
  if (record.icon.includes(':')) {
    return record.icon;
  }
  return legacyIconMap[record.icon] ?? 'lucide:menu';
}

async function loadMenus() {
  loading.value = true;
  try {
    const params: SystemMenuApi.MenuListParams = {
      keyword: queryForm.keyword?.trim() || undefined,
      menuGroup: queryForm.menuGroup,
      menuType: queryForm.menuType,
      status: queryForm.status,
    };
    const hasQuery = Object.values(params).some((value) => value !== undefined);
    const [filteredMenus, fullMenus] = hasQuery
      ? await Promise.all([getMenuList(params), getMenuList()])
      : await getMenuList().then((items) => [items, items] as const);
    menus.value = filteredMenus;
    allMenus.value = fullMenus;
    const tree = buildTree(menus.value);
    expandedRowKeys.value = autoExpandRows.value ? collectExpandedKeys(tree) : [];
    tableKey.value += 1;
  } finally {
    loading.value = false;
  }
}

function resetQuery() {
  queryForm.keyword = '';
  queryForm.menuGroup = undefined;
  queryForm.menuType = undefined;
  queryForm.status = undefined;
  autoExpandRows.value = true;
  loadMenus();
}

function handleToolbarSearch() {
  queryHidden.value = true;
  loadMenus();
}

function resetForm(parentId?: string) {
  editingMenu.value = undefined;
  formModel.activeIcon = '';
  formModel.activePath = '';
  formModel.affixTab = false;
  formModel.badge = '';
  formModel.badgeType = undefined;
  formModel.badgeVariant = undefined;
  formModel.component = '';
  formModel.hideChildrenInMenu = false;
  formModel.hideInBreadcrumb = false;
  formModel.hideInMenu = false;
  formModel.hideInTab = false;
  formModel.icon = '';
  formModel.keepAlive = false;
  formModel.menuGroup = 'general';
  formModel.menuKey = '';
  formModel.menuName = '';
  formModel.menuType = 'menu';
  formModel.parentId = parentId;
  formModel.path = '';
  formModel.permissionCode = '';
  formModel.sortOrder = 100;
  formModel.status = 1;
}

function openCreate(parent?: SystemMenuApi.SystemMenu) {
  resetForm(parent?.id);
  drawerOpen.value = true;
}

function openEdit(record: SystemMenuApi.SystemMenu) {
  hydratingForm.value = true;
  editingMenu.value = record;
  formModel.activeIcon = record.activeIcon ?? '';
  formModel.activePath = record.activePath ?? '';
  formModel.affixTab = boolValue(record.affixTab);
  formModel.badge = record.badge ?? '';
  formModel.badgeType = record.badgeType;
  formModel.badgeVariant = record.badgeVariant;
  formModel.component = record.component ?? '';
  formModel.hideChildrenInMenu = boolValue(record.hideChildrenInMenu);
  formModel.hideInBreadcrumb = boolValue(record.hideInBreadcrumb);
  formModel.hideInMenu = boolValue(record.hideInMenu);
  formModel.hideInTab = boolValue(record.hideInTab);
  formModel.icon = record.icon ?? '';
  formModel.keepAlive = boolValue(record.keepAlive);
  formModel.menuGroup = record.menuGroup ?? 'general';
  formModel.menuKey = record.menuKey;
  formModel.menuName = record.menuName;
  formModel.menuType = record.menuType ?? 'menu';
  formModel.parentId = record.parentId || undefined;
  formModel.path = record.path ?? '';
  formModel.permissionCode = record.permissionCode ?? '';
  formModel.sortOrder = record.sortOrder ?? 100;
  formModel.status = record.status ?? record.visible ?? 1;
  drawerOpen.value = true;
  void nextTick(() => {
    hydratingForm.value = false;
  });
}

function validateForm() {
  if (!formModel.menuKey.trim()) {
    message.warning('请输入菜单名称');
    return false;
  }
  if (!formModel.menuName.trim()) {
    message.warning('请输入标题');
    return false;
  }
  if (formModel.menuType !== 'button' && !formModel.path?.trim()) {
    message.warning('请输入路由地址');
    return false;
  }
  if (formModel.menuType === 'menu' && !formModel.component?.trim()) {
    message.warning('请输入页面组件');
    return false;
  }
  if (
    (formModel.menuType === 'embedded' || formModel.menuType === 'link') &&
    !formModel.component?.trim()
  ) {
    message.warning('请输入页面地址');
    return false;
  }
  if (
    (formModel.menuType === 'embedded' || formModel.menuType === 'link') &&
    !isExternalUrl(formModel.component)
  ) {
    message.warning('请输入 http/https 开头的页面地址');
    return false;
  }
  if (formModel.menuType === 'button' && !formModel.permissionCode?.trim()) {
    message.warning('请输入按钮权限标识');
    return false;
  }
  return true;
}

function buildPayload(): SystemMenuApi.SaveMenuParams {
  const component =
    formModel.menuType === 'button' || formModel.menuType === 'catalog'
      ? undefined
      : formModel.component?.trim() || undefined;
  return {
    activeIcon: formModel.activeIcon || undefined,
    activePath: formModel.activePath || undefined,
    affixTab: formModel.affixTab,
    badge: formModel.badge || undefined,
    badgeType: formModel.badgeType || undefined,
    badgeVariant: formModel.badgeVariant || undefined,
    component,
    hideChildrenInMenu: formModel.hideChildrenInMenu,
    hideInBreadcrumb: formModel.hideInBreadcrumb,
    hideInMenu: formModel.menuType === 'button' ? true : formModel.hideInMenu,
    hideInTab: formModel.hideInTab,
    icon: formModel.icon || undefined,
    keepAlive: formModel.keepAlive,
    menuGroup: formModel.menuGroup?.trim() || undefined,
    menuKey: formModel.menuKey.trim(),
    menuName: formModel.menuName.trim(),
    menuType: formModel.menuType,
    parentId: formModel.parentId || undefined,
    path: formModel.menuType === 'button' ? undefined : formModel.path?.trim(),
    permissionCode: formModel.permissionCode?.trim() || undefined,
    sortOrder: formModel.sortOrder,
    status: formModel.status,
    visible: formModel.status === 1,
  };
}

async function validateUniqueFields() {
  const excludeId = editingMenu.value?.id;
  const menuKey = formModel.menuKey.trim();
  if (await menuKeyExists(menuKey, excludeId)) {
    message.warning('菜单名称已存在');
    return false;
  }

  const path = formModel.menuType === 'button' ? '' : formModel.path?.trim();
  if (path && (await menuPathExists(path, excludeId))) {
    message.warning('路由地址已存在');
    return false;
  }
  return true;
}

async function submitForm() {
  if (!validateForm()) {
    return;
  }
  saving.value = true;
  try {
    if (!(await validateUniqueFields())) {
      return;
    }
    const payload = buildPayload();
    if (editingMenu.value) {
      await updateMenu(editingMenu.value.id, payload);
      message.success('菜单已更新');
    } else {
      await createMenu(payload);
      message.success('菜单已创建');
    }
    drawerOpen.value = false;
    await loadMenus();
  } finally {
    saving.value = false;
  }
}

async function removeMenu(record: SystemMenuApi.SystemMenu) {
  await deleteMenu(record.id);
  message.success('菜单已删除');
  await loadMenus();
}

async function toggleStatus(record: SystemMenuApi.SystemMenu) {
  const nextStatus = (record.status ?? record.visible ?? 1) === 1 ? 0 : 1;
  await updateMenuStatus(record.id, nextStatus);
  message.success('状态已更新');
  await loadMenus();
}

function toggleFullscreen() {
  blockFullscreen.value = !blockFullscreen.value;
}

function cloneColumnSettings(source = columnSettings) {
  return source.map((item) => ({ ...item }));
}

function syncColumnDraft(open: boolean) {
  columnSettingOpen.value = open;
  if (open) {
    columnDraft.value = cloneColumnSettings();
  }
}

function restoreColumnSettings() {
  columnDraft.value = defaultColumnSettings.map((item) => ({ ...item }));
}

function cancelColumnSettings() {
  columnSettingOpen.value = false;
}

function confirmColumnSettings() {
  columnSettings.splice(0, columnSettings.length, ...cloneColumnSettings(columnDraft.value));
  tableKey.value += 1;
  columnSettingOpen.value = false;
}

function expandAll() {
  autoExpandRows.value = true;
  expandedRowKeys.value = collectExpandedKeys(menuTree.value);
}

function collapseAll() {
  autoExpandRows.value = false;
  expandedRowKeys.value = [];
}

function onExpandedRowsChange(keys: Array<number | string>) {
  expandedRowKeys.value = keys;
  autoExpandRows.value = keys.length > 0;
}

watch(
  () => formModel.menuType,
  (type, previousType) => {
    if (hydratingForm.value) {
      return;
    }
    if (type === 'button') {
      formModel.path = '';
      formModel.component = '';
      formModel.hideInMenu = true;
      return;
    }

    if (previousType === 'button' && formModel.hideInMenu) {
      formModel.hideInMenu = false;
    }

    if (type === 'catalog') {
      formModel.component = '';
      return;
    }

    if (type === 'menu' && (previousType === 'embedded' || previousType === 'link')) {
      formModel.component = '';
      return;
    }

    if ((type === 'embedded' || type === 'link') && !isExternalUrl(formModel.component)) {
      formModel.component = '';
    }
  },
);

onMounted(loadMenus);
</script>

<template>
  <Page auto-content-height>
    <div
      class="menu-page"
      :class="{ 'is-block-fullscreen': blockFullscreen, 'is-query-hidden': queryHidden }"
    >
      <section v-show="!queryHidden" class="query-panel">
        <div class="query-grid" :class="{ collapsed }">
          <FormItem label="关键词">
            <Input
              v-model:value="queryForm.keyword"
              allow-clear
              placeholder="名称/标题/路径/权限"
              @press-enter="loadMenus"
            />
          </FormItem>
          <FormItem label="菜单类型">
            <Select
              v-model:value="queryForm.menuType"
              allow-clear
              placeholder="请选择"
              :options="menuTypeOptions"
            />
          </FormItem>
          <FormItem label="状态">
            <Select
              v-model:value="queryForm.status"
              allow-clear
              placeholder="请选择"
              :options="[
                { label: '启用', value: 1 },
                { label: '禁用', value: 0 },
              ]"
            />
          </FormItem>
          <FormItem v-if="!collapsed" label="分组">
            <Select
              v-model:value="queryForm.menuGroup"
              allow-clear
              show-search
              placeholder="请选择"
              :options="menuGroupOptions"
            />
          </FormItem>
          <div class="query-actions">
            <Button @click="resetQuery">重置</Button>
            <Button type="primary" @click="loadMenus">搜索</Button>
            <Button type="link" @click="collapsed = !collapsed">
              {{ collapsed ? '展开' : '收起' }}
              <IconifyIcon
                :icon="collapsed ? 'lucide:chevron-down' : 'lucide:chevron-up'"
                class="ml-1 size-4"
              />
            </Button>
          </div>
        </div>
      </section>

      <section class="list-panel">
        <div class="list-header">
          <div class="list-title">
            <h2>菜单列表</h2>
            <Button v-if="queryHidden" type="link" @click="queryHidden = false">
              展开搜索
            </Button>
          </div>
          <Space :size="8">
            <Button type="primary" @click="openCreate()">
              <Plus class="size-4" />
              新增菜单
            </Button>
            <Tooltip title="查询并隐藏搜索栏">
              <Button shape="circle" type="primary" @click="handleToolbarSearch">
                <IconifyIcon icon="lucide:search" class="size-4" />
              </Button>
            </Tooltip>
            <Tooltip title="展开全部">
              <Button shape="circle" @click="expandAll">
                <IconifyIcon icon="lucide:unfold-vertical" class="size-4" />
              </Button>
            </Tooltip>
            <Tooltip title="收起全部">
              <Button shape="circle" @click="collapseAll">
                <IconifyIcon icon="lucide:fold-vertical" class="size-4" />
              </Button>
            </Tooltip>
            <Tooltip title="刷新">
              <Button shape="circle" @click="loadMenus">
                <IconifyIcon icon="lucide:refresh-cw" class="size-4" />
              </Button>
            </Tooltip>
            <Tooltip :title="blockFullscreen ? '还原' : '全屏'">
              <Button shape="circle" @click="toggleFullscreen">
                <IconifyIcon
                  :icon="blockFullscreen ? 'lucide:minimize' : 'lucide:expand'"
                  class="size-4"
                />
              </Button>
            </Tooltip>
            <Popover
              :open="columnSettingOpen"
              overlay-class-name="menu-column-popover"
              placement="bottomRight"
              trigger="click"
              @open-change="syncColumnDraft"
            >
              <template #content>
                <div class="column-settings">
                  <Checkbox v-model:checked="allDraftChecked" class="column-check-all">
                    全部
                  </Checkbox>
                  <div class="column-setting-list">
                    <div
                      v-for="item in columnDraft"
                      :key="item.key"
                      class="column-setting-item"
                    >
                      <Checkbox v-model:checked="item.visible" />
                      <IconifyIcon icon="lucide:grip-vertical" class="drag-icon" />
                      <span>{{ item.title }}</span>
                    </div>
                  </div>
                  <div class="column-setting-footer">
                    <Button type="link" @click="restoreColumnSettings">恢复默认</Button>
                    <Space>
                      <Button type="text" @click="cancelColumnSettings">取消</Button>
                      <Button type="link" @click="confirmColumnSettings">确认</Button>
                    </Space>
                  </div>
                </div>
              </template>
              <Tooltip title="列设置">
                <Button
                  :class="{ 'is-active': columnSettingOpen }"
                  class="column-setting-trigger"
                  shape="circle"
                >
                  <IconifyIcon icon="lucide:columns-3" class="size-4" />
                </Button>
              </Tooltip>
            </Popover>
          </Space>
        </div>

        <div class="table-frame">
          <Table
            :key="tableKey"
            v-model:expanded-row-keys="expandedRowKeys"
            :columns="columns"
            :data-source="menuTree"
            :loading="loading"
            :pagination="false"
            :scroll="{ x: 'max-content' }"
            bordered
            row-key="id"
            size="middle"
            @expanded-rows-change="onExpandedRowsChange"
          >
            <template #emptyText>
              <Empty description="暂无数据" />
            </template>

            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'title'">
                <div class="title-cell">
                  <IconifyIcon
                    :icon="resolveMenuIcon(asMenu(record))"
                    class="size-4"
                  />
                  <span>{{ record.menuName }}</span>
                </div>
              </template>

              <template v-else-if="column.key === 'type'">
                <Tag :color="getTypeMeta(asMenu(record).menuType).color">
                  {{ getTypeMeta(asMenu(record).menuType).label }}
                </Tag>
              </template>

              <template v-else-if="column.key === 'auth'">
                {{ record.permissionCode || record.permissionId || '-' }}
              </template>

              <template v-else-if="column.key === 'component'">
                {{ displayComponent(asMenu(record)) }}
              </template>

              <template v-else-if="column.key === 'status'">
                <Tag :color="(record.status ?? record.visible ?? 1) === 1 ? 'green' : 'red'">
                  {{ (record.status ?? record.visible ?? 1) === 1 ? '已启用' : '已禁用' }}
                </Tag>
              </template>

              <template v-else-if="column.key === 'action'">
                <Space>
                  <Button
                    v-if="asMenu(record).menuType !== 'button'"
                    size="small"
                    type="link"
                    @click="openCreate(asMenu(record))"
                  >
                    新增下级
                  </Button>
                  <Button size="small" type="link" @click="openEdit(asMenu(record))">
                    修改
                  </Button>
                  <Button size="small" type="link" @click="toggleStatus(asMenu(record))">
                    {{ (record.status ?? record.visible ?? 1) === 1 ? '禁用' : '启用' }}
                  </Button>
                  <Popconfirm
                    title="确认删除该菜单？"
                    ok-text="删除"
                    cancel-text="取消"
                    @confirm="removeMenu(asMenu(record))"
                  >
                    <Button danger size="small" type="link">删除</Button>
                  </Popconfirm>
                </Space>
              </template>
            </template>
          </Table>
        </div>
      </section>

      <Drawer
        v-model:open="drawerOpen"
        :title="editingMenu ? '修改菜单' : '新增菜单'"
        width="860"
        :destroy-on-close="false"
      >
        <Form :model="formModel" class="menu-form" layout="horizontal">
          <FormItem class="type-row" label="类型">
            <RadioGroup v-model:value="formModel.menuType" button-style="solid">
              <Radio
                v-for="item in menuTypeOptions"
                :key="item.value"
                :value="item.value"
              >
                {{ item.label }}
              </Radio>
            </RadioGroup>
          </FormItem>

          <div class="form-grid">
            <FormItem label="菜单名称" required>
              <Input v-model:value="formModel.menuKey" allow-clear placeholder="请输入" />
            </FormItem>
            <FormItem label="上级菜单">
              <Select
                v-model:value="formModel.parentId"
                allow-clear
                placeholder="请选择"
                :options="parentOptions"
              />
            </FormItem>
            <FormItem label="菜单分组">
              <AutoComplete
                v-model:value="formModel.menuGroup"
                allow-clear
                placeholder="请选择或输入"
                :options="menuGroupOptions"
              />
            </FormItem>
            <FormItem label="标题" required>
              <Input v-model:value="formModel.menuName" allow-clear placeholder="请输入" />
            </FormItem>
            <FormItem label="路由地址" :required="formModel.menuType !== 'button'">
              <Input
                v-model:value="formModel.path"
                allow-clear
                :disabled="formModel.menuType === 'button'"
                placeholder="请输入"
              />
            </FormItem>
            <FormItem label="激活路径">
              <Input v-model:value="formModel.activePath" allow-clear placeholder="请输入" />
            </FormItem>
            <FormItem label="图标">
              <Input v-model:value="formModel.icon" allow-clear placeholder="请选择" />
            </FormItem>
            <FormItem label="激活图标">
              <Input v-model:value="formModel.activeIcon" allow-clear placeholder="请选择" />
            </FormItem>
            <FormItem
              label="页面组件"
              :required="
                formModel.menuType === 'menu' ||
                formModel.menuType === 'embedded' ||
                formModel.menuType === 'link'
              "
            >
              <AutoComplete
                v-model:value="formModel.component"
                allow-clear
                :disabled="formModel.menuType === 'button' || formModel.menuType === 'catalog'"
                :options="componentOptions"
                :placeholder="componentPlaceholder"
              />
            </FormItem>
            <FormItem label="权限标识" :required="formModel.menuType === 'button'">
              <Input
                v-model:value="formModel.permissionCode"
                allow-clear
                placeholder="请输入"
              />
            </FormItem>
            <FormItem label="状态">
              <RadioGroup v-model:value="formModel.status" button-style="solid">
                <Radio :value="1">已启用</Radio>
                <Radio :value="0">已禁用</Radio>
              </RadioGroup>
            </FormItem>
            <FormItem label="徽标类型">
              <Select
                v-model:value="formModel.badgeType"
                allow-clear
                placeholder="请选择"
                :options="[
                  { label: '点状', value: 'dot' },
                  { label: '文本', value: 'normal' },
                ]"
              />
            </FormItem>
            <FormItem label="徽章内容">
              <Input v-model:value="formModel.badge" allow-clear placeholder="请输入" />
            </FormItem>
            <FormItem label="排序">
              <InputNumber v-model:value="formModel.sortOrder" class="w-full" :min="0" />
            </FormItem>
          </div>

          <Divider>其它设置</Divider>
          <div class="setting-grid">
            <Checkbox v-model:checked="formModel.keepAlive">缓存标签页</Checkbox>
            <Checkbox v-model:checked="formModel.affixTab">固定在标签</Checkbox>
            <Checkbox v-model:checked="formModel.hideInMenu">隐藏菜单</Checkbox>
            <Checkbox v-model:checked="formModel.hideChildrenInMenu">隐藏子菜单</Checkbox>
            <Checkbox v-model:checked="formModel.hideInBreadcrumb">在面包屑中隐藏</Checkbox>
            <Checkbox v-model:checked="formModel.hideInTab">在标签栏中隐藏</Checkbox>
          </div>
        </Form>

        <template #footer>
          <Space>
            <Button @click="drawerOpen = false">取消</Button>
            <Button :loading="saving" type="primary" @click="submitForm">确认</Button>
          </Space>
        </template>
      </Drawer>
    </div>
  </Page>
</template>

<style scoped>
.menu-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 100%;
  background: #f1f3f6;
}

.menu-page.is-block-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 1000;
  height: 100vh;
  padding: 12px;
  overflow: auto;
}

.menu-page.is-block-fullscreen .list-panel {
  flex: 1;
}

.query-panel,
.list-panel {
  background: #fff;
}

.query-panel {
  padding: 22px 8px 16px;
}

.query-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(260px, 1fr));
  column-gap: 52px;
  row-gap: 10px;
  align-items: center;
}

.query-grid.collapsed {
  grid-template-rows: auto;
}

.query-grid :deep(.ant-form-item) {
  margin-bottom: 0;
}

.query-grid :deep(.ant-form-item-label) {
  width: 112px;
  padding-right: 8px;
  font-weight: 600;
}

.query-grid :deep(.ant-form-item-control) {
  min-width: 0;
}

.query-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.list-panel {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  padding: 16px 8px 10px;
}

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.list-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.list-header h2 {
  margin: 0;
  color: #111827;
  font-size: 16px;
  font-weight: 700;
}

.column-setting-trigger.is-active {
  color: #3164f4;
  border-color: #3164f4;
}

.title-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.table-frame {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}

.table-frame :deep(.ant-table-wrapper),
.table-frame :deep(.ant-spin-nested-loading),
.table-frame :deep(.ant-spin-container) {
  height: 100%;
}

.table-frame :deep(.ant-table) {
  height: 100%;
  border-radius: 4px;
}

.table-frame :deep(.ant-table-container) {
  min-height: 660px;
  border-inline-start: 0 !important;
}

.table-frame :deep(.ant-table-thead > tr > th) {
  background: #f5f5f5;
  color: #2b3340;
  font-weight: 600;
}

.table-frame :deep(.ant-table-cell) {
  overflow-wrap: break-word;
}

.table-frame :deep(.ant-empty) {
  margin: 120px 0;
}

.menu-form :deep(.ant-form-item-label) {
  width: 112px;
  font-weight: 600;
}

.type-row {
  margin-bottom: 18px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(260px, 1fr));
  column-gap: 28px;
}

.setting-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(180px, 1fr));
  row-gap: 18px;
  padding: 8px 108px;
}

:global(.menu-column-popover .ant-popover-inner) {
  padding: 0;
  border-radius: 4px;
}

.column-settings {
  width: 220px;
}

.column-check-all {
  display: flex;
  align-items: center;
  height: 32px;
  padding: 0 14px;
  color: #3164f4;
  font-weight: 600;
}

.column-setting-list {
  padding-bottom: 6px;
}

.column-setting-item {
  display: grid;
  grid-template-columns: 18px 18px 1fr;
  align-items: center;
  height: 28px;
  padding: 0 12px;
  column-gap: 4px;
}

.column-setting-item:hover {
  background: #f6f8ff;
}

.drag-icon {
  color: #6b7280;
}

.column-setting-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 38px;
  padding: 0 10px;
  border-top: 1px solid #edf0f5;
}

@media (max-width: 900px) {
  .query-grid {
    grid-template-columns: repeat(2, minmax(240px, 1fr));
    column-gap: 24px;
  }

  .form-grid,
  .setting-grid {
    grid-template-columns: 1fr;
  }

  .setting-grid {
    padding: 8px 24px;
  }
}

@media (max-width: 760px) {
  .query-grid {
    grid-template-columns: 1fr;
  }

  .query-actions {
    justify-content: flex-start;
  }
}
</style>

<script lang="ts" setup>
import type { SystemMenuApi } from '#/api';
import type { TableProps } from 'ant-design-vue';

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';

import { IconPicker, Page } from '@vben/common-ui';
import { IconifyIcon, Plus } from '@vben/icons';

import {
  AutoComplete,
  Button,
  Checkbox,
  Divider,
  Empty,
  Form,
  FormItem,
  Input,
  InputNumber,
  message,
  Modal,
  Popconfirm,
  Popover,
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

type MenuColumnKey =
  | 'action'
  | 'auth'
  | 'component'
  | 'path'
  | 'status'
  | 'title'
  | 'type';

type MenuColumnSetting = {
  key: MenuColumnKey;
  title: string;
  visible: boolean;
};

type FieldKey =
  | 'activeIcon'
  | 'activePath'
  | 'affixTab'
  | 'badge'
  | 'badgeType'
  | 'badgeVariant'
  | 'component'
  | 'hideChildrenInMenu'
  | 'hideInBreadcrumb'
  | 'hideInMenu'
  | 'hideInTab'
  | 'icon'
  | 'keepAlive'
  | 'link'
  | 'path'
  | 'permissionCode';

const typeOptions: Array<{
  color: string;
  label: string;
  value: SystemMenuApi.MenuType;
}> = [
  { color: 'processing', label: '目录', value: 'catalog' },
  { color: 'default', label: '菜单', value: 'menu' },
  { color: 'error', label: '按钮', value: 'button' },
  { color: 'success', label: '内嵌', value: 'embedded' },
  { color: 'warning', label: '外链', value: 'link' },
];

const menuTypeRadioOptions = typeOptions.map(({ label, value }) => ({
  label,
  value,
}));

const defaultColumnSettings: MenuColumnSetting[] = [
  { key: 'title', title: '标题', visible: true },
  { key: 'type', title: '类型', visible: true },
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

const fieldVisibility: Record<SystemMenuApi.MenuType, FieldKey[]> = {
  button: ['permissionCode'],
  catalog: [
    'path',
    'icon',
    'activeIcon',
    'permissionCode',
    'badgeType',
    'badge',
    'badgeVariant',
    'hideInMenu',
    'hideChildrenInMenu',
    'hideInBreadcrumb',
    'hideInTab',
  ],
  embedded: [
    'path',
    'activePath',
    'icon',
    'activeIcon',
    'link',
    'permissionCode',
    'badgeType',
    'badge',
    'badgeVariant',
    'affixTab',
    'hideInMenu',
    'hideInBreadcrumb',
    'hideInTab',
  ],
  link: [
    'icon',
    'link',
    'badgeType',
    'badge',
    'badgeVariant',
    'hideInMenu',
  ],
  menu: [
    'path',
    'activePath',
    'icon',
    'activeIcon',
    'component',
    'permissionCode',
    'badgeType',
    'badge',
    'badgeVariant',
    'keepAlive',
    'affixTab',
    'hideInMenu',
    'hideChildrenInMenu',
    'hideInBreadcrumb',
    'hideInTab',
  ],
};

const menus = ref<SystemMenuApi.SystemMenu[]>([]);
const allMenus = ref<SystemMenuApi.SystemMenu[]>([]);
const loading = ref(false);
const saving = ref(false);
const formOpen = ref(false);
const blockFullscreen = ref(false);
const columnSettingOpen = ref(false);
const tableKey = ref(0);
const expandedRowKeys = ref<Array<number | string>>([]);
const autoExpandRows = ref(true);
const editingMenu = ref<SystemMenuApi.SystemMenu>();
const hydratingForm = ref(false);

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
  menuGroup: 'system',
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
const isEditing = computed(() => !!editingMenu.value);
const formTitle = computed(() => (isEditing.value ? '修改菜单' : '新增菜单'));
const visibleFields = computed(() => new Set(fieldVisibility[formModel.menuType]));
const hasAdvancedSettings = computed(() =>
  ['affixTab', 'hideChildrenInMenu', 'hideInBreadcrumb', 'hideInMenu', 'hideInTab', 'keepAlive'].some(
    (key) => showField(key as FieldKey),
  ),
);
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
      label: `${'  '.repeat(item.level)}${item.menuName} (${getTypeMeta(item.menuType).label})`,
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
      width: 190,
    },
    auth: {
      dataIndex: 'permissionCode',
      key: 'auth',
      title: '权限标识',
      width: 190,
    },
    component: {
      dataIndex: 'component',
      key: 'component',
      title: '页面组件',
      width: 360,
    },
    path: { dataIndex: 'path', key: 'path', title: '路由地址', width: 200 },
    status: { align: 'center', key: 'status', title: '状态', width: 100 },
    title: {
      dataIndex: 'menuName',
      fixed: 'left',
      key: 'title',
      title: '标题',
      width: 260,
    },
    type: { align: 'center', key: 'type', title: '类型', width: 100 },
  };

const columns = computed<TableProps['columns']>(() =>
  columnSettings
    .filter((item) => item.visible)
    .map((item) => baseColumns[item.key]),
);

function showField(key: FieldKey) {
  return visibleFields.value.has(key);
}

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
    typeOptions.find((item) => item.value === type) ?? {
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

function displayTitle(record: SystemMenuApi.SystemMenu) {
  return record.menuName || record.menuKey || '-';
}

function resolveMenuIcon(record: SystemMenuApi.SystemMenu) {
  if (record.menuType === 'button') {
    return 'lucide:shield-check';
  }
  if (!record.icon) {
    return record.menuType === 'catalog' ? 'lucide:folder' : 'lucide:menu';
  }
  if (record.icon.includes(':')) {
    return record.icon;
  }
  return legacyIconMap[record.icon] ?? 'lucide:menu';
}

async function loadMenus() {
  loading.value = true;
  try {
    const items = await getMenuList();
    menus.value = items;
    allMenus.value = items;
    const tree = buildTree(items);
    expandedRowKeys.value = autoExpandRows.value ? collectExpandedKeys(tree) : [];
    tableKey.value += 1;
  } finally {
    loading.value = false;
  }
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
  formModel.menuGroup = 'system';
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
  formOpen.value = true;
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
  formModel.menuGroup = record.menuGroup ?? 'system';
  formModel.menuKey = record.menuKey;
  formModel.menuName = record.menuName;
  formModel.menuType = record.menuType ?? 'menu';
  formModel.parentId = record.parentId || undefined;
  formModel.path = record.path ?? '';
  formModel.permissionCode = record.permissionCode ?? '';
  formModel.sortOrder = record.sortOrder ?? 100;
  formModel.status = record.status ?? record.visible ?? 1;
  formOpen.value = true;
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
  if (showField('path') && !formModel.path?.trim()) {
    message.warning('请输入路由地址');
    return false;
  }
  if (formModel.menuType === 'menu' && !formModel.component?.trim()) {
    message.warning('请输入页面组件');
    return false;
  }
  if (showField('link') && !formModel.component?.trim()) {
    message.warning('请输入链接地址');
    return false;
  }
  if (showField('link') && !isExternalUrl(formModel.component)) {
    message.warning('链接地址必须以 http:// 或 https:// 开头');
    return false;
  }
  if (showField('permissionCode') && formModel.menuType === 'button' && !formModel.permissionCode?.trim()) {
    message.warning('请输入权限标识');
    return false;
  }
  return true;
}

function buildPayload(): SystemMenuApi.SaveMenuParams {
  const isComponentVisible = showField('component') || showField('link');
  return {
    activeIcon: showField('activeIcon') ? formModel.activeIcon || undefined : undefined,
    activePath: showField('activePath') ? formModel.activePath || undefined : undefined,
    affixTab: showField('affixTab') ? formModel.affixTab : false,
    badge: showField('badge') ? formModel.badge || undefined : undefined,
    badgeType: showField('badgeType') ? formModel.badgeType || undefined : undefined,
    badgeVariant: showField('badgeVariant') ? formModel.badgeVariant || undefined : undefined,
    component: isComponentVisible ? formModel.component?.trim() || undefined : undefined,
    hideChildrenInMenu: showField('hideChildrenInMenu') ? formModel.hideChildrenInMenu : false,
    hideInBreadcrumb: showField('hideInBreadcrumb') ? formModel.hideInBreadcrumb : false,
    hideInMenu: formModel.menuType === 'button' ? true : showField('hideInMenu') && formModel.hideInMenu,
    hideInTab: showField('hideInTab') ? formModel.hideInTab : false,
    icon: showField('icon') ? formModel.icon || undefined : undefined,
    keepAlive: showField('keepAlive') ? formModel.keepAlive : false,
    menuGroup: formModel.menuGroup?.trim() || undefined,
    menuKey: formModel.menuKey.trim(),
    menuName: formModel.menuName.trim(),
    menuType: formModel.menuType,
    parentId: formModel.parentId || undefined,
    path: showField('path') ? formModel.path?.trim() : undefined,
    permissionCode: showField('permissionCode')
      ? formModel.permissionCode?.trim() || undefined
      : undefined,
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

  const path = showField('path') ? formModel.path?.trim() : '';
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
    formOpen.value = false;
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

    formModel.activeIcon = '';
    formModel.activePath = '';
    formModel.badge = '';
    formModel.badgeType = undefined;
    formModel.badgeVariant = undefined;
    formModel.component = '';
    formModel.icon = '';
    formModel.permissionCode = '';

    formModel.affixTab = false;
    formModel.hideChildrenInMenu = false;
    formModel.hideInBreadcrumb = false;
    formModel.hideInTab = false;
    formModel.keepAlive = false;

    if (type === 'button') {
      formModel.path = '';
      formModel.hideInMenu = true;
      return;
    }

    if (previousType === 'button') {
      formModel.hideInMenu = false;
    }
  },
);

onMounted(loadMenus);
</script>

<template>
  <Page auto-content-height>
    <div class="menu-page" :class="{ 'is-block-fullscreen': blockFullscreen }">
      <section class="list-panel">
        <div class="list-header">
          <div></div>
          <Space :size="10">
            <Button type="primary" @click="openCreate()">
              <Plus class="size-4" />
              新增菜单
            </Button>
            <Tooltip title="刷新">
              <Button shape="circle" @click="loadMenus">
                <IconifyIcon icon="lucide:refresh-cw" class="size-4" />
              </Button>
            </Tooltip>
            <Tooltip title="展开全部">
              <Button shape="circle" @click="expandAll">
                <IconifyIcon icon="lucide:scan-line" class="size-4" />
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
                  <IconifyIcon icon="lucide:layout-grid" class="size-4" />
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
            :scroll="{ x: 1280 }"
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
                    class="title-icon"
                  />
                  <span>{{ displayTitle(asMenu(record)) }}</span>
                </div>
              </template>

              <template v-else-if="column.key === 'type'">
                <Tag :color="getTypeMeta(asMenu(record).menuType).color" class="type-tag">
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
                <Tag :color="(record.status ?? record.visible ?? 1) === 1 ? 'success' : 'error'">
                  {{ (record.status ?? record.visible ?? 1) === 1 ? '已启用' : '已禁用' }}
                </Tag>
              </template>

              <template v-else-if="column.key === 'action'">
                <Space :size="12">
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

      <Modal
        v-model:open="formOpen"
        :body-style="{ padding: '14px 26px 0' }"
        :confirm-loading="saving"
        :destroy-on-close="false"
        :mask-closable="false"
        :title="formTitle"
        centered
        class="menu-edit-modal"
        width="790px"
        @ok="submitForm"
      >
        <Form :model="formModel" class="menu-form" layout="horizontal">
          <FormItem class="type-row" label="类型">
            <RadioGroup
              v-model:value="formModel.menuType"
              button-style="solid"
              option-type="button"
              :options="menuTypeRadioOptions"
            />
          </FormItem>

          <div class="form-grid">
            <FormItem label="菜单名称" required>
              <Input v-model:value="formModel.menuKey" placeholder="请输入" />
            </FormItem>
            <FormItem label="上级菜单">
              <Select
                v-model:value="formModel.parentId"
                allow-clear
                placeholder="请选择"
                show-search
                :options="parentOptions"
              />
            </FormItem>

            <FormItem label="标题" required>
              <Input v-model:value="formModel.menuName" placeholder="请输入">
                <template #addonAfter>
                  <span class="title-addon">新增</span>
                </template>
              </Input>
            </FormItem>
            <FormItem v-if="showField('path')" label="路由地址" required>
              <Input v-model:value="formModel.path" placeholder="请输入" />
            </FormItem>

            <FormItem v-if="showField('activePath')" label="激活路径">
              <Input v-model:value="formModel.activePath" placeholder="请输入" />
            </FormItem>
            <FormItem v-if="showField('icon')" label="图标">
              <IconPicker v-model="formModel.icon" prefix="lucide" />
            </FormItem>

            <FormItem v-if="showField('activeIcon')" label="激活图标">
              <IconPicker v-model="formModel.activeIcon" prefix="lucide" />
            </FormItem>
            <FormItem
              v-if="showField('component')"
              label="页面组件"
              :required="formModel.menuType === 'menu'"
            >
              <AutoComplete
                v-model:value="formModel.component"
                allow-clear
                :options="componentOptions"
                placeholder="请输入"
              />
            </FormItem>

            <FormItem v-if="showField('link')" label="链接地址" required>
              <Input v-model:value="formModel.component" placeholder="请输入" />
            </FormItem>
            <FormItem
              v-if="showField('permissionCode')"
              label="权限标识"
              :required="formModel.menuType === 'button'"
            >
              <Input v-model:value="formModel.permissionCode" placeholder="请输入" />
            </FormItem>

            <FormItem label="状态">
              <RadioGroup
                v-model:value="formModel.status"
                button-style="solid"
                option-type="button"
                :options="[
                  { label: '已启用', value: 1 },
                  { label: '已禁用', value: 0 },
                ]"
              />
            </FormItem>
            <FormItem v-if="showField('badgeType')" label="徽标类型">
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

            <FormItem v-if="showField('badge')" label="徽章内容">
              <Input
                v-model:value="formModel.badge"
                :disabled="formModel.badgeType !== 'normal'"
                placeholder="请输入"
              />
            </FormItem>
            <FormItem v-if="showField('badgeVariant')" label="徽标样式">
              <Select
                v-model:value="formModel.badgeVariant"
                allow-clear
                placeholder="请选择"
                :options="[
                  { label: 'default', value: 'default' },
                  { label: 'destructive', value: 'destructive' },
                  { label: 'primary', value: 'primary' },
                  { label: 'success', value: 'success' },
                  { label: 'warning', value: 'warning' },
                ]"
              />
            </FormItem>

            <FormItem class="compact-only" label="排序">
              <InputNumber v-model:value="formModel.sortOrder" :min="0" />
            </FormItem>
            <FormItem class="compact-only" label="分组">
              <AutoComplete
                v-model:value="formModel.menuGroup"
                allow-clear
                :options="menuGroupOptions"
                placeholder="请选择"
              />
            </FormItem>
          </div>

          <template v-if="hasAdvancedSettings">
            <Divider class="settings-divider">其它设置</Divider>
            <div class="setting-grid">
              <Checkbox v-if="showField('keepAlive')" v-model:checked="formModel.keepAlive">
                缓存标签页
              </Checkbox>
              <Checkbox v-if="showField('affixTab')" v-model:checked="formModel.affixTab">
                固定在标签
              </Checkbox>
              <Checkbox v-if="showField('hideInMenu')" v-model:checked="formModel.hideInMenu">
                隐藏菜单
              </Checkbox>
              <Checkbox
                v-if="showField('hideChildrenInMenu')"
                v-model:checked="formModel.hideChildrenInMenu"
              >
                隐藏子菜单
              </Checkbox>
              <Checkbox
                v-if="showField('hideInBreadcrumb')"
                v-model:checked="formModel.hideInBreadcrumb"
              >
                在面包屑中隐藏
              </Checkbox>
              <Checkbox v-if="showField('hideInTab')" v-model:checked="formModel.hideInTab">
                在标签栏中隐藏
              </Checkbox>
            </div>
          </template>
        </Form>
      </Modal>
    </div>
  </Page>
</template>

<style scoped>
.menu-page {
  min-height: 100%;
  padding: 12px;
  background: #f1f4f8;
}

.menu-page.is-block-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 1000;
  height: 100vh;
}

.list-panel {
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 120px);
  padding: 8px;
  background: #fff;
  border-radius: 6px;
}

.menu-page.is-block-fullscreen .list-panel {
  min-height: calc(100vh - 24px);
}

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 40px;
  margin-bottom: 8px;
}

.column-setting-trigger.is-active {
  color: #3164f4;
  border-color: #3164f4;
}

.table-frame {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
}

.table-frame :deep(.ant-table-wrapper),
.table-frame :deep(.ant-spin-nested-loading),
.table-frame :deep(.ant-spin-container) {
  height: 100%;
}

.table-frame :deep(.ant-table-container) {
  min-height: 660px;
}

.table-frame :deep(.ant-table-thead > tr > th) {
  height: 38px;
  padding: 8px 8px;
  color: #303645;
  font-weight: 600;
  background: #f6f6f7;
  border-bottom-color: #e5e7eb;
}

.table-frame :deep(.ant-table-tbody > tr > td) {
  height: 40px;
  padding: 6px 8px;
  color: #111827;
  border-bottom-color: #e5e7eb;
}

.table-frame :deep(.ant-table-cell)::before {
  background-color: #e5e7eb !important;
}

.table-frame :deep(.ant-btn-link) {
  height: 24px;
  padding: 0;
}

.table-frame :deep(.ant-empty) {
  margin: 120px 0;
}

.title-cell {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
}

.title-icon {
  flex: 0 0 auto;
  width: 17px;
  height: 17px;
  color: #26313f;
}

.type-tag {
  min-width: 40px;
  text-align: center;
}

:global(.menu-edit-modal .ant-modal-content) {
  height: 940px;
  padding: 0;
  overflow: hidden;
  border-radius: 2px;
}

:global(.menu-edit-modal .ant-modal-header) {
  height: 54px;
  padding: 16px 14px;
  margin: 0;
  border-bottom: 1px solid #e5e7eb;
}

:global(.menu-edit-modal .ant-modal-title) {
  color: #1f2937;
  font-size: 16px;
  font-weight: 600;
}

:global(.menu-edit-modal .ant-modal-close) {
  top: 12px;
}

:global(.menu-edit-modal .ant-modal-body) {
  height: 838px;
  overflow: hidden;
}

:global(.menu-edit-modal .ant-modal-footer) {
  height: 48px;
  padding: 8px 12px;
  margin: 0;
  border-top: 1px solid #e5e7eb;
}

.menu-form :deep(.ant-form-item) {
  margin-bottom: 18px;
}

.menu-form :deep(.ant-form-item-label) {
  width: 108px;
  padding-right: 8px;
  font-weight: 600;
}

.menu-form :deep(.ant-form-item-label > label) {
  color: #2d3440;
}

.menu-form :deep(.ant-form-item-label > label::after) {
  margin-inline: 6px 0;
}

.menu-form :deep(.ant-input),
.menu-form :deep(.ant-input-number),
.menu-form :deep(.ant-select-selector) {
  height: 32px;
  border-radius: 8px;
}

.menu-form :deep(.ant-radio-button-wrapper) {
  min-width: 58px;
  text-align: center;
}

.type-row {
  margin-bottom: 18px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 34px;
}

.title-addon {
  display: inline-block;
  min-width: 34px;
  text-align: center;
}

.compact-only {
  display: none;
}

.settings-divider {
  margin: 24px 0 24px;
  color: #303645;
  font-weight: 600;
}

.settings-divider :deep(.ant-divider-inner-text) {
  padding-inline: 14px;
}

.setting-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(180px, 1fr));
  row-gap: 18px;
  padding: 0 108px;
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
  :global(.menu-edit-modal .ant-modal) {
    max-width: calc(100vw - 24px);
  }

  .form-grid,
  .setting-grid {
    grid-template-columns: 1fr;
  }

  .setting-grid {
    padding: 0 24px;
  }
}
</style>

<script setup lang="ts">
import type { SystemMenuApi } from '#/api';

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';

import { useAccess } from '@vben/access';
import { IconPicker, Page } from '@vben/common-ui';
import { IconifyIcon, Plus } from '@vben/icons';

import {
  AutoComplete,
  Button,
  Checkbox,
  Descriptions,
  DescriptionsItem,
  Divider,
  Drawer,
  Empty,
  Form,
  FormItem,
  Input,
  InputNumber,
  message,
  Modal,
  Popconfirm,
  RadioGroup,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Tooltip,
  Tree,
} from 'ant-design-vue';

import {
  createMenu,
  deleteMenu,
  getMenuList,
  menuKeyExists,
  menuPathExists,
  updateMenu,
} from '#/api';
import { ERP_TOOLBAR_ICONS } from '#/constants/erp-toolbar';
import { componentKeys } from '#/router/routes';

import {
  allExpandableKeys,
  buildMenuTree,
  buildMenuWorkbench,
  defaultExpandedKeys,
  filterMenuTree,
  flattenMenuTree,
  isFullyExpanded,
  reorderSiblings,
} from './menu-workbench';

const MENU_PERMISSIONS = {
  create: '/api/v1/menus:POST',
  delete: '/api/v1/menus/*:DELETE',
  query: '/api/v1/menus:GET',
  update: '/api/v1/menus/*:PUT',
} as const;

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

const fieldVisibility: Record<SystemMenuApi.MenuType, FieldKey[]> = {
  button: ['permissionCode'],
  catalog: ['path', 'icon', 'activeIcon', 'permissionCode', 'badgeType', 'badge', 'badgeVariant', 'hideInMenu', 'hideChildrenInMenu', 'hideInBreadcrumb', 'hideInTab'],
  embedded: ['path', 'activePath', 'icon', 'activeIcon', 'link', 'permissionCode', 'badgeType', 'badge', 'badgeVariant', 'affixTab', 'hideInMenu', 'hideInBreadcrumb', 'hideInTab'],
  link: ['icon', 'link', 'badgeType', 'badge', 'badgeVariant', 'hideInMenu'],
  menu: ['path', 'activePath', 'icon', 'activeIcon', 'component', 'permissionCode', 'badgeType', 'badge', 'badgeVariant', 'keepAlive', 'affixTab', 'hideInMenu', 'hideChildrenInMenu', 'hideInBreadcrumb', 'hideInTab'],
};

const legacyIconMap: Record<string, string> = {
  FilePlus2: 'lucide:file-plus-2',
  FileText: 'lucide:file-text',
  KeySquare: 'lucide:key-square',
  LayoutDashboard: 'lucide:layout-dashboard',
  ListTree: 'lucide:list-tree',
  Shield: 'lucide:shield',
  Users: 'lucide:users',
};

const { hasAccessByCodes } = useAccess();
const canQuery = computed(() => hasAccessByCodes([MENU_PERMISSIONS.query]));
const canCreate = computed(() => hasAccessByCodes([MENU_PERMISSIONS.create]));
const canUpdate = computed(() => hasAccessByCodes([MENU_PERMISSIONS.update]));
const canDelete = computed(() => hasAccessByCodes([MENU_PERMISSIONS.delete]));

const allMenus = ref<SystemMenuApi.SystemMenu[]>([]);
const loading = ref(false);
const saving = ref(false);
const sorting = ref(false);
const formOpen = ref(false);
const editingMenu = ref<SystemMenuApi.SystemMenu>();
const selectedMenuId = ref<string>();
const expandedKeys = ref<Array<number | string>>([]);
const preSearchExpandedKeys = ref<Array<number | string>>();
const selectedKeys = computed(() => (selectedMenuId.value ? [selectedMenuId.value] : []));
const keyword = ref('');
const filterType = ref<SystemMenuApi.MenuType>();
const filterStatus = ref<0 | 1>();
const formSnapshot = ref('');
const hydratingForm = ref(false);

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

const workbench = computed(() => buildMenuWorkbench(allMenus.value));
const hasFilters = computed(
  () => !!keyword.value.trim() || filterType.value !== undefined || filterStatus.value !== undefined,
);
const filteredResult = computed(() =>
  filterMenuTree(workbench.value.navigationTree, {
    keyword: keyword.value,
    menuType: filterType.value,
    status: filterStatus.value,
  }),
);
const visibleNavigationTree = computed(() =>
  hasFilters.value ? filteredResult.value.tree : workbench.value.navigationTree,
);
const treeData = computed(() => toTreeData(visibleNavigationTree.value));
const expandableKeys = computed(() => allExpandableKeys(workbench.value.navigationTree));
const fullyExpanded = computed(() => isFullyExpanded(expandedKeys.value, expandableKeys.value));
const selectedMenu = computed(() =>
  allMenus.value.find((menu) => menu.id === selectedMenuId.value),
);
const selectedPermissions = computed(() =>
  selectedMenu.value
    ? workbench.value.permissionsByMenuId.get(selectedMenu.value.id) ?? []
    : [],
);
const visibleFields = computed(() => new Set(fieldVisibility[formModel.menuType]));
const formDirty = computed(() => formOpen.value && formSnapshot.value !== serializeForm());
const formTitle = computed(() => (editingMenu.value ? '编辑菜单配置' : '新增菜单'));
const componentOptions = computed(() =>
  componentKeys
    .filter((key) => !key.includes('/_core/'))
    .map((key) => ({ label: key, value: key })),
);
const menuGroupOptions = computed(() => {
  const groups = new Set(['admin', 'forms', 'general', 'lowcode', 'process', 'system']);
  allMenus.value.forEach((menu) => menu.menuGroup && groups.add(menu.menuGroup));
  return [...groups].map((group) => ({ label: group, value: group }));
});
const parentOptions = computed(() =>
  flattenMenuTree(buildMenuTree(allMenus.value.filter((menu) => menu.menuType !== 'button')))
    .filter((menu: SystemMenuApi.SystemMenu & { level: number }) => menu.id !== editingMenu.value?.id)
    .map((menu: SystemMenuApi.SystemMenu & { level: number }) => ({
      label: `${'　'.repeat(menu.level)}${menu.menuName}`,
      value: menu.id,
    })),
);

const permissionColumns = [
  { dataIndex: 'menuName', key: 'name', title: '权限名称', width: 150 },
  { dataIndex: 'permissionCode', ellipsis: true, key: 'code', title: '权限标识' },
  { key: 'status', title: '状态', width: 80 },
  { key: 'action', title: '操作', width: 130 },
];

function showField(key: FieldKey) {
  return visibleFields.value.has(key);
}

function asMenu(record: Record<string, any>) {
  return record as SystemMenuApi.SystemMenu;
}

function parentMenuName(menu: SystemMenuApi.SystemMenu) {
  return allMenus.value.find((item) => item.id === menu.parentId)?.menuName || '无';
}

function typeMeta(type?: SystemMenuApi.MenuType) {
  return typeOptions.find((item) => item.value === type) ?? typeOptions[1]!;
}

function boolValue(value?: 0 | 1) {
  return value === 1;
}

function resolveMenuIcon(menu: SystemMenuApi.SystemMenu) {
  if (!menu.icon) {
    return menu.menuType === 'catalog' ? 'lucide:folder' : 'lucide:menu';
  }
  return menu.icon.includes(':') ? menu.icon : legacyIconMap[menu.icon] ?? 'lucide:menu';
}

function toTreeData(nodes: SystemMenuApi.SystemMenu[]): any[] {
  return nodes.map((node) => ({
    children: node.children?.length ? toTreeData(node.children) : undefined,
    key: node.id,
    menu: node,
    title: node.menuName,
  }));
}

async function loadMenus(options: { preserveSelection?: boolean } = {}) {
  if (!canQuery.value) {
    allMenus.value = [];
    return;
  }
  if (formDirty.value && !(await confirmDiscard())) {
    return;
  }
  loading.value = true;
  try {
    allMenus.value = await getMenuList();
    const validSelection = allMenus.value.some((menu) => menu.id === selectedMenuId.value);
    if (!options.preserveSelection || !validSelection) {
      selectedMenuId.value = workbench.value.navigationTree[0]?.id;
    }
    expandedKeys.value = defaultExpandedKeys(workbench.value.navigationTree);
  } finally {
    loading.value = false;
  }
}

async function selectMenu(keys: Array<number | string>) {
  const nextId = keys[0] ? String(keys[0]) : undefined;
  if (nextId === selectedMenuId.value) {
    return;
  }
  if (formDirty.value && !(await confirmDiscard())) {
    return;
  }
  formOpen.value = false;
  selectedMenuId.value = nextId;
}

function toggleExpandAll() {
  expandedKeys.value = fullyExpanded.value
    ? defaultExpandedKeys(workbench.value.navigationTree)
    : expandableKeys.value;
}

function resetForm(parentId?: string, menuType: SystemMenuApi.MenuType = 'menu') {
  editingMenu.value = undefined;
  Object.assign(formModel, {
    activeIcon: '', activePath: '', affixTab: false, badge: '', badgeType: undefined,
    badgeVariant: undefined, component: '', hideChildrenInMenu: false,
    hideInBreadcrumb: false, hideInMenu: menuType === 'button', hideInTab: false,
    icon: '', keepAlive: false, menuGroup: selectedMenu.value?.menuGroup ?? 'system',
    menuKey: '', menuName: '', menuType, parentId, path: '', permissionCode: '',
    sortOrder: 100, status: 1,
  });
  snapshotForm();
}

function hydrateForm(menu: SystemMenuApi.SystemMenu) {
  hydratingForm.value = true;
  editingMenu.value = menu;
  Object.assign(formModel, {
    activeIcon: menu.activeIcon ?? '', activePath: menu.activePath ?? '',
    affixTab: boolValue(menu.affixTab), badge: menu.badge ?? '', badgeType: menu.badgeType,
    badgeVariant: menu.badgeVariant, component: menu.component ?? '',
    hideChildrenInMenu: boolValue(menu.hideChildrenInMenu),
    hideInBreadcrumb: boolValue(menu.hideInBreadcrumb), hideInMenu: boolValue(menu.hideInMenu),
    hideInTab: boolValue(menu.hideInTab), icon: menu.icon ?? '', keepAlive: boolValue(menu.keepAlive),
    menuGroup: menu.menuGroup ?? 'system', menuKey: menu.menuKey, menuName: menu.menuName,
    menuType: menu.menuType ?? 'menu', parentId: menu.parentId || undefined,
    path: menu.path ?? '', permissionCode: menu.permissionCode ?? '',
    sortOrder: menu.sortOrder ?? 100, status: menu.status ?? menu.visible ?? 1,
  });
  snapshotForm();
  void nextTick(() => (hydratingForm.value = false));
}

function openCreate(parent = selectedMenu.value, menuType: SystemMenuApi.MenuType = 'menu') {
  if (!canCreate.value) {
    message.warning('当前账号没有新增菜单的权限');
    return;
  }
  resetForm(parent?.id, menuType);
  formOpen.value = true;
}

function openEdit(menu = selectedMenu.value) {
  if (!menu || !canUpdate.value) {
    return;
  }
  hydrateForm(menu);
  formOpen.value = true;
}

function serializeForm() {
  return JSON.stringify(formModel);
}

function snapshotForm() {
  formSnapshot.value = serializeForm();
}

function confirmDiscard() {
  return new Promise<boolean>((resolve) => {
    Modal.confirm({
      cancelText: '继续编辑',
      content: '当前修改尚未保存，确定放弃吗？',
      okText: '放弃修改',
      title: '未保存的修改',
      onCancel: () => resolve(false),
      onOk: () => resolve(true),
    });
  });
}

async function closeEditor() {
  if (formDirty.value && !(await confirmDiscard())) {
    return;
  }
  formOpen.value = false;
}

function isExternalUrl(value?: string) {
  return !!value?.trim().match(/^https?:\/\//);
}

function validateForm() {
  if (!formModel.menuKey.trim()) return message.warning('请输入菜单标识'), false;
  if (!formModel.menuName.trim()) return message.warning('请输入显示名称'), false;
  if (showField('path') && !formModel.path?.trim()) return message.warning('请输入路由地址'), false;
  if (formModel.menuType === 'menu' && !formModel.component?.trim()) return message.warning('请输入页面组件'), false;
  if (showField('link') && !isExternalUrl(formModel.component)) return message.warning('链接地址必须以 http:// 或 https:// 开头'), false;
  if (formModel.menuType === 'button' && !formModel.permissionCode?.trim()) return message.warning('请输入权限标识'), false;
  return true;
}

function buildPayload(model = formModel): SystemMenuApi.SaveMenuParams {
  const fields = new Set(fieldVisibility[model.menuType]);
  const visible = (key: FieldKey) => fields.has(key);
  return {
    activeIcon: visible('activeIcon') ? model.activeIcon || undefined : undefined,
    activePath: visible('activePath') ? model.activePath || undefined : undefined,
    affixTab: visible('affixTab') && model.affixTab,
    badge: visible('badge') ? model.badge || undefined : undefined,
    badgeType: visible('badgeType') ? model.badgeType : undefined,
    badgeVariant: visible('badgeVariant') ? model.badgeVariant : undefined,
    component: visible('component') || visible('link') ? model.component?.trim() || undefined : undefined,
    hideChildrenInMenu: visible('hideChildrenInMenu') && model.hideChildrenInMenu,
    hideInBreadcrumb: visible('hideInBreadcrumb') && model.hideInBreadcrumb,
    hideInMenu: model.menuType === 'button' || (visible('hideInMenu') && model.hideInMenu),
    hideInTab: visible('hideInTab') && model.hideInTab,
    icon: visible('icon') ? model.icon || undefined : undefined,
    keepAlive: visible('keepAlive') && model.keepAlive,
    menuGroup: model.menuGroup.trim() || undefined,
    menuKey: model.menuKey.trim(),
    menuName: model.menuName.trim(),
    menuType: model.menuType,
    parentId: model.parentId || undefined,
    path: visible('path') ? model.path?.trim() : undefined,
    permissionCode: visible('permissionCode') ? model.permissionCode?.trim() || undefined : undefined,
    sortOrder: model.sortOrder,
    status: model.status,
    visible: model.status === 1,
  };
}

function payloadFromMenu(menu: SystemMenuApi.SystemMenu, sortOrder = menu.sortOrder ?? 100) {
  return buildPayload({
    activeIcon: menu.activeIcon, activePath: menu.activePath, affixTab: boolValue(menu.affixTab),
    badge: menu.badge, badgeType: menu.badgeType, badgeVariant: menu.badgeVariant,
    component: menu.component, hideChildrenInMenu: boolValue(menu.hideChildrenInMenu),
    hideInBreadcrumb: boolValue(menu.hideInBreadcrumb), hideInMenu: boolValue(menu.hideInMenu),
    hideInTab: boolValue(menu.hideInTab), icon: menu.icon, keepAlive: boolValue(menu.keepAlive),
    menuGroup: menu.menuGroup ?? 'system', menuKey: menu.menuKey, menuName: menu.menuName,
    menuType: menu.menuType ?? 'menu', parentId: menu.parentId, path: menu.path,
    permissionCode: menu.permissionCode, sortOrder, status: menu.status ?? menu.visible ?? 1,
  });
}

async function submitForm() {
  if (!validateForm()) return;
  saving.value = true;
  try {
    const excludeId = editingMenu.value?.id;
    if (await menuKeyExists(formModel.menuKey.trim(), excludeId)) {
      message.warning('菜单标识已存在');
      return;
    }
    if (showField('path') && formModel.path?.trim() && await menuPathExists(formModel.path.trim(), excludeId)) {
      message.warning('路由地址已存在');
      return;
    }
    const saved = editingMenu.value
      ? await updateMenu(editingMenu.value.id, buildPayload())
      : await createMenu(buildPayload());
    message.success(editingMenu.value ? '菜单已更新' : '菜单已创建');
    formOpen.value = false;
    selectedMenuId.value = saved.id;
    await loadMenus({ preserveSelection: true });
  } finally {
    saving.value = false;
  }
}

async function removeMenu(menu: SystemMenuApi.SystemMenu) {
  await deleteMenu(menu.id);
  message.success('菜单已删除');
  if (selectedMenuId.value === menu.id) selectedMenuId.value = menu.parentId;
  await loadMenus({ preserveSelection: true });
}

async function handleDrop(info: any) {
  if (sorting.value || !info.dropToGap) {
    message.warning('仅支持同级排序；调整上级请编辑“上级菜单”');
    return;
  }
  const dragId = String(info.dragNode.eventKey);
  const targetId = String(info.node.eventKey);
  const result = reorderSiblings(
    allMenus.value,
    dragId,
    targetId,
    info.dropPosition > Number(info.node.pos.split('-').at(-1)),
  );
  if ('error' in result) {
    message.warning('不能跨层级拖拽，请通过“上级菜单”字段移动');
    return;
  }
  sorting.value = true;
  try {
    for (const item of result.items) {
      const menu = item.menu;
      const nextSort = item.sortOrder;
      if ((menu.sortOrder ?? 100) !== nextSort) {
        await updateMenu(menu.id, payloadFromMenu(menu, nextSort));
      }
    }
    message.success('菜单顺序已更新');
  } catch {
    message.error('部分排序保存失败，已重新加载服务端数据');
  } finally {
    sorting.value = false;
    await loadMenus({ preserveSelection: true });
  }
}

watch(hasFilters, (active, previous) => {
  if (active && !previous) preSearchExpandedKeys.value = [...expandedKeys.value];
  if (!active && previous) {
    expandedKeys.value = preSearchExpandedKeys.value ?? defaultExpandedKeys(workbench.value.navigationTree);
    preSearchExpandedKeys.value = undefined;
  }
});

watch(
  () => filteredResult.value.ancestorKeys,
  (keys) => {
    if (hasFilters.value) expandedKeys.value = [...new Set([...expandedKeys.value.map(String), ...keys])];
  },
);

watch(
  () => formModel.menuType,
  (type) => {
    if (hydratingForm.value) return;
    formModel.hideInMenu = type === 'button';
    if (type === 'button') {
      formModel.path = '';
      formModel.component = '';
    }
  },
);

onMounted(loadMenus);
</script>

<template>
  <Page auto-content-height>
    <div class="erp-compact-page menu-page">
      <header class="workbench-toolbar">
        <div class="toolbar-title">
          <h2>菜单管理</h2>
          <span>维护导航结构、路由和附属权限</span>
        </div>
        <div class="toolbar-filters">
          <Input v-model:value="keyword" allow-clear class="keyword-input" placeholder="搜索显示名称或菜单标识" />
          <Select v-model:value="filterType" allow-clear class="filter-select" :options="typeOptions" placeholder="类型" />
          <Select
            v-model:value="filterStatus"
            allow-clear
            class="filter-select"
            :options="[{ label: '已启用', value: 1 }, { label: '已禁用', value: 0 }]"
            placeholder="状态"
          />
        </div>
        <Space class="workbench-actions" :size="8">
          <Tooltip :title="fullyExpanded ? '收起全部' : '展开全部'">
            <Button shape="circle" @click="toggleExpandAll">
              <IconifyIcon :icon="fullyExpanded ? ERP_TOOLBAR_ICONS.collapse : ERP_TOOLBAR_ICONS.expandAll" class="size-4" />
            </Button>
          </Tooltip>
          <Tooltip title="刷新">
            <Button shape="circle" @click="loadMenus()">
              <IconifyIcon :icon="ERP_TOOLBAR_ICONS.refresh" class="size-4" />
            </Button>
          </Tooltip>
          <Button v-if="canCreate" type="primary" @click="openCreate(selectedMenu)">
            <Plus class="size-4" />新增菜单
          </Button>
        </Space>
      </header>

      <div class="menu-workbench">
        <aside class="navigation-panel">
          <div class="panel-heading">
            <div><strong>导航结构</strong><span>{{ workbench.navigationTree.length }} 个一级节点</span></div>
            <Tag v-if="workbench.unassignedPermissions.length" color="orange">
              未归类权限 {{ workbench.unassignedPermissions.length }}
            </Tag>
          </div>
          <div class="navigation-tree-scroll">
            <Spin :spinning="loading">
              <Tree
                v-if="treeData.length"
                v-model:expanded-keys="expandedKeys"
                :draggable="!sorting"
                :selected-keys="selectedKeys"
                :tree-data="treeData"
                block-node
                @drop="handleDrop"
                @select="selectMenu"
              >
                <template #title="{ menu }">
                  <div class="tree-node-title">
                    <IconifyIcon :icon="resolveMenuIcon(menu)" class="tree-node-icon" />
                    <span>{{ menu.menuName }}</span>
                    <Tag :color="typeMeta(menu.menuType).color">{{ typeMeta(menu.menuType).label }}</Tag>
                  </div>
                </template>
              </Tree>
              <Empty v-else description="没有匹配的导航节点" />
            </Spin>
          </div>
        </aside>

        <main class="detail-panel">
          <template v-if="selectedMenu">
            <div class="detail-header">
              <div class="detail-title">
                <IconifyIcon :icon="resolveMenuIcon(selectedMenu)" />
                <div><h3>{{ selectedMenu.menuName }}</h3><code>{{ selectedMenu.menuKey }}</code></div>
                <Tag :color="typeMeta(selectedMenu.menuType).color">{{ typeMeta(selectedMenu.menuType).label }}</Tag>
              </div>
              <Space>
                <Button v-if="canCreate && selectedMenu.menuType !== 'button'" @click="openCreate(selectedMenu)">新增下级</Button>
                <Button v-if="canUpdate" type="primary" @click="openEdit(selectedMenu)">编辑</Button>
                <Popconfirm v-if="canDelete" title="确认删除该菜单？" @confirm="removeMenu(selectedMenu)">
                  <Button danger>删除</Button>
                </Popconfirm>
              </Space>
            </div>

            <section class="detail-section">
              <h4>基本信息</h4>
              <Descriptions :column="2" bordered size="small">
                <DescriptionsItem label="菜单标识">{{ selectedMenu.menuKey }}</DescriptionsItem>
                <DescriptionsItem label="显示名称">{{ selectedMenu.menuName }}</DescriptionsItem>
                <DescriptionsItem label="状态"><Tag :color="(selectedMenu.status ?? 1) === 1 ? 'green' : 'default'">{{ (selectedMenu.status ?? 1) === 1 ? '已启用' : '已禁用' }}</Tag></DescriptionsItem>
                <DescriptionsItem label="排序">{{ selectedMenu.sortOrder ?? 100 }}</DescriptionsItem>
                <DescriptionsItem label="分组">{{ selectedMenu.menuGroup || '-' }}</DescriptionsItem>
                <DescriptionsItem label="上级菜单">{{ parentMenuName(selectedMenu) }}</DescriptionsItem>
              </Descriptions>
            </section>

            <section class="detail-section">
              <h4>路由配置</h4>
              <Descriptions :column="2" bordered size="small">
                <DescriptionsItem label="路由地址">{{ selectedMenu.path || '-' }}</DescriptionsItem>
                <DescriptionsItem label="页面组件">{{ selectedMenu.component || '-' }}</DescriptionsItem>
                <DescriptionsItem label="激活路径">{{ selectedMenu.activePath || '-' }}</DescriptionsItem>
                <DescriptionsItem label="权限标识">{{ selectedMenu.permissionCode || '-' }}</DescriptionsItem>
              </Descriptions>
            </section>

            <section class="detail-section">
              <div class="section-heading">
                <h4>权限配置 <Tag>{{ selectedPermissions.length }}</Tag></h4>
                <Button v-if="canCreate" size="small" type="link" @click="openCreate(selectedMenu, 'button')">新增权限</Button>
              </div>
              <Table :columns="permissionColumns" :data-source="selectedPermissions" :pagination="false" row-key="id" size="small">
                <template #emptyText><Empty description="当前菜单暂无按钮或 API 权限" /></template>
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'status'"><Tag :color="record.status === 0 ? 'default' : 'green'">{{ record.status === 0 ? '禁用' : '启用' }}</Tag></template>
                  <template v-else-if="column.key === 'action'">
                    <Space :size="4">
                      <Button v-if="canUpdate" size="small" type="link" @click="openEdit(asMenu(record))">编辑</Button>
                      <Popconfirm v-if="canDelete" title="确认删除该权限？" @confirm="removeMenu(asMenu(record))"><Button danger size="small" type="link">删除</Button></Popconfirm>
                    </Space>
                  </template>
                </template>
              </Table>
            </section>

            <section class="detail-section">
              <h4>显示设置</h4>
              <div class="setting-tags">
                <Tag>{{ selectedMenu.keepAlive ? '缓存标签页' : '不缓存' }}</Tag>
                <Tag>{{ selectedMenu.affixTab ? '固定标签' : '不固定' }}</Tag>
                <Tag>{{ selectedMenu.hideInMenu ? '隐藏菜单' : '显示菜单' }}</Tag>
                <Tag>{{ selectedMenu.hideChildrenInMenu ? '隐藏子菜单' : '显示子菜单' }}</Tag>
              </div>
            </section>
          </template>
          <Empty v-else class="detail-empty" description="从左侧选择一个菜单节点查看详情" />
        </main>
      </div>

      <Drawer
        :open="formOpen"
        :title="formTitle"
        class="menu-edit-drawer"
        :mask-closable="false"
        width="760"
        @close="closeEditor"
      >
        <Form :model="formModel" class="menu-form" layout="vertical">
          <section class="form-section">
            <h4>基本信息</h4>
            <div class="form-grid">
              <FormItem class="form-wide" label="类型"><RadioGroup v-model:value="formModel.menuType" button-style="solid" option-type="button" :options="typeOptions" /></FormItem>
              <FormItem label="菜单标识" required><Input v-model:value="formModel.menuKey" placeholder="例如 SystemUser" /></FormItem>
              <FormItem label="显示名称" required><Input v-model:value="formModel.menuName" placeholder="例如 用户管理" /></FormItem>
              <FormItem label="上级菜单"><Select v-model:value="formModel.parentId" allow-clear show-search :options="parentOptions" placeholder="请选择" /></FormItem>
              <FormItem label="状态"><RadioGroup v-model:value="formModel.status" option-type="button" :options="[{ label: '已启用', value: 1 }, { label: '已禁用', value: 0 }]" /></FormItem>
              <FormItem label="排序"><InputNumber v-model:value="formModel.sortOrder" class="w-full" :min="0" /></FormItem>
              <FormItem label="分组"><AutoComplete v-model:value="formModel.menuGroup" :options="menuGroupOptions" /></FormItem>
            </div>
          </section>

          <section v-if="showField('path') || showField('component') || showField('link')" class="form-section">
            <h4>路由配置</h4>
            <div class="form-grid">
              <FormItem v-if="showField('path')" label="路由地址" required><Input v-model:value="formModel.path" /></FormItem>
              <FormItem v-if="showField('activePath')" label="激活路径"><Input v-model:value="formModel.activePath" /></FormItem>
              <FormItem v-if="showField('component')" label="页面组件" :required="formModel.menuType === 'menu'"><AutoComplete v-model:value="formModel.component" :options="componentOptions" /></FormItem>
              <FormItem v-if="showField('link')" label="链接地址" required><Input v-model:value="formModel.component" placeholder="https://" /></FormItem>
            </div>
          </section>

          <section v-if="showField('permissionCode')" class="form-section">
            <h4>权限配置</h4>
            <FormItem label="权限标识" :required="formModel.menuType === 'button'"><Input v-model:value="formModel.permissionCode" placeholder="/api/v1/resource:ACTION" /></FormItem>
          </section>

          <section v-if="formModel.menuType !== 'button'" class="form-section">
            <h4>显示设置</h4>
            <div class="form-grid">
              <FormItem v-if="showField('icon')" label="图标"><IconPicker v-model="formModel.icon" prefix="lucide" /></FormItem>
              <FormItem v-if="showField('activeIcon')" label="激活图标"><IconPicker v-model="formModel.activeIcon" prefix="lucide" /></FormItem>
              <FormItem v-if="showField('badgeType')" label="徽标类型"><Select v-model:value="formModel.badgeType" allow-clear :options="[{ label: '点状', value: 'dot' }, { label: '文本', value: 'normal' }]" /></FormItem>
              <FormItem v-if="showField('badge')" label="徽标内容"><Input v-model:value="formModel.badge" :disabled="formModel.badgeType !== 'normal'" /></FormItem>
            </div>
            <Divider />
            <Space wrap>
              <Checkbox v-if="showField('keepAlive')" v-model:checked="formModel.keepAlive">缓存标签页</Checkbox>
              <Checkbox v-if="showField('affixTab')" v-model:checked="formModel.affixTab">固定在标签</Checkbox>
              <Checkbox v-if="showField('hideInMenu')" v-model:checked="formModel.hideInMenu">隐藏菜单</Checkbox>
              <Checkbox v-if="showField('hideChildrenInMenu')" v-model:checked="formModel.hideChildrenInMenu">隐藏子菜单</Checkbox>
              <Checkbox v-if="showField('hideInBreadcrumb')" v-model:checked="formModel.hideInBreadcrumb">在面包屑中隐藏</Checkbox>
              <Checkbox v-if="showField('hideInTab')" v-model:checked="formModel.hideInTab">在标签栏中隐藏</Checkbox>
            </Space>
          </section>
        </Form>
        <template #footer><Space><Button @click="closeEditor">取消</Button><Button :loading="saving" type="primary" @click="submitForm">保存</Button></Space></template>
      </Drawer>
    </div>
  </Page>
</template>

<style scoped>
/* stylelint-disable declaration-block-single-line-max-declarations */
.menu-page { display: flex; flex-direction: column; gap: var(--erp-panel-gap); height: calc(100dvh - 112px); min-height: 480px; padding: 0; }

.workbench-toolbar { display: grid; grid-template-columns: minmax(180px, auto) minmax(360px, 1fr) auto; gap: 8px; align-items: center; min-height: 44px; padding: 6px 8px; background: hsl(var(--card)); border: var(--erp-panel-border); border-radius: var(--erp-panel-radius); }

.toolbar-title h2 { margin: 0; font-size: 15px; color: #111827; }

.toolbar-title span { font-size: 12px; color: #64748b; }

.toolbar-filters { display: flex; gap: 6px; justify-content: flex-end; }

.keyword-input { width: 280px; }

.filter-select { width: 120px; }

.menu-workbench { display: grid; flex: 1; grid-template-columns: var(--erp-master-panel-width) minmax(0, 1fr); gap: var(--erp-panel-gap); min-height: 0; overflow: hidden; }

.navigation-panel, .detail-panel { min-height: 0; background: hsl(var(--card)); border: var(--erp-panel-border); border-radius: var(--erp-panel-radius); }

.navigation-panel { display: flex; flex-direction: column; padding: 8px; overflow: hidden; }

.navigation-tree-scroll { flex: 1; min-height: 0; margin-top: 6px; overflow: hidden auto; }

.detail-panel { padding: 14px; overflow: auto; }

.panel-heading { display: flex; align-items: center; justify-content: space-between; padding: 0 6px 8px; border-bottom: 1px solid #edf0f5; }

.panel-heading strong, .panel-heading span { display: block; }

.panel-heading span { margin-top: 2px; font-size: 11px; color: #94a3b8; }

.navigation-panel :deep(.ant-tree) { min-width: 0; }

.tree-node-title { display: flex; gap: 6px; align-items: center; min-width: 0; }

.tree-node-title > span { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.tree-node-icon { flex: 0 0 auto; color: #475569; }

.tree-node-title :deep(.ant-tag) { margin: 0; font-size: 10px; line-height: 18px; }

.detail-header, .detail-title, .section-heading { display: flex; gap: 10px; align-items: center; justify-content: space-between; }

.detail-title { justify-content: flex-start; }

.detail-title > svg { width: 24px; height: 24px; color: #2563eb; }

.detail-title h3 { margin: 0; font-size: 16px; }

.detail-title code { font-size: 11px; color: #64748b; }

.detail-section { margin-top: 16px; }

.detail-section h4, .form-section h4 { margin: 0 0 8px; font-size: 13px; color: #334155; }

.section-heading h4 { margin: 0; }

.setting-tags { display: flex; flex-wrap: wrap; gap: 8px; }

.detail-empty { margin-top: 160px; }

.form-section { padding: 12px; margin-bottom: 12px; background: #fafafa; border: 1px solid #e5e7eb; border-radius: 6px; }

.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 12px; }

.form-wide { grid-column: 1 / -1; }

.menu-form :deep(.ant-form-item) { margin-bottom: 12px; }

@media (max-width: 900px) {
  .workbench-toolbar { grid-template-columns: 1fr; }

  .toolbar-filters { flex-wrap: wrap; justify-content: flex-start; }

  .menu-workbench { grid-template-columns: 1fr; }

  .navigation-panel { max-height: 42vh; }

  .form-grid { grid-template-columns: 1fr; }
}
</style>

<script lang="ts" setup>
import type { SystemMenuApi } from '#/api';
import type { TableProps } from 'ant-design-vue';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { IconifyIcon, Plus } from '@vben/icons';

import {
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
  updateMenu,
  updateMenuStatus,
} from '#/api';

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
const loading = ref(false);
const saving = ref(false);
const drawerOpen = ref(false);
const blockFullscreen = ref(false);
const columnSettingOpen = ref(false);
const editingMenu = ref<SystemMenuApi.SystemMenu>();

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
const parentOptions = computed(() => {
  const excluded = new Set<string>();
  if (editingMenu.value) {
    collectIds(editingMenu.value, excluded);
  }
  return flattenMenus(menuTree.value)
    .filter((item) => !excluded.has(item.id) && item.menuType !== 'button')
    .map((item) => ({
      label: `${'　'.repeat(item.level)}${item.menuName}`,
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

function boolValue(value?: 0 | 1) {
  return value === 1;
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
    menus.value = await getMenuList();
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
  formModel.menuKey = record.menuKey;
  formModel.menuName = record.menuName;
  formModel.menuType = record.menuType ?? 'menu';
  formModel.parentId = record.parentId || undefined;
  formModel.path = record.path ?? '';
  formModel.permissionCode = record.permissionCode ?? '';
  formModel.sortOrder = record.sortOrder ?? 100;
  formModel.status = record.status ?? record.visible ?? 1;
  drawerOpen.value = true;
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
  if (formModel.menuType === 'button' && !formModel.permissionCode?.trim()) {
    message.warning('请输入按钮权限标识');
    return false;
  }
  return true;
}

function buildPayload(): SystemMenuApi.SaveMenuParams {
  return {
    activeIcon: formModel.activeIcon || undefined,
    activePath: formModel.activePath || undefined,
    affixTab: formModel.affixTab,
    badge: formModel.badge || undefined,
    badgeType: formModel.badgeType || undefined,
    badgeVariant: formModel.badgeVariant || undefined,
    component: formModel.component || undefined,
    hideChildrenInMenu: formModel.hideChildrenInMenu,
    hideInBreadcrumb: formModel.hideInBreadcrumb,
    hideInMenu: formModel.hideInMenu,
    hideInTab: formModel.hideInTab,
    icon: formModel.icon || undefined,
    keepAlive: formModel.keepAlive,
    menuKey: formModel.menuKey.trim(),
    menuName: formModel.menuName.trim(),
    menuType: formModel.menuType,
    parentId: formModel.parentId || undefined,
    path: formModel.menuType === 'button' ? undefined : formModel.path?.trim(),
    permissionCode: formModel.permissionCode || undefined,
    sortOrder: formModel.sortOrder,
    status: formModel.status,
    visible: formModel.status === 1,
  };
}

async function submitForm() {
  if (!validateForm()) {
    return;
  }
  saving.value = true;
  try {
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
  record.status = nextStatus;
  record.visible = nextStatus;
  message.success('状态已更新');
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
  columnSettingOpen.value = false;
}

onMounted(loadMenus);
</script>

<template>
  <Page auto-content-height>
    <div class="menu-page" :class="{ 'is-block-fullscreen': blockFullscreen }">
      <section class="menu-panel">
        <div class="menu-toolbar">
          <div></div>
          <Space :size="8">
            <Button type="primary" @click="openCreate()">
              <Plus class="size-4" />
              新增菜单
            </Button>
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

        <Table
          :columns="columns"
          :data-source="menuTree"
          :loading="loading"
          :pagination="false"
          :scroll="{ x: 'max-content' }"
          bordered
          row-key="id"
          size="middle"
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
              {{ record.permissionCode || record.permissionId || '' }}
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
                <Button size="small" type="link" @click="openCreate(asMenu(record))">
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
            <FormItem label="页面组件" :required="formModel.menuType === 'menu'">
              <Input v-model:value="formModel.component" allow-clear placeholder="请输入" />
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
  min-height: 100%;
  padding: 8px;
  background: #f1f3f6;
}

.menu-page.is-block-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 1000;
  height: 100vh;
  overflow: auto;
}

.menu-panel {
  min-height: 100%;
  padding: 8px;
  background: #fff;
}

.menu-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
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

.menu-panel :deep(.ant-table-thead > tr > th) {
  background: #f5f5f5;
  color: #2b3340;
  font-weight: 600;
}

.menu-panel :deep(.ant-table-container) {
  min-height: 660px;
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
  .form-grid,
  .setting-grid {
    grid-template-columns: 1fr;
  }

  .setting-grid {
    padding: 8px 24px;
  }
}
</style>

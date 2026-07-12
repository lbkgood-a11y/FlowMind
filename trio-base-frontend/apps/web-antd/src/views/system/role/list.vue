<script setup lang="ts">
import type { SystemMenuApi, SystemRoleApi } from '#/api';
import type { TableProps } from 'ant-design-vue';
import type { Dayjs } from 'dayjs';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { IconifyIcon, Plus } from '@vben/icons';

import {
  Button,
  Checkbox,
  DatePicker,
  Descriptions,
  DescriptionsItem,
  Drawer,
  Empty,
  Form,
  FormItem,
  Input,
  message,
  Modal,
  Pagination,
  Popconfirm,
  Popover,
  Radio,
  RadioGroup,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  Tree,
} from 'ant-design-vue';

import {
  createRole,
  deleteRole,
  getMenuList,
  getRoleDetail,
  getRolePage,
  roleCodeExists,
  updateRole,
  updateRoleStatus,
} from '#/api';

const RangePicker = DatePicker.RangePicker;
const Textarea = Input.TextArea;

const ROLE_PERMISSIONS = {
  create: '/api/v1/roles:POST',
  delete: '/api/v1/roles/*:DELETE',
  query: '/api/v1/roles:GET',
  update: '/api/v1/roles/*:PUT',
} as const;

const MENU_PERMISSIONS = {
  query: '/api/v1/menus:GET',
} as const;

type RoleFormModel = {
  description?: string;
  menuIds: string[];
  roleCode: string;
  roleName: string;
  status: 0 | 1;
};

type MenuCheckedKeys =
  | Array<number | string>
  | {
      checked: Array<number | string>;
      halfChecked: Array<number | string>;
    };

type RoleQueryModel = {
  createdRange?: [Dayjs, Dayjs];
  keyword?: string;
  roleCode?: string;
  roleName?: string;
  status?: 0 | 1;
};

type RoleColumnKey =
  | 'action'
  | 'createdAt'
  | 'description'
  | 'id'
  | 'roleCode'
  | 'roleName'
  | 'status';

type RoleColumnSetting = {
  key: RoleColumnKey;
  title: string;
  visible: boolean;
  width: number;
};

const defaultColumnSettings: RoleColumnSetting[] = [
  { key: 'roleName', title: '角色名称', visible: true, width: 180 },
  { key: 'roleCode', title: '角色编码', visible: true, width: 160 },
  { key: 'id', title: '角色ID', visible: true, width: 150 },
  { key: 'status', title: '状态', visible: true, width: 110 },
  { key: 'description', title: '描述', visible: true, width: 260 },
  { key: 'createdAt', title: '创建时间', visible: true, width: 210 },
  { key: 'action', title: '操作', visible: true, width: 230 },
];

const roles = ref<SystemRoleApi.SystemRole[]>([]);
const menus = ref<SystemMenuApi.SystemMenu[]>([]);
const loading = ref(false);
const loadingMenus = ref(false);
const saving = ref(false);
const formOpen = ref(false);
const detailOpen = ref(false);
const permissionOnly = ref(false);
const collapsed = ref(false);
const queryHidden = ref(false);
const blockFullscreen = ref(false);
const columnSettingOpen = ref(false);
const tableKey = ref(0);
const editingRole = ref<SystemRoleApi.RoleDetail>();
const detailRole = ref<SystemRoleApi.RoleDetail>();
const { hasAccessByCodes } = useAccess();

const canQuery = computed(() => hasAccessByCodes([ROLE_PERMISSIONS.query]));
const canCreate = computed(() => hasAccessByCodes([ROLE_PERMISSIONS.create]));
const canUpdate = computed(() => hasAccessByCodes([ROLE_PERMISSIONS.update]));
const canDelete = computed(() => hasAccessByCodes([ROLE_PERMISSIONS.delete]));
const canQueryMenus = computed(() => hasAccessByCodes([MENU_PERMISSIONS.query]));
const canSaveRole = computed(() =>
  editingRole.value ? canUpdate.value : canCreate.value,
);

const queryForm = reactive<RoleQueryModel>({
  createdRange: undefined,
  keyword: '',
  roleCode: '',
  roleName: '',
  status: undefined,
});

const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
});

const formModel = reactive<RoleFormModel>({
  description: '',
  menuIds: [],
  roleCode: '',
  roleName: '',
  status: 1,
});

const columnSettings = reactive<RoleColumnSetting[]>(
  defaultColumnSettings.map((item) => ({ ...item })),
);
const columnDraft = ref<RoleColumnSetting[]>(
  defaultColumnSettings.map((item) => ({ ...item })),
);

const menuCheckedKeys = ref<MenuCheckedKeys>([]);

const menuIds = computed(() => new Set(menus.value.map((item) => item.id)));
const menuTree = computed(() => buildMenuTree(menus.value));

const allDraftChecked = computed({
  get: () => columnDraft.value.every((item) => item.visible),
  set: (checked: boolean) => {
    columnDraft.value.forEach((item) => {
      item.visible = checked;
    });
  },
});

const baseColumns: Record<RoleColumnKey, NonNullable<TableProps['columns']>[number]> =
  {
    action: { align: 'center', fixed: 'right', key: 'action', title: '操作', width: 230 },
    createdAt: {
      align: 'center',
      dataIndex: 'createdAt',
      key: 'createdAt',
      title: '创建时间',
      width: 210,
    },
    description: {
      dataIndex: 'description',
      key: 'description',
      title: '描述',
      width: 260,
    },
    id: { align: 'center', dataIndex: 'id', key: 'id', title: '角色ID', width: 150 },
    roleCode: {
      align: 'center',
      dataIndex: 'roleCode',
      key: 'roleCode',
      title: '角色编码',
      width: 160,
    },
    roleName: {
      dataIndex: 'roleName',
      fixed: 'left',
      key: 'roleName',
      title: '角色名称',
      width: 180,
    },
    status: { align: 'center', dataIndex: 'status', key: 'status', title: '状态', width: 110 },
  };

const columns = computed<TableProps['columns']>(() =>
  columnSettings
    .filter((item) => item.visible)
    .map((item) => ({
      ...baseColumns[item.key],
      width: item.width,
    })),
);

function asRole(record: Record<string, any>) {
  return record as SystemRoleApi.SystemRole;
}

function statusValue(record?: Pick<SystemRoleApi.SystemRole, 'status'>) {
  return record?.status ?? 1;
}

function formatDate(value?: string) {
  return value ? value.replace('T', ' ') : '-';
}

function menuTypeLabel(type?: SystemMenuApi.MenuType) {
  const labels: Record<SystemMenuApi.MenuType, string> = {
    button: '权限点',
    catalog: '目录',
    embedded: '内嵌',
    link: '外链',
    menu: '菜单',
  };
  return labels[type ?? 'menu'];
}

function buildMenuTree(list: SystemMenuApi.SystemMenu[]) {
  const nodeMap = new Map<
    string,
    {
      children: any[];
      key: string;
      sortOrder: number;
      title: string;
    }
  >();
  list.forEach((menu) => {
    nodeMap.set(menu.id, {
      children: [],
      key: menu.id,
      sortOrder: menu.sortOrder ?? 100,
      title: `${menu.menuName || menu.menuKey} (${menuTypeLabel(menu.menuType)})`,
    });
  });

  const roots: any[] = [];
  list.forEach((menu) => {
    const node = nodeMap.get(menu.id);
    if (!node) {
      return;
    }
    if (menu.parentId && nodeMap.has(menu.parentId)) {
      nodeMap.get(menu.parentId)?.children.push(node);
    } else {
      roots.push(node);
    }
  });

  const sortNodes = (nodes: any[]) => {
    nodes.sort((a, b) => a.sortOrder - b.sortOrder);
    nodes.forEach((node) => {
      if (node.children.length > 0) {
        sortNodes(node.children);
      } else {
        delete node.children;
      }
      delete node.sortOrder;
    });
  };
  sortNodes(roots);
  return roots;
}

async function loadMenusForAuthorization(force = false) {
  if (!force && menus.value.length > 0) {
    return;
  }
  if (!canQueryMenus.value || (!canCreate.value && !canUpdate.value)) {
    return;
  }
  loadingMenus.value = true;
  try {
    menus.value = await getMenuList();
  } finally {
    loadingMenus.value = false;
  }
}

async function loadRoles(page = pagination.current) {
  if (!canQuery.value) {
    roles.value = [];
    pagination.total = 0;
    return;
  }
  loading.value = true;
  try {
    const result = await getRolePage({
      createdEnd: queryForm.createdRange?.[1]
        ?.endOf('day')
        .format('YYYY-MM-DDTHH:mm:ss'),
      createdStart: queryForm.createdRange?.[0]
        ?.startOf('day')
        .format('YYYY-MM-DDTHH:mm:ss'),
      keyword: queryForm.keyword?.trim() || undefined,
      page,
      roleCode: queryForm.roleCode?.trim() || undefined,
      roleName: queryForm.roleName?.trim() || undefined,
      size: pagination.pageSize,
      status: queryForm.status,
    });
    roles.value = result.items;
    pagination.current = page;
    pagination.total = result.total;
  } finally {
    loading.value = false;
  }
}

function resetQuery() {
  queryForm.createdRange = undefined;
  queryForm.keyword = '';
  queryForm.roleCode = '';
  queryForm.roleName = '';
  queryForm.status = undefined;
  loadRoles(1);
}

async function handleToolbarSearch() {
  await loadRoles(1);
  queryHidden.value = true;
}

function resetForm() {
  editingRole.value = undefined;
  permissionOnly.value = false;
  formModel.description = '';
  formModel.menuIds = [];
  formModel.roleCode = '';
  formModel.roleName = '';
  formModel.status = 1;
  menuCheckedKeys.value = [];
}

async function openCreate() {
  resetForm();
  await loadMenusForAuthorization(true);
  formOpen.value = true;
}

async function openEdit(record: SystemRoleApi.SystemRole, onlyPermissions = false) {
  resetForm();
  permissionOnly.value = onlyPermissions;
  await loadMenusForAuthorization(true);
  const detail = await getRoleDetail(record.id);
  editingRole.value = detail;
  formModel.description = detail.description ?? '';
  formModel.menuIds = detail.menuIds ?? [];
  formModel.roleCode = detail.roleCode;
  formModel.roleName = detail.roleName;
  formModel.status = statusValue(detail);
  menuCheckedKeys.value = [...formModel.menuIds];
  formOpen.value = true;
}

async function openDetail(record: SystemRoleApi.SystemRole) {
  await loadMenusForAuthorization(true);
  detailRole.value = await getRoleDetail(record.id);
  detailOpen.value = true;
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

function selectedMenuIds() {
  const checkedKeys = Array.isArray(menuCheckedKeys.value)
    ? menuCheckedKeys.value
    : menuCheckedKeys.value.checked;
  return checkedKeys
    .map((key) => String(key))
    .filter((key) => menuIds.value.has(key));
}

function validateForm() {
  if (!editingRole.value && !formModel.roleCode.trim()) {
    message.warning('请输入角色编码');
    return false;
  }
  if (!formModel.roleName.trim()) {
    message.warning('请输入角色名称');
    return false;
  }
  return true;
}

async function submitForm() {
  if (!canSaveRole.value) {
    message.warning('当前账号没有保存角色的权限');
    return;
  }
  if (!validateForm()) {
    return;
  }
  saving.value = true;
  try {
    if (!editingRole.value && (await roleCodeExists(formModel.roleCode.trim()))) {
      message.warning('角色编码已存在');
      return;
    }
    const payload: SystemRoleApi.SaveRoleParams = {
      description: formModel.description?.trim() || undefined,
      menuIds: canQueryMenus.value ? selectedMenuIds() : formModel.menuIds,
      roleName: formModel.roleName.trim(),
      status: formModel.status,
    };
    if (!editingRole.value) {
      payload.roleCode = formModel.roleCode.trim();
    }

    if (editingRole.value) {
      await updateRole(editingRole.value.id, payload);
      message.success(permissionOnly.value ? '角色授权已更新' : '角色已更新');
    } else {
      await createRole(payload);
      message.success('角色已创建');
    }

    formOpen.value = false;
    await loadRoles();
  } finally {
    saving.value = false;
  }
}

function changeStatus(record: SystemRoleApi.SystemRole, checked: boolean) {
  const nextStatus = checked ? 1 : 0;
  Modal.confirm({
    content: `确认${checked ? '启用' : '禁用'}角色 ${record.roleName}？`,
    onOk: async () => {
      await updateRoleStatus(record.id, nextStatus);
      message.success('状态已更新');
      await loadRoles();
    },
    onCancel: () => {
      tableKey.value += 1;
    },
    title: '切换角色状态',
  });
}

async function removeRole(record: SystemRoleApi.SystemRole) {
  await deleteRole(record.id);
  message.success('角色已删除');
  const nextPage =
    roles.value.length === 1 && pagination.current > 1
      ? pagination.current - 1
      : pagination.current;
  await loadRoles(nextPage);
}

function onPageChange(page: number, pageSize: number) {
  pagination.pageSize = pageSize;
  loadRoles(page);
}

function onPageSizeChange(page: number, pageSize: number) {
  pagination.pageSize = pageSize;
  loadRoles(page);
}

function toggleFullscreen() {
  blockFullscreen.value = !blockFullscreen.value;
}

onMounted(() => {
  loadRoles(1);
});
</script>

<template>
  <Page auto-content-height>
    <div
      class="role-page"
      :class="{ 'is-block-fullscreen': blockFullscreen, 'is-query-hidden': queryHidden }"
    >
      <section v-show="!queryHidden" class="query-panel">
        <div class="query-grid" :class="{ collapsed }">
          <FormItem label="角色名称">
            <Input
              v-model:value="queryForm.roleName"
              allow-clear
              placeholder="请输入"
              @press-enter="loadRoles(1)"
            />
          </FormItem>
          <FormItem label="角色编码">
            <Input
              v-model:value="queryForm.roleCode"
              allow-clear
              placeholder="请输入"
              @press-enter="loadRoles(1)"
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
          <FormItem v-if="!collapsed" label="关键词">
            <Input
              v-model:value="queryForm.keyword"
              allow-clear
              placeholder="编码/名称/描述"
              @press-enter="loadRoles(1)"
            />
          </FormItem>
          <FormItem v-if="!collapsed" label="创建时间">
            <RangePicker
              v-model:value="queryForm.createdRange"
              class="w-full"
              :placeholder="['开始日期', '结束日期']"
            />
          </FormItem>
          <div class="query-actions">
            <Button v-if="canQuery" @click="resetQuery">重置</Button>
            <Button v-if="canQuery" type="primary" @click="loadRoles(1)">搜索</Button>
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
            <h2>角色列表</h2>
            <Button v-if="queryHidden" type="link" @click="queryHidden = false">
              展开搜索
            </Button>
          </div>
          <Space :size="8">
            <Button v-if="canCreate" type="primary" @click="openCreate">
              <Plus class="size-4" />
              新增角色
            </Button>
            <Tooltip v-if="canQuery" title="查询并隐藏搜索栏">
              <Button shape="circle" type="primary" @click="handleToolbarSearch">
                <IconifyIcon icon="lucide:search" class="size-4" />
              </Button>
            </Tooltip>
            <Tooltip v-if="canQuery" title="刷新">
              <Button shape="circle" @click="loadRoles()">
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
              overlay-class-name="role-column-popover"
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
                      <span class="column-setting-title">{{ item.title }}</span>
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
            :columns="columns"
            :data-source="roles"
            :loading="loading"
            :pagination="false"
            :scroll="{ x: 'max-content' }"
            :sticky="{ offsetScroll: 0 }"
            bordered
            row-key="id"
            table-layout="fixed"
            size="middle"
          >
            <template #emptyText>
              <Empty description="暂无数据" />
            </template>

            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'roleName'">
                <div class="role-name-cell">
                  <IconifyIcon icon="lucide:shield-user" class="size-4" />
                  <span>{{ record.roleName }}</span>
                </div>
              </template>

              <template v-else-if="column.key === 'status'">
                <Switch
                  v-if="canUpdate"
                  :checked="statusValue(asRole(record)) === 1"
                  checked-children="启用"
                  un-checked-children="禁用"
                  @change="(checked) => changeStatus(asRole(record), checked as boolean)"
                />
                <Tag v-else :color="statusValue(asRole(record)) === 1 ? 'success' : 'error'">
                  {{ statusValue(asRole(record)) === 1 ? '启用' : '禁用' }}
                </Tag>
              </template>

              <template v-else-if="column.key === 'description'">
                {{ record.description || '-' }}
              </template>

              <template v-else-if="column.key === 'createdAt'">
                {{ formatDate(record.createdAt) }}
              </template>

              <template v-else-if="column.key === 'action'">
                <Space>
                  <Button size="small" type="link" @click="openDetail(asRole(record))">
                    详情
                  </Button>
                  <Button
                    v-if="canUpdate"
                    size="small"
                    type="link"
                    @click="openEdit(asRole(record))"
                  >
                    编辑
                  </Button>
                  <Button
                    v-if="canUpdate && canQueryMenus"
                    size="small"
                    type="link"
                    @click="openEdit(asRole(record), true)"
                  >
                    授权
                  </Button>
                  <Popconfirm
                    v-if="canDelete"
                    title="确认删除该角色？"
                    ok-text="删除"
                    cancel-text="取消"
                    @confirm="removeRole(asRole(record))"
                  >
                    <Button danger size="small" type="link">删除</Button>
                  </Popconfirm>
                </Space>
              </template>
            </template>
          </Table>
        </div>

        <div class="table-footer">
          <div class="table-total">共 {{ pagination.total }} 条记录</div>
          <Pagination
            v-model:current="pagination.current"
            v-model:page-size="pagination.pageSize"
            :page-size-options="['10', '20', '50', '100']"
            :total="pagination.total"
            show-less-items
            show-size-changer
            @change="onPageChange"
            @show-size-change="onPageSizeChange"
          />
        </div>
      </section>
    </div>

    <Drawer
      v-model:open="formOpen"
      :title="permissionOnly ? '角色授权' : editingRole ? '编辑角色' : '新增角色'"
      width="760"
      :destroy-on-close="false"
    >
      <Form :model="formModel" class="role-form" layout="horizontal">
        <div class="form-grid">
          <FormItem label="角色编码" :required="!editingRole">
            <Input
              v-model:value="formModel.roleCode"
              allow-clear
              :disabled="!!editingRole || permissionOnly"
              placeholder="如 ADMIN"
            />
          </FormItem>
          <FormItem label="角色名称" required>
            <Input
              v-model:value="formModel.roleName"
              allow-clear
              :disabled="permissionOnly"
              placeholder="请输入"
            />
          </FormItem>
          <FormItem label="状态">
            <RadioGroup
              v-model:value="formModel.status"
              button-style="solid"
              :disabled="permissionOnly"
            >
              <Radio :value="1">启用</Radio>
              <Radio :value="0">禁用</Radio>
            </RadioGroup>
          </FormItem>
          <FormItem class="form-full" label="描述">
            <Textarea
              v-model:value="formModel.description"
              :disabled="permissionOnly"
              :rows="3"
              allow-clear
              placeholder="请输入"
            />
          </FormItem>
        </div>

        <FormItem v-if="canQueryMenus" class="permission-item" label="菜单授权">
          <div class="permission-panel">
            <Tree
              v-model:checkedKeys="menuCheckedKeys"
              :check-strictly="true"
              :tree-data="menuTree"
              block-node
              checkable
              default-expand-all
              :loading="loadingMenus"
            />
          </div>
        </FormItem>
      </Form>

      <template #footer>
        <Space>
          <Button @click="formOpen = false">取消</Button>
          <Button
            :disabled="!canSaveRole"
            :loading="saving"
            type="primary"
            @click="submitForm"
          >
            保存
          </Button>
        </Space>
      </template>
    </Drawer>

    <Drawer v-model:open="detailOpen" title="角色详情" width="460">
      <Descriptions v-if="detailRole" bordered :column="1" size="small">
        <DescriptionsItem label="角色 ID">{{ detailRole.id }}</DescriptionsItem>
        <DescriptionsItem label="角色编码">{{ detailRole.roleCode }}</DescriptionsItem>
        <DescriptionsItem label="角色名称">{{ detailRole.roleName }}</DescriptionsItem>
        <DescriptionsItem label="状态">
          <Tag :color="statusValue(detailRole) === 1 ? 'success' : 'error'">
            {{ statusValue(detailRole) === 1 ? '启用' : '禁用' }}
          </Tag>
        </DescriptionsItem>
        <DescriptionsItem label="描述">{{ detailRole.description || '-' }}</DescriptionsItem>
        <DescriptionsItem label="创建时间">{{ formatDate(detailRole.createdAt) }}</DescriptionsItem>
        <DescriptionsItem label="授权菜单数量">
          {{ detailRole.menuIds?.length ?? 0 }}
        </DescriptionsItem>
      </Descriptions>
    </Drawer>
  </Page>
</template>

<style scoped>
.role-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 100%;
  background: #f1f3f6;
}

.role-page.is-block-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 1000;
  height: 100vh;
  padding: 12px;
  overflow: auto;
}

.role-page.is-block-fullscreen .list-panel {
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

.role-name-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 46px;
  padding: 10px 2px 0;
}

.table-total {
  color: #111827;
  font-size: 14px;
}

.role-form :deep(.ant-form-item-label) {
  width: 104px;
  font-weight: 600;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(240px, 1fr));
  column-gap: 22px;
}

.form-full,
.permission-item {
  grid-column: 1 / -1;
}

.permission-panel {
  width: 100%;
  max-height: 360px;
  padding: 8px 12px;
  overflow: auto;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}

:global(.role-column-popover .ant-popover-inner) {
  padding: 0;
  border-radius: 4px;
}

.column-settings {
  width: 232px;
  color: #1f2937;
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
  padding: 2px 0 6px;
}

.column-setting-item {
  display: grid;
  grid-template-columns: 18px 18px 1fr;
  align-items: center;
  height: 28px;
  padding: 0 12px;
  column-gap: 4px;
  font-size: 14px;
}

.column-setting-item:hover {
  background: #f6f8ff;
}

.drag-icon {
  color: #6b7280;
}

.column-setting-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.column-setting-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 38px;
  padding: 0 10px;
  border-top: 1px solid #edf0f5;
}

@media (max-width: 1100px) {
  .query-grid {
    grid-template-columns: repeat(2, minmax(240px, 1fr));
    column-gap: 24px;
  }
}

@media (max-width: 760px) {
  .query-grid,
  .form-grid {
    grid-template-columns: 1fr;
  }

  .query-actions {
    justify-content: flex-start;
  }

  .table-footer {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }
}
</style>

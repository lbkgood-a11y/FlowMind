<script lang="ts" setup>
import type { SystemRoleApi, SystemUserApi } from '#/api';
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
  InputPassword,
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
} from 'ant-design-vue';

import {
  createUser,
  deleteUser,
  getRoleList,
  getUserList,
  updateUser,
  updateUserStatus,
} from '#/api';

const RangePicker = DatePicker.RangePicker;

const USER_PERMISSIONS = {
  create: '/api/v1/users:POST',
  delete: '/api/v1/users/*:DELETE',
  query: '/api/v1/users:GET',
  update: '/api/v1/users/*:PUT',
} as const;

const ROLE_PERMISSIONS = {
  query: '/api/v1/roles:GET',
} as const;

type UserFormModel = {
  email?: string;
  password?: string;
  phone?: string;
  roleIds: string[];
  status: 0 | 1;
  username: string;
};

type QueryFormModel = {
  createdRange?: [Dayjs, Dayjs];
  remark?: string;
  status?: 0 | 1;
  userId?: string;
  username?: string;
};

type UserColumnKey =
  | 'action'
  | 'createdAt'
  | 'id'
  | 'remark'
  | 'status'
  | 'username';

type UserColumnSetting = {
  key: UserColumnKey;
  title: string;
  visible: boolean;
  width: number;
};

const defaultColumnSettings: UserColumnSetting[] = [
  { key: 'username', title: '用户名', visible: true, width: 120 },
  { key: 'id', title: '用户ID', visible: true, width: 150 },
  { key: 'status', title: '状态', visible: true, width: 120 },
  { key: 'remark', title: '备注', visible: true, width: 160 },
  { key: 'createdAt', title: '创建时间', visible: true, width: 260 },
  { key: 'action', title: '操作', visible: true, width: 190 },
];

const users = ref<SystemUserApi.SystemUser[]>([]);
const roles = ref<SystemRoleApi.SystemRole[]>([]);
const loading = ref(false);
const saving = ref(false);
const formOpen = ref(false);
const detailOpen = ref(false);
const collapsed = ref(false);
const queryHidden = ref(false);
const blockFullscreen = ref(false);
const columnSettingOpen = ref(false);
const tableKey = ref(0);
const editingUser = ref<SystemUserApi.SystemUser>();
const detailUser = ref<SystemUserApi.SystemUser>();
const { hasAccessByCodes } = useAccess();

const canQuery = computed(() => hasAccessByCodes([USER_PERMISSIONS.query]));
const canCreate = computed(() => hasAccessByCodes([USER_PERMISSIONS.create]));
const canUpdate = computed(() => hasAccessByCodes([USER_PERMISSIONS.update]));
const canDelete = computed(() => hasAccessByCodes([USER_PERMISSIONS.delete]));
const canQueryRoles = computed(() => hasAccessByCodes([ROLE_PERMISSIONS.query]));
const canSaveUser = computed(() =>
  editingUser.value ? canUpdate.value : canCreate.value,
);

const queryForm = reactive<QueryFormModel>({
  createdRange: undefined,
  remark: '',
  status: undefined,
  userId: '',
  username: '',
});

const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
});

const formModel = reactive<UserFormModel>({
  email: '',
  password: '',
  phone: '',
  roleIds: [],
  status: 1,
  username: '',
});

const roleOptions = computed(() =>
  roles.value.map((role) => ({
    label: `${role.roleName} (${role.roleCode})`,
    value: role.id,
  })),
);

const columnSettings = reactive<UserColumnSetting[]>(
  defaultColumnSettings.map((item) => ({ ...item })),
);
const columnDraft = ref<UserColumnSetting[]>(
  defaultColumnSettings.map((item) => ({ ...item })),
);

const baseColumns: Record<UserColumnKey, NonNullable<TableProps['columns']>[number]> =
  {
    action: { align: 'center', key: 'action', title: '操作', width: 190 },
    createdAt: {
      align: 'center',
      dataIndex: 'createdAt',
      key: 'createdAt',
      title: '创建时间',
      width: 260,
    },
    id: { align: 'center', dataIndex: 'id', key: 'id', title: '用户ID', width: 150 },
    remark: { align: 'center', dataIndex: 'remark', key: 'remark', title: '备注', width: 160 },
    status: { align: 'center', dataIndex: 'status', key: 'status', title: '状态', width: 120 },
    username: {
      align: 'center',
      dataIndex: 'username',
      key: 'username',
      title: '用户名',
      width: 120,
    },
  };

const columns = computed<TableProps['columns']>(() =>
  columnSettings
    .filter((item) => item.visible)
    .map((item) => ({
      ...baseColumns[item.key],
      width: item.width,
    })),
);

const allDraftChecked = computed({
  get: () => columnDraft.value.every((item) => item.visible),
  set: (checked: boolean) => {
    columnDraft.value.forEach((item) => {
      item.visible = checked;
    });
  },
});

const keyword = computed(() => {
  return queryForm.remark?.trim() || undefined;
});

async function loadRoles() {
  if (roles.value.length > 0) {
    return;
  }
  if (!canQueryRoles.value || (!canCreate.value && !canUpdate.value)) {
    return;
  }
  roles.value = await getRoleList({ status: 1 });
}

async function loadUsers(page = pagination.current) {
  if (!canQuery.value) {
    users.value = [];
    pagination.total = 0;
    return;
  }
  loading.value = true;
  try {
    const result = await getUserList({
      createdEnd: queryForm.createdRange?.[1]
        ?.endOf('day')
        .format('YYYY-MM-DDTHH:mm:ss'),
      createdStart: queryForm.createdRange?.[0]
        ?.startOf('day')
        .format('YYYY-MM-DDTHH:mm:ss'),
      keyword: keyword.value,
      page,
      size: pagination.pageSize,
      status: queryForm.status,
      userId: queryForm.userId?.trim() || undefined,
      username: queryForm.username?.trim() || undefined,
    });
    users.value = result.items;
    pagination.current = page;
    pagination.total = result.total;
  } finally {
    loading.value = false;
  }
}

function resetQuery() {
  queryForm.createdRange = undefined;
  queryForm.remark = '';
  queryForm.status = undefined;
  queryForm.userId = '';
  queryForm.username = '';
  loadUsers(1);
}

async function handleToolbarSearch() {
  await loadUsers(1);
  queryHidden.value = true;
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

function resetForm() {
  editingUser.value = undefined;
  formModel.email = '';
  formModel.password = '';
  formModel.phone = '';
  formModel.roleIds = [];
  formModel.status = 1;
  formModel.username = '';
}

function getRoleIdsByCodes(codes?: string[]) {
  if (!codes?.length) {
    return [];
  }
  return roles.value
    .filter((role) => codes.includes(role.roleCode))
    .map((role) => role.id);
}

async function openCreate() {
  resetForm();
  await loadRoles();
  formOpen.value = true;
}

async function openEdit(record: SystemUserApi.SystemUser) {
  await loadRoles();
  editingUser.value = record;
  formModel.email = record.email ?? '';
  formModel.password = '';
  formModel.phone = record.phone ?? '';
  formModel.roleIds = canQueryRoles.value ? getRoleIdsByCodes(record.roles) : [];
  formModel.status = record.status;
  formModel.username = record.username;
  formOpen.value = true;
}

function openDetail(record: SystemUserApi.SystemUser) {
  editingUser.value = undefined;
  detailUser.value = record;
  detailOpen.value = true;
}

function validatePassword(password: string) {
  return (
    password.length >= 8 &&
    /[a-z]/.test(password) &&
    /[A-Z]/.test(password) &&
    /\d/.test(password)
  );
}

async function submitForm() {
  if (!canSaveUser.value) {
    message.warning('当前账号没有保存用户的权限');
    return;
  }
  if (!formModel.username.trim()) {
    message.warning('请输入用户名');
    return;
  }
  if (!editingUser.value && !formModel.password) {
    message.warning('请输入初始密码');
    return;
  }
  if (formModel.password && !validatePassword(formModel.password)) {
    message.warning('密码至少 8 位，且包含大小写字母和数字');
    return;
  }

  saving.value = true;
  try {
    const payload: SystemUserApi.SaveUserParams = {
      email: formModel.email || undefined,
      phone: formModel.phone || undefined,
      roleIds: canQueryRoles.value ? formModel.roleIds : undefined,
      status: formModel.status,
    };
    if (!editingUser.value) {
      payload.username = formModel.username.trim();
    }
    if (formModel.password) {
      payload.password = formModel.password;
    }

    if (editingUser.value) {
      await updateUser(editingUser.value.id, payload);
      message.success('用户已更新');
    } else {
      await createUser(payload);
      message.success('用户已创建');
    }

    formOpen.value = false;
    await loadUsers();
  } finally {
    saving.value = false;
  }
}

function changeStatus(record: SystemUserApi.SystemUser, checked: boolean) {
  const nextStatus = checked ? 1 : 0;
  Modal.confirm({
    content: `确认${checked ? '启用' : '禁用'}用户 ${record.username}？`,
    onOk: async () => {
      await updateUserStatus(record.id, nextStatus);
      record.status = nextStatus;
      message.success('状态已更新');
    },
    title: '切换用户状态',
  });
}

async function removeUser(record: SystemUserApi.SystemUser) {
  await deleteUser(record.id);
  message.success('用户已删除');
  await loadUsers();
}

function onPageChange(page: number, pageSize: number) {
  pagination.pageSize = pageSize;
  loadUsers(page);
}

function onPageSizeChange(page: number, pageSize: number) {
  pagination.pageSize = pageSize;
  loadUsers(page);
}

function asUser(record: Record<string, any>) {
  return record as SystemUserApi.SystemUser;
}

function toggleFullscreen() {
  blockFullscreen.value = !blockFullscreen.value;
}

onMounted(async () => {
  await loadUsers(1);
});
</script>

<template>
  <Page auto-content-height>
    <div
      class="user-page"
      :class="{ 'is-block-fullscreen': blockFullscreen, 'is-query-hidden': queryHidden }"
    >
      <section v-show="!queryHidden" class="query-panel">
        <div class="query-grid" :class="{ collapsed }">
          <FormItem label="用户名">
            <Input
              v-model:value="queryForm.username"
              allow-clear
              placeholder="请输入"
              @press-enter="loadUsers(1)"
            />
          </FormItem>
          <FormItem label="用户ID">
            <Input
              v-model:value="queryForm.userId"
              allow-clear
              placeholder="请输入"
              @press-enter="loadUsers(1)"
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
          <FormItem v-if="!collapsed" label="备注">
            <Input
              v-model:value="queryForm.remark"
              allow-clear
              placeholder="请输入"
              @press-enter="loadUsers(1)"
            />
          </FormItem>
          <FormItem v-if="!collapsed" label="创建时间">
            <RangePicker
              v-model:value="queryForm.createdRange"
              class="w-full"
              value-format="YYYY-MM-DD"
              :placeholder="['开始日期', '结束日期']"
            />
          </FormItem>
          <div class="query-actions">
            <Button v-if="canQuery" @click="resetQuery">重置</Button>
            <Button v-if="canQuery" type="primary" @click="loadUsers(1)">搜索</Button>
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
            <h2>用户列表</h2>
            <Button v-if="queryHidden" type="link" @click="queryHidden = false">
              展开搜索
            </Button>
          </div>
          <Space :size="8">
            <Button v-if="canCreate" type="primary" @click="openCreate">
              <Plus class="size-4" />
              新增用户名
            </Button>
            <Tooltip v-if="canQuery" title="查询并隐藏搜索栏">
              <Button shape="circle" type="primary" @click="handleToolbarSearch">
                <IconifyIcon icon="lucide:search" class="size-4" />
              </Button>
            </Tooltip>
            <Tooltip v-if="canQuery" title="刷新">
              <Button shape="circle" @click="loadUsers()">
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
              overlay-class-name="user-column-popover"
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
                      <button
                        class="pin-button"
                        disabled
                        type="button"
                      >
                        <IconifyIcon icon="lucide:pin" class="size-4" />
                      </button>
                      <button
                        class="pin-button"
                        disabled
                        type="button"
                      >
                        <IconifyIcon icon="lucide:pin" class="size-4 rotate-pin" />
                      </button>
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
            :data-source="users"
            :loading="loading"
            :pagination="false"
            bordered
            row-key="id"
            table-layout="fixed"
            size="middle"
          >
            <template #emptyText>
              <Empty description="暂无数据" />
            </template>

            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'status'">
                <Switch
                  v-if="canUpdate"
                  :checked="record.status === 1"
                  checked-children="启用"
                  un-checked-children="禁用"
                  @change="(checked) => changeStatus(asUser(record), checked as boolean)"
                />
                <Tag v-else :color="record.status === 1 ? 'success' : 'error'">
                  {{ record.status === 1 ? '启用' : '禁用' }}
                </Tag>
              </template>

              <template v-else-if="column.key === 'remark'">
                <Space wrap>
                  <Tag v-for="role in record.roles" :key="role" color="blue">
                    {{ role }}
                  </Tag>
                  <span v-if="!record.roles?.length" class="text-muted-foreground">
                    -
                  </span>
                </Space>
              </template>

              <template v-else-if="column.key === 'action'">
                <Space>
                  <Button size="small" type="link" @click="openDetail(asUser(record))">
                    详情
                  </Button>
                  <Button
                    v-if="canUpdate"
                    size="small"
                    type="link"
                    @click="openEdit(asUser(record))"
                  >
                    编辑
                  </Button>
                  <Popconfirm
                    v-if="canDelete"
                    title="确认删除该用户？"
                    ok-text="删除"
                    cancel-text="取消"
                    @confirm="removeUser(asUser(record))"
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

    <Modal
      v-model:open="formOpen"
      :confirm-loading="saving"
      :ok-button-props="{ disabled: !canSaveUser }"
      ok-text="保存"
      :title="editingUser ? '编辑用户' : '新增用户'"
      destroy-on-close
      @ok="submitForm"
    >
      <Form layout="vertical">
        <FormItem label="用户名" required>
          <Input
            v-model:value="formModel.username"
            :disabled="!!editingUser"
            placeholder="请输入用户名"
          />
        </FormItem>
        <FormItem :label="editingUser ? '重置密码' : '初始密码'" :required="!editingUser">
          <InputPassword
            v-model:value="formModel.password"
            placeholder="至少 8 位，包含大小写字母和数字"
          />
        </FormItem>
        <FormItem label="邮箱">
          <Input v-model:value="formModel.email" placeholder="user@example.com" />
        </FormItem>
        <FormItem label="手机号">
          <Input v-model:value="formModel.phone" placeholder="13800138000" />
        </FormItem>
        <FormItem label="状态">
          <RadioGroup v-model:value="formModel.status">
            <Radio :value="1">启用</Radio>
            <Radio :value="0">禁用</Radio>
          </RadioGroup>
        </FormItem>
        <FormItem v-if="canQueryRoles" label="角色">
          <Select
            v-model:value="formModel.roleIds"
            mode="multiple"
            placeholder="选择角色"
            :options="roleOptions"
          />
        </FormItem>
      </Form>
    </Modal>

    <Drawer v-model:open="detailOpen" :footer="null" title="用户详情" width="420">
      <Descriptions v-if="detailUser" bordered :column="1" size="small">
        <DescriptionsItem label="用户 ID">{{ detailUser.id }}</DescriptionsItem>
        <DescriptionsItem label="用户名">{{ detailUser.username }}</DescriptionsItem>
        <DescriptionsItem label="邮箱">{{ detailUser.email || '-' }}</DescriptionsItem>
        <DescriptionsItem label="手机号">{{ detailUser.phone || '-' }}</DescriptionsItem>
        <DescriptionsItem label="状态">
          <Tag :color="detailUser.status === 1 ? 'success' : 'error'">
            {{ detailUser.status === 1 ? '启用' : '禁用' }}
          </Tag>
        </DescriptionsItem>
        <DescriptionsItem label="角色">
          <Space wrap>
            <Tag v-for="role in detailUser.roles" :key="role" color="blue">
              {{ role }}
            </Tag>
            <span v-if="!detailUser.roles?.length">-</span>
          </Space>
        </DescriptionsItem>
        <DescriptionsItem label="创建时间">
          {{ detailUser.createdAt || '-' }}
        </DescriptionsItem>
      </Descriptions>
    </Drawer>
  </Page>
</template>

<style scoped>
.user-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 100%;
  background: #f1f3f6;
}

.user-page.is-block-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 1000;
  height: 100vh;
  padding: 12px;
  overflow: auto;
}

.user-page.is-block-fullscreen .list-panel {
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
  grid-template-columns: repeat(3, minmax(280px, 1fr));
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

.list-panel :deep(.ant-table) {
  border-radius: 4px;
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
}

.table-frame :deep(.ant-table-container) {
  border-inline-start: 0 !important;
}

.table-frame :deep(.ant-table-content > table) {
  width: 100% !important;
}

.table-frame :deep(.ant-table-cell) {
  overflow-wrap: break-word;
}

.list-panel :deep(.ant-table-thead > tr > th) {
  background: #f5f5f5;
  color: #2b3340;
  font-weight: 600;
}

.table-frame :deep(.ant-empty) {
  margin: 120px 0;
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

:global(.user-column-popover .ant-popover-inner) {
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
  grid-template-columns: 18px 18px 1fr 24px 24px;
  align-items: center;
  height: 26px;
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

.pin-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  color: #4b5563;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.pin-button.active {
  color: #3164f4;
}

.pin-button:disabled {
  color: #9ca3af;
  cursor: default;
}

.rotate-pin {
  transform: rotate(90deg);
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
    grid-template-columns: repeat(2, minmax(260px, 1fr));
    column-gap: 24px;
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

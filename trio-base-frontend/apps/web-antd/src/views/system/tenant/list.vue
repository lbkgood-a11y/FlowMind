<script setup lang="ts">
import type { SystemTenantApi } from '#/api';
import type { TableProps } from 'ant-design-vue';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { IconifyIcon, Plus } from '@vben/icons';

import {
  Button,
  DatePicker,
  Descriptions,
  DescriptionsItem,
  Drawer,
  Empty,
  Form,
  FormItem,
  Input,
  InputNumber,
  message,
  Modal,
  Pagination,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
} from 'ant-design-vue';

import {
  createTenant,
  deleteTenantSetting,
  getTenantPage,
  getTenantSettings,
  saveTenantSetting,
  updateTenant,
  updateTenantStatus,
} from '#/api';
import { ERP_TOOLBAR_ICONS } from '#/constants/erp-toolbar';
import {
  BusinessPageScaffold,
  CompactQueryBar,
  CompactTableFrame,
  CompactToolbar,
} from '#/shared';

const Textarea = Input.TextArea;

const TENANT_PERMISSIONS = {
  create: '/api/v1/tenants:POST',
  deleteSetting: '/api/v1/tenants/*:DELETE',
  query: '/api/v1/tenants:GET',
  queryDetail: '/api/v1/tenants/*:GET',
  update: '/api/v1/tenants/*:PUT',
} as const;

type TenantFormModel = {
  attributesJson?: string;
  contactEmail?: string;
  contactName?: string;
  contactPhone?: string;
  expireAt?: string;
  industry?: string;
  locale?: string;
  maxUsers?: number;
  planCode?: string;
  region?: string;
  shortName?: string;
  tenantId?: string;
  tenantName: string;
  tenantType?: string;
  timezone?: string;
};

type SettingFormModel = {
  description?: string;
  enabled: boolean;
  sensitive: boolean;
  settingKey: string;
  settingValue?: string;
  valueType: string;
};

const { hasAccessByCodes } = useAccess();
const canQuery = computed(() => hasAccessByCodes([TENANT_PERMISSIONS.query]));
const canCreate = computed(() => hasAccessByCodes([TENANT_PERMISSIONS.create]));
const canUpdate = computed(() => hasAccessByCodes([TENANT_PERMISSIONS.update]));
const canQueryDetail = computed(() =>
  hasAccessByCodes([TENANT_PERMISSIONS.queryDetail]),
);
const canDeleteSetting = computed(() =>
  hasAccessByCodes([TENANT_PERMISSIONS.deleteSetting]),
);

const tenants = ref<SystemTenantApi.Tenant[]>([]);
const settings = ref<SystemTenantApi.TenantSetting[]>([]);
const loading = ref(false);
const settingsLoading = ref(false);
const saving = ref(false);
const settingSaving = ref(false);
const formOpen = ref(false);
const detailOpen = ref(false);
const settingsOpen = ref(false);
const settingFormOpen = ref(false);
const editingTenant = ref<SystemTenantApi.Tenant>();
const detailTenant = ref<SystemTenantApi.Tenant>();
const activeTenant = ref<SystemTenantApi.Tenant>();
const editingSetting = ref<SystemTenantApi.TenantSetting>();

const query = reactive({
  keyword: '',
  status: undefined as SystemTenantApi.TenantStatus | undefined,
});

const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
});

const formModel = reactive<TenantFormModel>({
  attributesJson: '',
  contactEmail: '',
  contactName: '',
  contactPhone: '',
  expireAt: undefined,
  industry: '',
  locale: 'zh-CN',
  maxUsers: 100,
  planCode: 'STANDARD',
  region: '',
  shortName: '',
  tenantId: '',
  tenantName: '',
  tenantType: 'ENTERPRISE',
  timezone: 'Asia/Shanghai',
});

const settingForm = reactive<SettingFormModel>({
  description: '',
  enabled: true,
  sensitive: false,
  settingKey: '',
  settingValue: '',
  valueType: 'STRING',
});

const statusOptions: Array<{
  label: string;
  value: SystemTenantApi.TenantStatus;
}> = [
  { label: '启用', value: 'ACTIVE' },
  { label: '暂停', value: 'SUSPENDED' },
  { label: '停用', value: 'DISABLED' },
];

const tenantTypeOptions = [
  { label: '企业租户', value: 'ENTERPRISE' },
  { label: '内部租户', value: 'INTERNAL' },
  { label: '合作伙伴', value: 'PARTNER' },
];

const planOptions = [
  { label: '标准版', value: 'STANDARD' },
  { label: '专业版', value: 'PROFESSIONAL' },
  { label: '企业版', value: 'ENTERPRISE' },
];

const localeOptions = [
  { label: '简体中文', value: 'zh-CN' },
  { label: 'English', value: 'en-US' },
];

const settingTypeOptions = [
  { label: '字符串 STRING', value: 'STRING' },
  { label: '整数 INTEGER', value: 'INTEGER' },
  { label: '布尔 BOOLEAN', value: 'BOOLEAN' },
  { label: 'JSON', value: 'JSON' },
];

const columns = computed<TableProps['columns']>(() => [
  {
    dataIndex: 'tenantName',
    fixed: 'left',
    key: 'tenantName',
    title: '租户名称',
    width: 180,
  },
  { dataIndex: 'tenantId', key: 'tenantId', title: '租户ID', width: 150 },
  { dataIndex: 'tenantType', key: 'tenantType', title: '类型', width: 110 },
  { dataIndex: 'status', key: 'status', title: '状态', width: 110 },
  { dataIndex: 'planCode', key: 'planCode', title: '套餐', width: 120 },
  { dataIndex: 'maxUsers', key: 'maxUsers', title: '用户上限', width: 100 },
  { dataIndex: 'contactName', key: 'contactName', title: '联系人', width: 120 },
  { dataIndex: 'region', key: 'region', title: '区域', width: 120 },
  { dataIndex: 'expireAt', key: 'expireAt', title: '到期时间', width: 180 },
  { dataIndex: 'updatedAt', key: 'updatedAt', title: '更新时间', width: 180 },
  { fixed: 'right', key: 'action', title: '操作', width: 250 },
]);

const settingColumns = computed<TableProps['columns']>(() => [
  {
    dataIndex: 'settingKey',
    fixed: 'left',
    key: 'settingKey',
    title: '配置键',
    width: 220,
  },
  { dataIndex: 'settingValue', key: 'settingValue', title: '配置值', width: 220 },
  { dataIndex: 'valueType', key: 'valueType', title: '类型', width: 110 },
  { dataIndex: 'enabled', key: 'enabled', title: '启用', width: 90 },
  { dataIndex: 'sensitive', key: 'sensitive', title: '敏感', width: 90 },
  { dataIndex: 'description', key: 'description', title: '描述', width: 260 },
  { dataIndex: 'updatedAt', key: 'updatedAt', title: '更新时间', width: 180 },
  { fixed: 'right', key: 'action', title: '操作', width: 130 },
]);

function statusLabel(status?: SystemTenantApi.TenantStatus) {
  return statusOptions.find((item) => item.value === status)?.label ?? status ?? '-';
}

function statusColor(status?: SystemTenantApi.TenantStatus) {
  if (status === 'ACTIVE') {
    return 'success';
  }
  if (status === 'SUSPENDED') {
    return 'warning';
  }
  if (status === 'DISABLED') {
    return 'error';
  }
  return 'default';
}

function tenantTypeLabel(value?: string) {
  return tenantTypeOptions.find((item) => item.value === value)?.label ?? value ?? '-';
}

function planLabel(value?: string) {
  return planOptions.find((item) => item.value === value)?.label ?? value ?? '-';
}

async function loadTenants(page = pagination.current) {
  if (!canQuery.value) {
    tenants.value = [];
    pagination.total = 0;
    return;
  }
  loading.value = true;
  try {
    const result = await getTenantPage({
      keyword: query.keyword.trim() || undefined,
      page,
      size: pagination.pageSize,
      status: query.status,
    });
    tenants.value = result.items;
    pagination.current = page;
    pagination.total = result.total;
  } finally {
    loading.value = false;
  }
}

function resetQuery() {
  query.keyword = '';
  query.status = undefined;
  loadTenants(1);
}

function resetForm() {
  editingTenant.value = undefined;
  formModel.attributesJson = '';
  formModel.contactEmail = '';
  formModel.contactName = '';
  formModel.contactPhone = '';
  formModel.expireAt = undefined;
  formModel.industry = '';
  formModel.locale = 'zh-CN';
  formModel.maxUsers = 100;
  formModel.planCode = 'STANDARD';
  formModel.region = '';
  formModel.shortName = '';
  formModel.tenantId = '';
  formModel.tenantName = '';
  formModel.tenantType = 'ENTERPRISE';
  formModel.timezone = 'Asia/Shanghai';
}

function openCreate() {
  resetForm();
  formOpen.value = true;
}

function openEdit(record: SystemTenantApi.Tenant) {
  editingTenant.value = record;
  formModel.attributesJson = record.attributesJson ?? '';
  formModel.contactEmail = record.contactEmail ?? '';
  formModel.contactName = record.contactName ?? '';
  formModel.contactPhone = record.contactPhone ?? '';
  formModel.expireAt = record.expireAt;
  formModel.industry = record.industry ?? '';
  formModel.locale = record.locale ?? 'zh-CN';
  formModel.maxUsers = record.maxUsers ?? 100;
  formModel.planCode = record.planCode ?? 'STANDARD';
  formModel.region = record.region ?? '';
  formModel.shortName = record.shortName ?? '';
  formModel.tenantId = record.tenantId;
  formModel.tenantName = record.tenantName;
  formModel.tenantType = record.tenantType ?? 'ENTERPRISE';
  formModel.timezone = record.timezone ?? 'Asia/Shanghai';
  formOpen.value = true;
}

function buildTenantPayload() {
  const payload: SystemTenantApi.SaveTenantParams = {
    attributesJson: formModel.attributesJson?.trim() || undefined,
    contactEmail: formModel.contactEmail?.trim() || undefined,
    contactName: formModel.contactName?.trim() || undefined,
    contactPhone: formModel.contactPhone?.trim() || undefined,
    expireAt: formModel.expireAt,
    industry: formModel.industry?.trim() || undefined,
    locale: formModel.locale,
    maxUsers: formModel.maxUsers,
    planCode: formModel.planCode,
    region: formModel.region?.trim() || undefined,
    shortName: formModel.shortName?.trim() || undefined,
    tenantName: formModel.tenantName.trim(),
    tenantType: formModel.tenantType,
    timezone: formModel.timezone?.trim() || undefined,
  };

  if (!editingTenant.value) {
    payload.tenantId = formModel.tenantId?.trim();
  }

  return payload;
}

async function submitTenantForm() {
  if (editingTenant.value ? !canUpdate.value : !canCreate.value) {
    message.warning('当前账号没有保存租户的权限');
    return;
  }
  if (!formModel.tenantName.trim()) {
    message.warning('请输入租户名称');
    return;
  }
  if (!editingTenant.value && !formModel.tenantId?.trim()) {
    message.warning('请输入租户ID');
    return;
  }

  saving.value = true;
  try {
    if (editingTenant.value) {
      await updateTenant(editingTenant.value.tenantId, buildTenantPayload());
      message.success('租户已更新');
    } else {
      await createTenant(buildTenantPayload());
      message.success('租户已创建');
    }
    formOpen.value = false;
    await loadTenants(editingTenant.value ? pagination.current : 1);
  } finally {
    saving.value = false;
  }
}

function openDetail(record: SystemTenantApi.Tenant) {
  detailTenant.value = record;
  detailOpen.value = true;
}

function confirmStatus(
  record: SystemTenantApi.Tenant,
  status: SystemTenantApi.TenantStatus,
) {
  const label = statusLabel(status);
  Modal.confirm({
    content: `确认将租户 ${record.tenantName} 调整为${label}？`,
    onOk: async () => {
      const updated = await updateTenantStatus(
        record.tenantId,
        status,
        status === 'ACTIVE' ? undefined : `页面操作：${label}`,
      );
      Object.assign(record, updated);
      message.success('租户状态已更新');
    },
    title: '调整租户状态',
  });
}

async function openSettings(record: SystemTenantApi.Tenant) {
  if (!canQueryDetail.value) {
    message.warning('当前账号没有查看租户设置的权限');
    return;
  }
  activeTenant.value = record;
  settingsOpen.value = true;
  await loadSettings(record.tenantId);
}

async function loadSettings(tenantId = activeTenant.value?.tenantId) {
  if (!tenantId || !canQueryDetail.value) {
    settings.value = [];
    return;
  }
  settingsLoading.value = true;
  try {
    settings.value = await getTenantSettings(tenantId);
  } finally {
    settingsLoading.value = false;
  }
}

function resetSettingForm() {
  editingSetting.value = undefined;
  settingForm.description = '';
  settingForm.enabled = true;
  settingForm.sensitive = false;
  settingForm.settingKey = '';
  settingForm.settingValue = '';
  settingForm.valueType = 'STRING';
}

function openCreateSetting() {
  resetSettingForm();
  settingFormOpen.value = true;
}

function openEditSetting(record: SystemTenantApi.TenantSetting) {
  editingSetting.value = record;
  settingForm.description = record.description ?? '';
  settingForm.enabled = record.enabled;
  settingForm.sensitive = record.sensitive;
  settingForm.settingKey = record.settingKey;
  settingForm.settingValue = record.settingValue ?? '';
  settingForm.valueType = record.valueType ?? 'STRING';
  settingFormOpen.value = true;
}

async function submitSettingForm() {
  if (!activeTenant.value || !canUpdate.value) {
    message.warning('当前账号没有保存租户设置的权限');
    return;
  }
  if (!settingForm.settingKey.trim()) {
    message.warning('请输入配置键');
    return;
  }

  const settingKey = settingForm.settingKey.trim();
  settingSaving.value = true;
  try {
    await saveTenantSetting(activeTenant.value.tenantId, settingKey, {
      description: settingForm.description?.trim() || undefined,
      enabled: settingForm.enabled,
      sensitive: settingForm.sensitive,
      settingKey,
      settingValue: settingForm.settingValue,
      valueType: settingForm.valueType,
    });
    message.success(editingSetting.value ? '租户设置已更新' : '租户设置已创建');
    settingFormOpen.value = false;
    await loadSettings();
  } finally {
    settingSaving.value = false;
  }
}

async function removeSetting(record: SystemTenantApi.TenantSetting) {
  if (!activeTenant.value) {
    return;
  }
  await deleteTenantSetting(activeTenant.value.tenantId, record.settingKey);
  message.success('租户设置已删除');
  await loadSettings();
}

function onPageChange(page: number, pageSize: number) {
  pagination.pageSize = pageSize;
  loadTenants(page);
}

function onPageSizeChange(page: number, pageSize: number) {
  pagination.pageSize = pageSize;
  loadTenants(page);
}

function asTenant(record: Record<string, any>) {
  return record as SystemTenantApi.Tenant;
}

function asSetting(record: Record<string, any>) {
  return record as SystemTenantApi.TenantSetting;
}

onMounted(loadTenants);
</script>

<template>
  <Page auto-content-height>
    <BusinessPageScaffold class="tenant-page" pattern="single-table">
      <template #query>
        <CompactQueryBar :columns="3">
          <Input
            v-model:value="query.keyword"
            allow-clear
            class="query-input"
            placeholder="租户名称/ID/联系人"
            @press-enter="loadTenants(1)"
          />
          <Select
            v-model:value="query.status"
            allow-clear
            class="query-select"
            :options="statusOptions"
            placeholder="状态"
          />
          <template #actions>
            <Button v-if="canQuery" @click="resetQuery">重置</Button>
            <Button v-if="canQuery" type="primary" @click="loadTenants(1)">
              查询
            </Button>
            <Tooltip v-if="canQuery" title="刷新">
              <Button shape="circle" @click="loadTenants()">
                <IconifyIcon :icon="ERP_TOOLBAR_ICONS.refresh" class="size-4" />
              </Button>
            </Tooltip>
          </template>
        </CompactQueryBar>
      </template>

      <template #toolbar>
        <CompactToolbar title="租户管理" subtitle="维护租户资料、状态和运行级配置">
          <Button v-if="canCreate" type="primary" @click="openCreate">
            <Plus class="size-4" />
            新增租户
          </Button>
        </CompactToolbar>
      </template>

      <CompactTableFrame>
        <Table
          row-key="tenantId"
          :columns="columns"
          :data-source="tenants"
          :loading="loading"
          :pagination="false"
          :scroll="{ x: 1600 }"
          size="small"
          :sticky="{ offsetScroll: 0 }"
          table-layout="fixed"
        >
          <template #emptyText>
            <Empty description="暂无租户" />
          </template>

          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'tenantName'">
              <div class="tenant-name-cell">
                <strong>{{ record.tenantName }}</strong>
                <span>{{ record.shortName || record.tenantCode || '-' }}</span>
              </div>
            </template>
            <template v-else-if="column.key === 'tenantId'">
              <span class="tenant-id-cell">{{ record.tenantId }}</span>
            </template>
            <template v-else-if="column.key === 'tenantType'">
              {{ tenantTypeLabel(record.tenantType) }}
            </template>
            <template v-else-if="column.key === 'status'">
              <Tag :color="statusColor(record.status)">
                {{ statusLabel(record.status) }}
              </Tag>
            </template>
            <template v-else-if="column.key === 'planCode'">
              {{ planLabel(record.planCode) }}
            </template>
            <template v-else-if="column.key === 'maxUsers'">
              {{ record.maxUsers ?? '-' }}
            </template>
            <template v-else-if="column.key === 'contactName'">
              {{ record.contactName || '-' }}
            </template>
            <template v-else-if="column.key === 'region'">
              {{ record.region || '-' }}
            </template>
            <template v-else-if="column.key === 'expireAt'">
              {{ record.expireAt || '-' }}
            </template>
            <template v-else-if="column.key === 'updatedAt'">
              {{ record.updatedAt || '-' }}
            </template>
            <template v-else-if="column.key === 'action'">
              <Space :size="4">
                <Button size="small" type="link" @click="openDetail(asTenant(record))">
                  详情
                </Button>
                <Button
                  v-if="canUpdate"
                  size="small"
                  type="link"
                  @click="openEdit(asTenant(record))"
                >
                  编辑
                </Button>
                <Button
                  v-if="canQueryDetail"
                  size="small"
                  type="link"
                  @click="openSettings(asTenant(record))"
                >
                  设置
                </Button>
                <Button
                  v-if="canUpdate && record.status !== 'ACTIVE'"
                  size="small"
                  type="link"
                  @click="confirmStatus(asTenant(record), 'ACTIVE')"
                >
                  启用
                </Button>
                <Button
                  v-if="canUpdate && record.status === 'ACTIVE'"
                  size="small"
                  type="link"
                  @click="confirmStatus(asTenant(record), 'SUSPENDED')"
                >
                  暂停
                </Button>
                <Button
                  v-if="canUpdate && record.status !== 'DISABLED'"
                  danger
                  size="small"
                  type="link"
                  @click="confirmStatus(asTenant(record), 'DISABLED')"
                >
                  停用
                </Button>
              </Space>
            </template>
          </template>
        </Table>

        <template #footer>
          <div class="table-total">共 {{ pagination.total }} 条记录</div>
          <Pagination
            v-model:current="pagination.current"
            v-model:page-size="pagination.pageSize"
            :page-size-options="['10', '20', '50', '100']"
            :total="pagination.total"
            show-less-items
            show-size-changer
            size="small"
            @change="onPageChange"
            @show-size-change="onPageSizeChange"
          />
        </template>
      </CompactTableFrame>
    </BusinessPageScaffold>

    <Drawer
      v-model:open="formOpen"
      :title="editingTenant ? '编辑租户' : '新增租户'"
      destroy-on-close
      placement="right"
      width="760"
    >
      <Form layout="vertical">
        <div class="form-grid">
          <FormItem label="租户ID" :required="!editingTenant">
            <Input
              v-model:value="formModel.tenantId"
              :disabled="!!editingTenant"
              placeholder="例如：acme"
            />
          </FormItem>
          <FormItem label="租户名称" required>
            <Input v-model:value="formModel.tenantName" placeholder="请输入租户名称" />
          </FormItem>
          <FormItem label="简称">
            <Input v-model:value="formModel.shortName" placeholder="用于表格和侧栏展示" />
          </FormItem>
          <FormItem label="租户类型">
            <Select v-model:value="formModel.tenantType" :options="tenantTypeOptions" />
          </FormItem>
          <FormItem label="套餐">
            <Select v-model:value="formModel.planCode" :options="planOptions" />
          </FormItem>
          <FormItem label="用户上限">
            <InputNumber v-model:value="formModel.maxUsers" class="w-full" :min="1" />
          </FormItem>
          <FormItem label="联系人">
            <Input v-model:value="formModel.contactName" placeholder="请输入联系人" />
          </FormItem>
          <FormItem label="联系邮箱">
            <Input v-model:value="formModel.contactEmail" placeholder="admin@example.com" />
          </FormItem>
          <FormItem label="联系电话">
            <Input v-model:value="formModel.contactPhone" placeholder="请输入联系电话" />
          </FormItem>
          <FormItem label="区域">
            <Input v-model:value="formModel.region" placeholder="例如：华东" />
          </FormItem>
          <FormItem label="时区">
            <Input v-model:value="formModel.timezone" placeholder="Asia/Shanghai" />
          </FormItem>
          <FormItem label="语言">
            <Select v-model:value="formModel.locale" :options="localeOptions" />
          </FormItem>
          <FormItem label="行业">
            <Input v-model:value="formModel.industry" placeholder="例如：制造业" />
          </FormItem>
          <FormItem label="到期时间">
            <DatePicker
              v-model:value="formModel.expireAt"
              class="w-full"
              show-time
              value-format="YYYY-MM-DDTHH:mm:ss"
            />
          </FormItem>
          <FormItem class="form-wide" label="扩展属性 JSON">
            <Textarea
              v-model:value="formModel.attributesJson"
              :rows="4"
              placeholder='{"featureFlags":["rag","workflow"]}'
            />
          </FormItem>
        </div>
      </Form>

      <template #footer>
        <Space>
          <Button @click="formOpen = false">取消</Button>
          <Button
            :disabled="editingTenant ? !canUpdate : !canCreate"
            :loading="saving"
            type="primary"
            @click="submitTenantForm"
          >
            保存
          </Button>
        </Space>
      </template>
    </Drawer>

    <Drawer
      v-model:open="detailOpen"
      :footer="null"
      placement="right"
      title="租户详情"
      width="560"
    >
      <Descriptions v-if="detailTenant" bordered :column="1" size="small">
        <DescriptionsItem label="租户名称">{{ detailTenant.tenantName }}</DescriptionsItem>
        <DescriptionsItem label="租户ID">{{ detailTenant.tenantId }}</DescriptionsItem>
        <DescriptionsItem label="租户编码">{{ detailTenant.tenantCode || '-' }}</DescriptionsItem>
        <DescriptionsItem label="类型">
          {{ tenantTypeLabel(detailTenant.tenantType) }}
        </DescriptionsItem>
        <DescriptionsItem label="状态">
          <Tag :color="statusColor(detailTenant.status)">
            {{ statusLabel(detailTenant.status) }}
          </Tag>
        </DescriptionsItem>
        <DescriptionsItem label="隔离模式">
          {{ detailTenant.isolationMode || '-' }}
        </DescriptionsItem>
        <DescriptionsItem label="套餐">{{ planLabel(detailTenant.planCode) }}</DescriptionsItem>
        <DescriptionsItem label="用户上限">{{ detailTenant.maxUsers ?? '-' }}</DescriptionsItem>
        <DescriptionsItem label="联系人">{{ detailTenant.contactName || '-' }}</DescriptionsItem>
        <DescriptionsItem label="联系邮箱">
          {{ detailTenant.contactEmail || '-' }}
        </DescriptionsItem>
        <DescriptionsItem label="联系电话">
          {{ detailTenant.contactPhone || '-' }}
        </DescriptionsItem>
        <DescriptionsItem label="区域">{{ detailTenant.region || '-' }}</DescriptionsItem>
        <DescriptionsItem label="时区">{{ detailTenant.timezone || '-' }}</DescriptionsItem>
        <DescriptionsItem label="语言">{{ detailTenant.locale || '-' }}</DescriptionsItem>
        <DescriptionsItem label="行业">{{ detailTenant.industry || '-' }}</DescriptionsItem>
        <DescriptionsItem label="到期时间">{{ detailTenant.expireAt || '-' }}</DescriptionsItem>
        <DescriptionsItem label="暂停原因">
          {{ detailTenant.suspendedReason || '-' }}
        </DescriptionsItem>
        <DescriptionsItem label="创建时间">{{ detailTenant.createdAt || '-' }}</DescriptionsItem>
        <DescriptionsItem label="更新时间">{{ detailTenant.updatedAt || '-' }}</DescriptionsItem>
      </Descriptions>
    </Drawer>

    <Drawer
      v-model:open="settingsOpen"
      destroy-on-close
      placement="right"
      title="租户设置"
      width="900"
    >
      <div class="settings-header">
        <div>
          <strong>{{ activeTenant?.tenantName || '-' }}</strong>
          <span>{{ activeTenant?.tenantId || '-' }}</span>
        </div>
        <Space>
          <Tooltip title="刷新">
            <Button shape="circle" @click="loadSettings()">
              <IconifyIcon :icon="ERP_TOOLBAR_ICONS.refresh" class="size-4" />
            </Button>
          </Tooltip>
          <Button v-if="canUpdate" type="primary" @click="openCreateSetting">
            <Plus class="size-4" />
            新增设置
          </Button>
        </Space>
      </div>

      <Table
        row-key="settingKey"
        :columns="settingColumns"
        :data-source="settings"
        :loading="settingsLoading"
        :pagination="false"
        :scroll="{ x: 1210 }"
        size="small"
        table-layout="fixed"
      >
        <template #emptyText>
          <Empty description="暂无设置" />
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'settingValue'">
            <span class="setting-value">
              {{ record.sensitive ? '******' : record.settingValue || '-' }}
            </span>
          </template>
          <template v-else-if="column.key === 'enabled'">
            <Tag :color="record.enabled ? 'success' : 'default'">
              {{ record.enabled ? '是' : '否' }}
            </Tag>
          </template>
          <template v-else-if="column.key === 'sensitive'">
            <Tag :color="record.sensitive ? 'warning' : 'default'">
              {{ record.sensitive ? '是' : '否' }}
            </Tag>
          </template>
          <template v-else-if="column.key === 'description'">
            {{ record.description || '-' }}
          </template>
          <template v-else-if="column.key === 'updatedAt'">
            {{ record.updatedAt || '-' }}
          </template>
          <template v-else-if="column.key === 'action'">
            <Space>
              <Button
                v-if="canUpdate"
                size="small"
                type="link"
                @click="openEditSetting(asSetting(record))"
              >
                编辑
              </Button>
              <Popconfirm
                v-if="canDeleteSetting"
                title="确认删除该设置？"
                ok-text="删除"
                cancel-text="取消"
                @confirm="removeSetting(asSetting(record))"
              >
                <Button danger size="small" type="link">删除</Button>
              </Popconfirm>
            </Space>
          </template>
        </template>
      </Table>
    </Drawer>

    <Drawer
      v-model:open="settingFormOpen"
      :title="editingSetting ? '编辑租户设置' : '新增租户设置'"
      destroy-on-close
      placement="right"
      width="520"
    >
      <Form layout="vertical">
        <FormItem label="配置键" required>
          <Input
            v-model:value="settingForm.settingKey"
            :disabled="!!editingSetting"
            placeholder="例如：ai.rag.enabled"
          />
        </FormItem>
        <FormItem label="类型">
          <Select v-model:value="settingForm.valueType" :options="settingTypeOptions" />
        </FormItem>
        <FormItem label="配置值">
          <Textarea
            v-model:value="settingForm.settingValue"
            :rows="4"
            placeholder="请输入配置值"
          />
        </FormItem>
        <FormItem label="启用">
          <Switch
            v-model:checked="settingForm.enabled"
            checked-children="是"
            un-checked-children="否"
          />
        </FormItem>
        <FormItem label="敏感值">
          <Switch
            v-model:checked="settingForm.sensitive"
            checked-children="是"
            un-checked-children="否"
          />
        </FormItem>
        <FormItem label="描述">
          <Textarea
            v-model:value="settingForm.description"
            :rows="3"
            placeholder="请输入描述"
          />
        </FormItem>
      </Form>

      <template #footer>
        <Space>
          <Button @click="settingFormOpen = false">取消</Button>
          <Button
            :disabled="!canUpdate"
            :loading="settingSaving"
            type="primary"
            @click="submitSettingForm"
          >
            保存
          </Button>
        </Space>
      </template>
    </Drawer>
  </Page>
</template>

<style scoped>
.tenant-page {
  display: flex;
  min-height: 100%;
  flex-direction: column;
  gap: 8px;
}

.query-input {
  width: 220px;
}

.query-select {
  width: 140px;
}

.tenant-name-cell,
.settings-header > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.tenant-name-cell span,
.settings-header span {
  overflow: hidden;
  color: #6b7280;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tenant-id-cell,
.setting-value {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
}

.form-wide {
  grid-column: 1 / -1;
}

.settings-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

@media (max-width: 760px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .settings-header {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }
}
</style>

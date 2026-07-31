<script setup lang="ts">
import type { TableProps } from 'ant-design-vue';

import type { SystemGovernanceApi } from '#/api';
import type { TableColumnSetting } from '#/shared';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import {
  Button,
  Drawer,
  FormItem,
  Input,
  InputNumber,
  message,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
} from 'ant-design-vue';

import { getSystemConfigs, updateSystemConfig } from '#/api';
import { ERP_TOOLBAR_ICONS } from '#/constants/erp-toolbar';
import {
  BusinessPageScaffold,
  CompactQueryBar,
  CompactTableFrame,
  CompactToolbar,
  restoreTableColumnSettings,
  TableColumnSettings,
} from '#/shared';

const Textarea = Input.TextArea;

const CONFIG_PERMISSIONS = {
  query: '/api/v1/system-configs:GET',
  update: '/api/v1/system-configs/*:PUT',
} as const;

type ConfigFormModel = {
  configGroup: string;
  configType: string;
  configValue?: string;
  defaultValue?: string;
  description?: string;
  sensitive: 0 | 1;
  sortOrder: number;
  status: 0 | 1;
};

const { hasAccessByCodes } = useAccess();
const canQuery = computed(() => hasAccessByCodes([CONFIG_PERMISSIONS.query]));
const canUpdate = computed(() => hasAccessByCodes([CONFIG_PERMISSIONS.update]));

const configs = ref<SystemGovernanceApi.SystemConfig[]>([]);
const loading = ref(false);
const saving = ref(false);
const formOpen = ref(false);
const editingConfig = ref<SystemGovernanceApi.SystemConfig>();
const collapsed = ref(false);
const queryHidden = ref(false);
const blockFullscreen = ref(false);
const tableKey = ref(0);

const query = reactive({
  configGroup: undefined as string | undefined,
  keyword: '',
  status: undefined as 0 | 1 | undefined,
});

const formModel = reactive<ConfigFormModel>({
  configGroup: '',
  configType: 'STRING',
  configValue: '',
  defaultValue: '',
  description: '',
  sensitive: 0,
  sortOrder: 100,
  status: 1,
});

const groupOptions = computed(() => {
  const groups = new Set(configs.value.map((item) => item.configGroup).filter(Boolean));
  return [...groups].sort().map((value) => ({ label: value, value }));
});

const configTypeOptions = [
  { label: '字符串 STRING', value: 'STRING' },
  { label: '整数 INTEGER', value: 'INTEGER' },
  { label: '布尔 BOOLEAN', value: 'BOOLEAN' },
  { label: 'JSON', value: 'JSON' },
];

const baseColumns: NonNullable<TableProps['columns']> = [
  { dataIndex: 'configGroup', fixed: 'left', key: 'configGroup', title: '分组', width: 130 },
  { dataIndex: 'configKey', key: 'configKey', title: '参数键', width: 260 },
  { dataIndex: 'configValue', key: 'configValue', title: '参数值', width: 220 },
  { dataIndex: 'defaultValue', key: 'defaultValue', title: '默认值', width: 180 },
  { dataIndex: 'configType', key: 'configType', title: '类型', width: 110 },
  { dataIndex: 'status', key: 'status', title: '状态', width: 90 },
  { dataIndex: 'sensitive', key: 'sensitive', title: '敏感', width: 90 },
  { dataIndex: 'sortOrder', key: 'sortOrder', title: '排序', width: 80 },
  { dataIndex: 'description', key: 'description', title: '描述', width: 280 },
  { fixed: 'right', key: 'action', title: '操作', width: 100 },
];
const defaultColumnSettings: TableColumnSetting[] = baseColumns.map((column) => ({
  fixed: column.fixed === true ? 'left' : column.fixed || undefined,
  key: String(column.key),
  required: column.key === 'action',
  title: String(column.title),
  visible: true,
  width: Number(column.width || 120),
}));
const columnSettings = reactive(
  restoreTableColumnSettings(
    'triobase:table-columns:system-config',
    defaultColumnSettings,
  ),
);
const columns = computed<TableProps['columns']>(() =>
  columnSettings.filter((item) => item.visible).map((item) => ({
    ...baseColumns.find((column) => String(column.key) === item.key),
    fixed: item.fixed,
    width: item.width,
  })),
);

async function loadConfigs() {
  if (!canQuery.value) {
    configs.value = [];
    return;
  }
  loading.value = true;
  try {
    configs.value = await getSystemConfigs({
      configGroup: query.configGroup,
      keyword: query.keyword || undefined,
      status: query.status,
    });
  } finally {
    loading.value = false;
  }
}

function resetQuery() {
  query.configGroup = undefined;
  query.keyword = '';
  query.status = undefined;
  loadConfigs();
}

async function handleToolbarSearch() {
  await loadConfigs();
  queryHidden.value = true;
}

function applyColumnSettings(settings: TableColumnSetting[]) {
  columnSettings.splice(0, columnSettings.length, ...settings);
  tableKey.value += 1;
}

function toggleFullscreen() {
  blockFullscreen.value = !blockFullscreen.value;
}

function openEdit(record: SystemGovernanceApi.SystemConfig) {
  editingConfig.value = record;
  formModel.configGroup = record.configGroup;
  formModel.configType = record.configType;
  formModel.configValue = record.configValue ?? '';
  formModel.defaultValue = record.defaultValue ?? '';
  formModel.description = record.description ?? '';
  formModel.sensitive = (record.sensitive ?? 0) as 0 | 1;
  formModel.sortOrder = record.sortOrder ?? 100;
  formModel.status = (record.status ?? 1) as 0 | 1;
  formOpen.value = true;
}

async function submitForm() {
  if (!canUpdate.value) {
    message.warning('当前账号没有修改系统参数的权限');
    return;
  }
  if (!editingConfig.value) {
    return;
  }
  if (!formModel.configGroup.trim()) {
    message.warning('请输入参数分组');
    return;
  }
  saving.value = true;
  try {
    await updateSystemConfig(editingConfig.value.id, {
      configGroup: formModel.configGroup.trim(),
      configType: formModel.configType,
      configValue: formModel.configValue,
      defaultValue: formModel.defaultValue,
      description: formModel.description?.trim() || undefined,
      sensitive: formModel.sensitive,
      sortOrder: formModel.sortOrder,
      status: formModel.status,
    });
    message.success('系统参数已更新');
    formOpen.value = false;
    await loadConfigs();
  } finally {
    saving.value = false;
  }
}

function asConfig(record: Record<string, any>) {
  return record as SystemGovernanceApi.SystemConfig;
}

onMounted(loadConfigs);
</script>

<template>
  <Page auto-content-height>
    <BusinessPageScaffold class="config-page" pattern="single-table" :fullscreen="blockFullscreen" :class="{ 'is-block-fullscreen': blockFullscreen, 'is-query-hidden': queryHidden }">
      <template #query>
        <CompactQueryBar v-show="!queryHidden" :collapsed="collapsed" :columns="3">
          <FormItem label="参数分组">
<Select
            v-model:value="query.configGroup"
            allow-clear
            :options="groupOptions"
            placeholder="请选择"
          />
</FormItem>
          <FormItem label="关键词"><Input v-model:value="query.keyword" placeholder="参数键/描述" allow-clear /></FormItem>
          <FormItem v-if="!collapsed" label="状态">
<Select
            v-model:value="query.status"
            allow-clear
            :options="[
              { label: '启用', value: 1 },
              { label: '禁用', value: 0 },
            ]"
            placeholder="请选择"
          />
</FormItem>
          <template #actions>
            <Button v-if="canQuery" @click="resetQuery">重置</Button>
            <Button v-if="canQuery" type="primary" @click="loadConfigs">搜索</Button>
            <Button type="link" @click="collapsed = !collapsed">{{ collapsed ? '展开' : '收起' }}<IconifyIcon :icon="collapsed ? ERP_TOOLBAR_ICONS.expand : ERP_TOOLBAR_ICONS.collapse" class="ml-1 size-4" /></Button>
          </template>
        </CompactQueryBar>
      </template>

      <template #toolbar>
        <CompactToolbar>
          <template #title><div class="list-title"><h2>系统参数</h2><Button v-if="queryHidden" type="link" @click="queryHidden = false">展开搜索</Button></div></template>
          <Space :size="8">
            <Tooltip v-if="canQuery" title="查询并隐藏搜索栏"><Button shape="circle" type="primary" @click="handleToolbarSearch"><i aria-hidden="true" class="vxe-button--item vxe-table-icon-search"></i></Button></Tooltip>
            <Tooltip v-if="canQuery" title="刷新"><Button shape="circle" @click="loadConfigs"><i aria-hidden="true" class="vxe-button--item vxe-table-icon-refresh"></i></Button></Tooltip>
            <Tooltip :title="blockFullscreen ? '还原' : '全屏'"><Button shape="circle" @click="toggleFullscreen"><i aria-hidden="true" class="vxe-button--item vxe-button--prefix-icon" :class="blockFullscreen ? 'vxe-table-icon-minimize' : 'vxe-table-icon-fullscreen'"></i></Button></Tooltip>
            <TableColumnSettings :defaults="defaultColumnSettings" :model-value="columnSettings" storage-key="triobase:table-columns:system-config" @apply="applyColumnSettings" />
          </Space>
        </CompactToolbar>
      </template>

      <CompactTableFrame>
        <Table
          :key="tableKey"
          row-key="id"
          :columns="columns"
          :data-source="configs"
          :loading="loading"
          :pagination="false"
          :scroll="{ x: 1570 }"
          size="small"
          table-layout="fixed"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <Tag :color="record.status === 1 ? 'green' : 'default'">
                {{ record.status === 1 ? '启用' : '禁用' }}
              </Tag>
            </template>
            <template v-else-if="column.key === 'sensitive'">
              <Tag :color="record.sensitive === 1 ? 'orange' : 'default'">
                {{ record.sensitive === 1 ? '是' : '否' }}
              </Tag>
            </template>
            <template v-else-if="column.key === 'action'">
              <Button v-if="canUpdate" type="link" size="small" @click="openEdit(asConfig(record))">
                编辑
              </Button>
            </template>
          </template>
        </Table>
        <template #footer><div class="table-total">共 {{ configs.length }} 条记录</div></template>
      </CompactTableFrame>
    </BusinessPageScaffold>

    <Drawer
      v-model:open="formOpen"
      title="编辑系统参数"
      placement="right"
      width="760"
    >
      <div class="form-grid">
        <FormItem label="参数键">
          <Input :value="editingConfig?.configKey" disabled />
        </FormItem>
        <FormItem label="参数分组" required>
          <Input v-model:value="formModel.configGroup" placeholder="请输入参数分组" />
        </FormItem>
        <FormItem label="参数类型">
          <Select v-model:value="formModel.configType" :options="configTypeOptions" />
        </FormItem>
        <FormItem label="排序">
          <InputNumber v-model:value="formModel.sortOrder" class="w-full" :min="0" />
        </FormItem>
        <FormItem label="状态">
          <Switch
            v-model:checked="formModel.status"
            :checked-value="1"
            :un-checked-value="0"
            checked-children="启用"
            un-checked-children="禁用"
          />
        </FormItem>
        <FormItem label="敏感值">
          <Switch
            v-model:checked="formModel.sensitive"
            :checked-value="1"
            :un-checked-value="0"
            checked-children="是"
            un-checked-children="否"
          />
        </FormItem>
        <FormItem class="form-wide" label="参数值">
          <Textarea v-model:value="formModel.configValue" :rows="3" placeholder="请输入参数值" />
        </FormItem>
        <FormItem class="form-wide" label="默认值">
          <Textarea v-model:value="formModel.defaultValue" :rows="2" placeholder="请输入默认值" />
        </FormItem>
        <FormItem class="form-wide" label="描述">
          <Textarea v-model:value="formModel.description" :rows="3" placeholder="请输入描述" />
        </FormItem>
      </div>

      <template #footer>
        <Space>
          <Button @click="formOpen = false">取消</Button>
          <Button :loading="saving" type="primary" @click="submitForm">保存</Button>
        </Space>
      </template>
    </Drawer>
  </Page>
</template>

<style scoped>
.config-page {
  display: flex;
  min-height: 100%;
  flex-direction: column;
  gap: 8px;
}

.toolbar,
.table-shell {
  width: 100%;
}

.table-shell {
  overflow: hidden;
}

.group-select {
  width: 150px;
}

.query-input {
  width: 210px;
}

.query-select {
  width: 120px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
}

.form-wide {
  grid-column: 1 / -1;
}

@media (max-width: 760px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>

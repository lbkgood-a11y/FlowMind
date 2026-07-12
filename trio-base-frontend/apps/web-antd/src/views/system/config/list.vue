<script setup lang="ts">
import type { SystemGovernanceApi } from '#/api';
import type { TableProps } from 'ant-design-vue';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import {
  Button,
  FormItem,
  Input,
  InputNumber,
  message,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
} from 'ant-design-vue';

import { getSystemConfigs, updateSystemConfig } from '#/api';

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

const columns = computed<TableProps['columns']>(() => [
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
]);

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
    <div class="config-page">
      <section class="toolbar">
        <Space wrap>
          <Select
            v-model:value="query.configGroup"
            allow-clear
            class="group-select"
            :options="groupOptions"
            placeholder="参数分组"
          />
          <Input v-model:value="query.keyword" class="query-input" placeholder="参数键/描述" allow-clear />
          <Select
            v-model:value="query.status"
            allow-clear
            class="query-select"
            :options="[
              { label: '启用', value: 1 },
              { label: '禁用', value: 0 },
            ]"
            placeholder="状态"
          />
          <Button v-if="canQuery" type="primary" @click="loadConfigs">查询</Button>
          <Button v-if="canQuery" @click="resetQuery">重置</Button>
          <Tooltip v-if="canQuery" title="刷新">
            <Button shape="circle" @click="loadConfigs">
              <IconifyIcon icon="lucide:refresh-cw" class="size-4" />
            </Button>
          </Tooltip>
        </Space>
      </section>

      <section class="table-shell">
        <Table
          row-key="id"
          :columns="columns"
          :data-source="configs"
          :loading="loading"
          :pagination="false"
          :scroll="{ x: 1570 }"
          :sticky="{ offsetScroll: 0 }"
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
      </section>
    </div>

    <Modal
      v-model:open="formOpen"
      :confirm-loading="saving"
      title="编辑系统参数"
      ok-text="保存"
      width="760px"
      @ok="submitForm"
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
    </Modal>
  </Page>
</template>

<style scoped>
.config-page {
  display: flex;
  min-height: 100%;
  flex-direction: column;
  gap: 12px;
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
  gap: 8px 16px;
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

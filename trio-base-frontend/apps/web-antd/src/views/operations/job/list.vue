<script setup lang="ts">
import type { OperationsApi } from '#/api';
import type { TableProps } from 'ant-design-vue';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import {
  Button,
  FormItem,
  Input,
  message,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
} from 'ant-design-vue';

import {
  createJob,
  deleteJob,
  getJobLogs,
  getJobs,
  triggerJob,
  updateJob,
  updateJobEnabled,
} from '#/api';

const Textarea = Input.TextArea;

const PERMISSIONS = {
  create: '/api/v1/jobs:POST',
  delete: '/api/v1/jobs/*:DELETE',
  logs: '/api/v1/jobs/*/logs:GET',
  query: '/api/v1/jobs:GET',
  trigger: '/api/v1/jobs/*/trigger:POST',
  update: '/api/v1/jobs/*:PUT',
} as const;

const { hasAccessByCodes } = useAccess();
const canQuery = computed(() => hasAccessByCodes([PERMISSIONS.query]));
const canCreate = computed(() => hasAccessByCodes([PERMISSIONS.create]));
const canUpdate = computed(() => hasAccessByCodes([PERMISSIONS.update]));
const canDelete = computed(() => hasAccessByCodes([PERMISSIONS.delete]));
const canTrigger = computed(() => hasAccessByCodes([PERMISSIONS.trigger]));
const canLogs = computed(() => hasAccessByCodes([PERMISSIONS.logs]));

const loading = ref(false);
const saving = ref(false);
const logLoading = ref(false);
const modalOpen = ref(false);
const logOpen = ref(false);
const editing = ref<OperationsApi.JobDefinition>();
const selectedJob = ref<OperationsApi.JobDefinition>();
const records = ref<OperationsApi.JobDefinition[]>([]);
const logs = ref<OperationsApi.JobExecutionLog[]>([]);

const query = reactive({
  enabled: undefined as 0 | 1 | undefined,
  keyword: '',
});

const form = reactive<OperationsApi.SaveJobParams>({
  cronExpression: '0 */5 * * * *',
  description: '',
  enabled: 0,
  handlerName: 'noop',
  jobCode: '',
  jobName: '',
  jobParams: '',
});

const pagination = reactive({
  current: 1,
  pageSize: 20,
  size: 'small' as const,
  showSizeChanger: true,
  total: 0,
});

const logPagination = reactive({
  current: 1,
  pageSize: 20,
  size: 'small' as const,
  showSizeChanger: true,
  total: 0,
});

const columns = computed<TableProps['columns']>(() => [
  { dataIndex: 'jobName', fixed: 'left', key: 'jobName', title: '任务名称', width: 180 },
  { dataIndex: 'jobCode', key: 'jobCode', title: '编码', width: 160 },
  { dataIndex: 'handlerName', key: 'handlerName', title: '处理器', width: 130 },
  { dataIndex: 'cronExpression', key: 'cronExpression', title: 'Cron', width: 160 },
  { dataIndex: 'enabled', key: 'enabled', title: '状态', width: 90 },
  { dataIndex: 'lastRunAt', key: 'lastRunAt', title: '上次执行', width: 180 },
  { dataIndex: 'nextRunAt', key: 'nextRunAt', title: '下次执行', width: 180 },
  { fixed: 'right', key: 'action', title: '操作', width: 280 },
]);

const logColumns = computed<TableProps['columns']>(() => [
  { dataIndex: 'triggerType', key: 'triggerType', title: '触发方式', width: 100 },
  { dataIndex: 'status', key: 'status', title: '状态', width: 100 },
  { dataIndex: 'startedAt', key: 'startedAt', title: '开始时间', width: 180 },
  { dataIndex: 'durationMs', key: 'durationMs', title: '耗时(ms)', width: 100 },
  { dataIndex: 'resultSummary', key: 'resultSummary', title: '结果', width: 220 },
  { dataIndex: 'errorMessage', key: 'errorMessage', title: '错误', width: 240 },
]);

async function load() {
  if (!canQuery.value) {
    records.value = [];
    pagination.total = 0;
    return;
  }
  loading.value = true;
  try {
    const result = await getJobs({
      enabled: query.enabled,
      keyword: query.keyword || undefined,
      page: pagination.current,
      size: pagination.pageSize,
    });
    records.value = result.records;
    pagination.total = result.total;
  } finally {
    loading.value = false;
  }
}

function resetQuery() {
  query.keyword = '';
  query.enabled = undefined;
  pagination.current = 1;
  load();
}

function openCreate() {
  editing.value = undefined;
  form.jobCode = '';
  form.jobName = '';
  form.handlerName = 'noop';
  form.cronExpression = '0 */5 * * * *';
  form.jobParams = '';
  form.enabled = 0;
  form.description = '';
  modalOpen.value = true;
}

function openEdit(record: OperationsApi.JobDefinition) {
  editing.value = record;
  form.jobCode = record.jobCode;
  form.jobName = record.jobName;
  form.handlerName = record.handlerName;
  form.cronExpression = record.cronExpression;
  form.jobParams = record.jobParams || '';
  form.enabled = record.enabled;
  form.description = record.description || '';
  modalOpen.value = true;
}

async function submit() {
  if (!form.jobCode.trim() || !form.jobName.trim() || !form.handlerName.trim() || !form.cronExpression.trim()) {
    message.warning('请输入任务编码、名称、处理器和 Cron');
    return;
  }
  saving.value = true;
  try {
    const payload = {
      cronExpression: form.cronExpression.trim(),
      description: form.description?.trim() || undefined,
      enabled: form.enabled,
      handlerName: form.handlerName.trim(),
      jobCode: form.jobCode.trim(),
      jobName: form.jobName.trim(),
      jobParams: form.jobParams?.trim() || undefined,
    };
    if (editing.value) {
      await updateJob(editing.value.id, payload);
      message.success('任务已更新');
    } else {
      await createJob(payload);
      message.success('任务已创建');
    }
    modalOpen.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function toggleEnabled(record: OperationsApi.JobDefinition) {
  await updateJobEnabled(record.id, record.enabled === 1 ? 0 : 1);
  message.success(record.enabled === 1 ? '任务已停用' : '任务已启用');
  await load();
}

async function trigger(record: OperationsApi.JobDefinition) {
  const log = await triggerJob(record.id);
  message.success(log.status === 'SUCCESS' ? '任务执行成功' : '任务执行失败');
  await load();
}

async function remove(record: OperationsApi.JobDefinition) {
  await deleteJob(record.id);
  message.success('任务已删除');
  await load();
}

async function openLogs(record: OperationsApi.JobDefinition) {
  selectedJob.value = record;
  logOpen.value = true;
  await loadLogs();
}

async function loadLogs() {
  if (!selectedJob.value) return;
  logLoading.value = true;
  try {
    const result = await getJobLogs(selectedJob.value.id, {
      page: logPagination.current,
      size: logPagination.pageSize,
    });
    logs.value = result.records;
    logPagination.total = result.total;
  } finally {
    logLoading.value = false;
  }
}

function handleTableChange(next: TableProps['pagination']) {
  if (next && typeof next === 'object') {
    pagination.current = next.current ?? 1;
    pagination.pageSize = next.pageSize ?? 20;
    load();
  }
}

function handleLogTableChange(next: TableProps['pagination']) {
  if (next && typeof next === 'object') {
    logPagination.current = next.current ?? 1;
    logPagination.pageSize = next.pageSize ?? 20;
    loadLogs();
  }
}

function statusColor(status?: string) {
  if (status === 'SUCCESS') return 'green';
  if (status === 'FAILED') return 'red';
  return 'blue';
}

function asJob(record: Record<string, any>) {
  return record as OperationsApi.JobDefinition;
}

onMounted(load);
</script>

<template>
  <Page auto-content-height>
    <div class="erp-compact-page ops-page">
      <section class="toolbar">
        <Space wrap>
          <Input v-model:value="query.keyword" class="query-input" placeholder="任务名称/编码" allow-clear />
          <Select
            v-model:value="query.enabled"
            allow-clear
            class="query-select"
            :options="[
              { label: '启用', value: 1 },
              { label: '停用', value: 0 },
            ]"
            placeholder="状态"
          />
          <Button v-if="canQuery" type="primary" @click="load">查询</Button>
          <Button v-if="canQuery" @click="resetQuery">重置</Button>
          <Button v-if="canCreate" type="primary" @click="openCreate">
            <Plus class="size-4" />
            新增任务
          </Button>
        </Space>
      </section>

      <Table
        row-key="id"
        :columns="columns"
        :data-source="records"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 1380 }"
        size="small"
        :sticky="{ offsetScroll: 0 }"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'enabled'">
            <Tag :color="record.enabled === 1 ? 'green' : 'default'">
              {{ record.enabled === 1 ? '启用' : '停用' }}
            </Tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <Space>
              <Button v-if="canUpdate" type="link" size="small" @click="openEdit(asJob(record))">
                编辑
              </Button>
              <Button v-if="canUpdate" type="link" size="small" @click="toggleEnabled(asJob(record))">
                {{ record.enabled === 1 ? '停用' : '启用' }}
              </Button>
              <Button v-if="canTrigger" type="link" size="small" @click="trigger(asJob(record))">
                触发
              </Button>
              <Button v-if="canLogs" type="link" size="small" @click="openLogs(asJob(record))">
                日志
              </Button>
              <Popconfirm v-if="canDelete" title="确认删除该任务？" @confirm="remove(asJob(record))">
                <Button danger type="link" size="small">删除</Button>
              </Popconfirm>
            </Space>
          </template>
        </template>
      </Table>
    </div>

    <Modal
      v-model:open="modalOpen"
      :confirm-loading="saving"
      :title="editing ? '编辑后台任务' : '新增后台任务'"
      ok-text="保存"
      width="720px"
      @ok="submit"
    >
      <div class="form-grid">
        <FormItem label="任务编码" required>
          <Input v-model:value="form.jobCode" :disabled="!!editing" placeholder="例如 CLEAN_EXPIRED_SESSION" />
        </FormItem>
        <FormItem label="任务名称" required>
          <Input v-model:value="form.jobName" placeholder="请输入任务名称" />
        </FormItem>
        <FormItem label="处理器" required>
          <Input v-model:value="form.handlerName" placeholder="默认 noop" />
        </FormItem>
        <FormItem label="Cron" required>
          <Input v-model:value="form.cronExpression" placeholder="例如 0 */5 * * * *" />
        </FormItem>
        <FormItem label="启用状态">
          <Switch
            v-model:checked="form.enabled"
            :checked-value="1"
            :un-checked-value="0"
            checked-children="启用"
            un-checked-children="停用"
          />
        </FormItem>
        <FormItem class="form-wide" label="参数">
          <Textarea v-model:value="form.jobParams" :rows="4" placeholder='例如 {"days":7}' />
        </FormItem>
        <FormItem class="form-wide" label="描述">
          <Textarea v-model:value="form.description" :rows="3" />
        </FormItem>
      </div>
    </Modal>

    <Modal v-model:open="logOpen" :footer="null" :title="`${selectedJob?.jobName || ''} 执行日志`" width="920px">
      <Table
        row-key="id"
        :columns="logColumns"
        :data-source="logs"
        :loading="logLoading"
        :pagination="logPagination"
        :scroll="{ x: 960 }"
        size="small"
        @change="handleLogTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <Tag :color="statusColor(record.status)">{{ record.status }}</Tag>
          </template>
        </template>
      </Table>
    </Modal>
  </Page>
</template>

<style scoped>
.ops-page {
  display: flex;
  min-height: 100%;
  flex-direction: column;
  gap: 8px;
}

.toolbar {
  width: 100%;
}

.query-input {
  width: 220px;
}

.query-select {
  width: 140px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
}

.form-wide {
  grid-column: 1 / -1;
}

@media (max-width: 900px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>

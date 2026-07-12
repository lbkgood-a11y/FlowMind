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
  Table,
  Tag,
} from 'ant-design-vue';

import {
  cancelImportExportTask,
  createExportTask,
  createImportTask,
  getImportExportTaskResult,
  getImportExportTasks,
} from '#/api';

const Textarea = Input.TextArea;

const PERMISSIONS = {
  cancel: '/api/v1/import-export-tasks/*/cancel:POST',
  createExport: '/api/v1/import-export-tasks/export:POST',
  createImport: '/api/v1/import-export-tasks/import:POST',
  query: '/api/v1/import-export-tasks:GET',
  result: '/api/v1/import-export-tasks/*/result:GET',
} as const;

const { hasAccessByCodes } = useAccess();
const canQuery = computed(() => hasAccessByCodes([PERMISSIONS.query]));
const canCreateImport = computed(() => hasAccessByCodes([PERMISSIONS.createImport]));
const canCreateExport = computed(() => hasAccessByCodes([PERMISSIONS.createExport]));
const canCancel = computed(() => hasAccessByCodes([PERMISSIONS.cancel]));
const canResult = computed(() => hasAccessByCodes([PERMISSIONS.result]));

const loading = ref(false);
const saving = ref(false);
const modalOpen = ref(false);
const taskType = ref<'EXPORT' | 'IMPORT'>('IMPORT');
const records = ref<OperationsApi.ImportExportTask[]>([]);

const query = reactive({
  businessType: '',
  status: undefined as string | undefined,
  taskType: undefined as string | undefined,
});

const form = reactive<OperationsApi.CreateTaskParams>({
  businessType: '',
  requestParams: '',
  taskName: '',
});

const pagination = reactive({
  current: 1,
  pageSize: 20,
  size: 'small' as const,
  showSizeChanger: true,
  total: 0,
});

const columns = computed<TableProps['columns']>(() => [
  { dataIndex: 'taskName', fixed: 'left', key: 'taskName', title: '任务名称', width: 220 },
  { dataIndex: 'taskType', key: 'taskType', title: '类型', width: 90 },
  { dataIndex: 'businessType', key: 'businessType', title: '业务类型', width: 140 },
  { dataIndex: 'status', key: 'status', title: '状态', width: 110 },
  { dataIndex: 'progress', key: 'progress', title: '进度', width: 90 },
  { dataIndex: 'successCount', key: 'successCount', title: '成功', width: 90 },
  { dataIndex: 'failureCount', key: 'failureCount', title: '失败', width: 90 },
  { dataIndex: 'createdAt', key: 'createdAt', title: '创建时间', width: 180 },
  { fixed: 'right', key: 'action', title: '操作', width: 180 },
]);

async function load() {
  if (!canQuery.value) {
    records.value = [];
    pagination.total = 0;
    return;
  }
  loading.value = true;
  try {
    const result = await getImportExportTasks({
      businessType: query.businessType || undefined,
      page: pagination.current,
      size: pagination.pageSize,
      status: query.status,
      taskType: query.taskType,
    });
    records.value = result.records;
    pagination.total = result.total;
  } finally {
    loading.value = false;
  }
}

function resetQuery() {
  query.businessType = '';
  query.status = undefined;
  query.taskType = undefined;
  pagination.current = 1;
  load();
}

function openCreate(type: 'EXPORT' | 'IMPORT') {
  taskType.value = type;
  form.businessType = '';
  form.requestParams = '';
  form.taskName = '';
  modalOpen.value = true;
}

async function submit() {
  if (!form.taskName.trim() || !form.businessType.trim()) {
    message.warning('请输入任务名称和业务类型');
    return;
  }
  saving.value = true;
  try {
    const payload = {
      businessType: form.businessType.trim(),
      requestParams: form.requestParams?.trim() || undefined,
      taskName: form.taskName.trim(),
    };
    if (taskType.value === 'IMPORT') {
      await createImportTask(payload);
      message.success('导入任务已创建');
    } else {
      await createExportTask(payload);
      message.success('导出任务已创建');
    }
    modalOpen.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function cancelTask(record: OperationsApi.ImportExportTask) {
  await cancelImportExportTask(record.id);
  message.success('任务已取消');
  await load();
}

async function viewResult(record: OperationsApi.ImportExportTask) {
  const result = await getImportExportTaskResult(record.id);
  Modal.info({
    content: result.failureReason || result.resultFileId || result.failureFileId || '暂无结果文件',
    title: `${result.taskName} 执行结果`,
  });
}

function handleTableChange(next: TableProps['pagination']) {
  if (next && typeof next === 'object') {
    pagination.current = next.current ?? 1;
    pagination.pageSize = next.pageSize ?? 20;
    load();
  }
}

function statusColor(status?: string) {
  if (status === 'SUCCESS') return 'green';
  if (status === 'FAILED') return 'red';
  if (status === 'CANCELED') return 'default';
  return 'blue';
}

function asTask(record: Record<string, any>) {
  return record as OperationsApi.ImportExportTask;
}

onMounted(load);
</script>

<template>
  <Page auto-content-height>
    <div class="erp-compact-page ops-page">
      <section class="toolbar">
        <Space wrap>
          <Input v-model:value="query.businessType" allow-clear class="query-input" placeholder="业务类型" />
          <Select
            v-model:value="query.taskType"
            allow-clear
            class="query-select"
            :options="[
              { label: '导入', value: 'IMPORT' },
              { label: '导出', value: 'EXPORT' },
            ]"
            placeholder="类型"
          />
          <Select
            v-model:value="query.status"
            allow-clear
            class="query-select"
            :options="[
              { label: '等待中', value: 'PENDING' },
              { label: '执行中', value: 'RUNNING' },
              { label: '成功', value: 'SUCCESS' },
              { label: '失败', value: 'FAILED' },
              { label: '已取消', value: 'CANCELED' },
            ]"
            placeholder="状态"
          />
          <Button v-if="canQuery" type="primary" @click="load">查询</Button>
          <Button v-if="canQuery" @click="resetQuery">重置</Button>
          <Button v-if="canCreateImport" type="primary" @click="openCreate('IMPORT')">
            <Plus class="size-4" />
            新建导入
          </Button>
          <Button v-if="canCreateExport" @click="openCreate('EXPORT')">
            <Plus class="size-4" />
            新建导出
          </Button>
        </Space>
      </section>

      <Table
        row-key="id"
        :columns="columns"
        :data-source="records"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 1180 }"
        size="small"
        :sticky="{ offsetScroll: 0 }"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'taskType'">
            <Tag color="blue">{{ record.taskType }}</Tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <Tag :color="statusColor(record.status)">{{ record.status }}</Tag>
          </template>
          <template v-else-if="column.key === 'progress'">
            {{ record.progress ?? 0 }}%
          </template>
          <template v-else-if="column.key === 'action'">
            <Space>
              <Button v-if="canResult" type="link" size="small" @click="viewResult(asTask(record))">
                结果
              </Button>
              <Popconfirm
                v-if="canCancel && ['PENDING', 'RUNNING'].includes(record.status)"
                title="确认取消该任务？"
                @confirm="cancelTask(asTask(record))"
              >
                <Button danger type="link" size="small">取消</Button>
              </Popconfirm>
            </Space>
          </template>
        </template>
      </Table>

      <Modal
        v-model:open="modalOpen"
        :confirm-loading="saving"
        :title="taskType === 'IMPORT' ? '新建导入任务' : '新建导出任务'"
        ok-text="保存"
        width="720px"
        @ok="submit"
      >
        <div class="form-grid">
          <FormItem label="任务名称" required>
            <Input v-model:value="form.taskName" placeholder="请输入任务名称" />
          </FormItem>
          <FormItem label="业务类型" required>
            <Input v-model:value="form.businessType" placeholder="如 USER、ORDER" />
          </FormItem>
          <FormItem class="form-wide" label="请求参数">
            <Textarea v-model:value="form.requestParams" :rows="5" placeholder="JSON 参数，可选" />
          </FormItem>
        </div>
      </Modal>
    </div>
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
  width: 180px;
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

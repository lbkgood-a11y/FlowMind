<script setup lang="ts">
import type { LowcodeApi } from '#/api/lowcode';
import type { TableProps } from 'ant-design-vue';

import { computed, h, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { Page } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import {
  Button,
  Drawer,
  Form,
  message,
  Pagination,
  Space,
  Table,
  Tag,
  Tooltip,
} from 'ant-design-vue';

import {
  getRuntimeApplicationDescriptor,
  getRuntimeApplicationInstance,
  getRuntimeApplicationInstances,
  retryRuntimeApplicationWorkflow,
  runRuntimeApplicationAction,
} from '#/api/lowcode';

import DynamicProcessForm from '../../process/components/DynamicProcessForm.vue';
import {
  createRuntimeDraftKey,
  createRuntimeRetryKey,
  formatRuntimeValue,
  getPrimaryCreateAction,
  getRetryWorkflowAction,
  getRuntimeDetailSections,
  getRuntimeListColumns,
  parseInstanceData,
  toRuntimeTableRecord,
  type RuntimeFieldDescriptor,
  type RuntimeTableRecord,
  workflowStatusTag,
} from './runtime-metadata';

const route = useRoute();
const loading = ref(false);
const descriptorLoading = ref(false);
const detailLoading = ref(false);
const saving = ref(false);
const drawerOpen = ref(false);
const detailOpen = ref(false);
const descriptor = ref<LowcodeApi.RuntimeApplicationDescriptor>();
const records = ref<LowcodeApi.FormInstance[]>([]);
const detailRecord = ref<LowcodeApi.FormInstance>();
const formData = ref<Record<string, unknown>>({});
const draftIdempotencyKey = ref('');
const formRef = ref<InstanceType<typeof DynamicProcessForm>>();

const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
});

const appKey = computed(() => String(route.params.appKey || ''));
const requestedVersion = computed(() => {
  const value = Number(route.query.version);
  return Number.isFinite(value) && value > 0 ? value : undefined;
});
const createAction = computed(() => getPrimaryCreateAction(descriptor.value));
const retryAction = computed(() => getRetryWorkflowAction(descriptor.value));
const tableRecords = computed(() => records.value.map(toRuntimeTableRecord));
const detailData = computed(() => parseInstanceData(detailRecord.value?.dataJson));
const detailSections = computed(() => getRuntimeDetailSections(descriptor.value, 'DETAIL'));

const columns = computed<TableProps['columns']>(() => {
  const metadataColumns = getRuntimeListColumns(descriptor.value).map((column) => ({
    customRender: ({ record }: { record: RuntimeTableRecord }) =>
      formatRuntimeValue(record[column.fieldKey], column),
    dataIndex: column.fieldKey,
    ellipsis: true,
    key: column.fieldKey,
    title: column.label,
    width: column.width || 150,
  }));
  return [
    ...metadataColumns,
    { dataIndex: 'submittedBy', key: 'submittedBy', title: '提交人', width: 140 },
    { align: 'center', dataIndex: 'submittedAt', key: 'submittedAt', title: '提交时间', width: 180 },
    {
      align: 'center',
      key: 'workflowStatus',
      title: '流程状态',
      width: 130,
      customRender: ({ record }: { record: RuntimeTableRecord }) => {
        const tag = workflowStatusTag(record.workflowStatus, record.processInstanceId);
        return h(Tag, { color: tag.color }, () => tag.label);
      },
    },
    { align: 'center', fixed: 'right', key: 'action', title: '操作', width: 190 },
  ];
});

async function loadAll() {
  await loadDescriptor();
  await loadRecords(1);
}

async function loadDescriptor() {
  if (!appKey.value) return;
  descriptorLoading.value = true;
  try {
    descriptor.value = await getRuntimeApplicationDescriptor(appKey.value, {
      version: requestedVersion.value,
    });
  } finally {
    descriptorLoading.value = false;
  }
}

async function loadRecords(page = pagination.current) {
  if (!descriptor.value) {
    records.value = [];
    pagination.total = 0;
    return;
  }
  loading.value = true;
  try {
    const result = await getRuntimeApplicationInstances(appKey.value, {
      page,
      size: pagination.pageSize,
      version: descriptor.value.version,
    });
    records.value = result.items;
    pagination.current = page;
    pagination.total = result.total;
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  const action = createAction.value;
  if (!descriptor.value || !action) {
    message.warning('当前应用未配置创建动作');
    return;
  }
  formData.value = {};
  draftIdempotencyKey.value = createRuntimeDraftKey(
    descriptor.value.appKey,
    descriptor.value.version,
    action.actionCode,
  );
  drawerOpen.value = true;
}

async function submit() {
  const action = createAction.value;
  if (!descriptor.value || !action) return;
  const errors = formRef.value?.validate() ?? [];
  if (errors.length > 0) {
    message.warning(errors[0]?.message ?? '请完善表单');
    return;
  }
  saving.value = true;
  try {
    const response = await runRuntimeApplicationAction(
      descriptor.value.appKey,
      action.actionCode,
      {
        data: formData.value,
        idempotencyKey: draftIdempotencyKey.value,
        title: buildTitle(formData.value),
      },
      { version: descriptor.value.version },
    );
    if (response.status === 'WORKFLOW_PENDING') {
      message.warning('表单已保存，流程启动待重试');
    } else if (response.status === 'WORKFLOW_STARTED') {
      message.success('表单已提交并启动流程');
    } else {
      message.success('表单已保存');
    }
    drawerOpen.value = false;
    await loadRecords(1);
  } finally {
    saving.value = false;
  }
}

async function openDetail(record: RuntimeTableRecord) {
  if (!descriptor.value) return;
  detailOpen.value = true;
  detailLoading.value = true;
  try {
    detailRecord.value = await getRuntimeApplicationInstance(
      descriptor.value.appKey,
      record.id,
      { version: descriptor.value.version },
    );
  } finally {
    detailLoading.value = false;
  }
}

async function retryWorkflow(record: RuntimeTableRecord) {
  const action = retryAction.value;
  if (!descriptor.value || !action) {
    message.warning('当前应用未配置流程重试动作');
    return;
  }
  await retryRuntimeApplicationWorkflow(
    descriptor.value.appKey,
    record.id,
    {
      actionCode: action.actionCode,
      idempotencyKey: createRuntimeRetryKey(
        descriptor.value.appKey,
        descriptor.value.version,
        record.id,
        action.actionCode,
      ),
    },
    { version: descriptor.value.version },
  );
  message.success('流程重试已提交');
  await loadRecords();
}

function buildTitle(data: Record<string, unknown>) {
  const firstText = Object.values(data).find(
    (value) => typeof value === 'string' && value.trim(),
  );
  return `${descriptor.value?.name || appKey.value}-${String(firstText || Date.now())}`;
}

function formatDetailValue(field: RuntimeFieldDescriptor) {
  return formatRuntimeValue(detailData.value[field.fieldKey], field);
}

function onPageChange(page: number, pageSize: number) {
  pagination.pageSize = pageSize;
  loadRecords(page);
}

watch(
  () => [route.params.appKey, route.query.version],
  () => {
    records.value = [];
    detailRecord.value = undefined;
    void loadAll();
  },
  { immediate: true },
);
</script>

<template>
  <Page auto-content-height>
    <div class="runtime-page">
      <section class="list-panel">
        <div class="list-header">
          <div>
            <h2>{{ descriptor?.name || appKey }}</h2>
            <p>
              {{ descriptor?.description || '发布版快速应用运行台' }}
              <span v-if="descriptor"> · v{{ descriptor.version }}</span>
            </p>
          </div>
          <Space :size="8">
            <Button v-if="createAction" type="primary" @click="openCreate">
              <Plus class="size-4" />
              {{ createAction.label || '新建' }}
            </Button>
            <Tooltip title="刷新">
              <Button shape="circle" @click="loadRecords()">
                <span class="text-lg">↻</span>
              </Button>
            </Tooltip>
          </Space>
        </div>

        <div class="table-frame">
          <Table
            :columns="columns"
            :data-source="tableRecords"
            :loading="loading || descriptorLoading"
            :pagination="false"
            :scroll="{ x: 1120 }"
            bordered
            row-key="id"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'action'">
                <Space>
                  <Button
                    size="small"
                    type="link"
                    @click="openDetail(record as RuntimeTableRecord)"
                  >
                    详情
                  </Button>
                  <Button
                    v-if="record.workflowStatus === 'PENDING_WORKFLOW' && retryAction"
                    size="small"
                    type="link"
                    @click="retryWorkflow(record as RuntimeTableRecord)"
                  >
                    重试流程
                  </Button>
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
            size="small"
            @change="onPageChange"
            @show-size-change="onPageChange"
          />
        </div>
      </section>
    </div>

    <Drawer
      v-model:open="drawerOpen"
      :title="createAction?.label || '新建'"
      destroy-on-close
      placement="right"
      width="640"
    >
      <Form layout="vertical">
        <DynamicProcessForm
          ref="formRef"
          v-model="formData"
          :schema-json="descriptor?.schemaJson"
          :ui-schema-json="descriptor?.uiSchemaJson"
        />
      </Form>

      <template #footer>
        <Space>
          <Button @click="drawerOpen = false">取消</Button>
          <Button :loading="saving" type="primary" @click="submit">提交</Button>
        </Space>
      </template>
    </Drawer>

    <Drawer
      v-model:open="detailOpen"
      :loading="detailLoading"
      destroy-on-close
      placement="right"
      title="业务详情"
      width="640"
    >
      <div v-for="section in detailSections" :key="section.title" class="detail-section">
        <h3>{{ section.title }}</h3>
        <div class="detail-grid">
          <div v-for="field in section.fields" :key="field.fieldKey" class="detail-field">
            <dt>{{ field.label }}</dt>
            <dd>{{ formatDetailValue(field) }}</dd>
          </div>
        </div>
      </div>
      <div class="detail-section">
        <h3>流程信息</h3>
        <div class="detail-grid">
          <div class="detail-field">
            <dt>流程状态</dt>
            <dd>
              <Tag
                :color="
                  workflowStatusTag(detailRecord?.workflowStatus, detailRecord?.processInstanceId)
                    .color
                "
              >
                {{
                  workflowStatusTag(detailRecord?.workflowStatus, detailRecord?.processInstanceId)
                    .label
                }}
              </Tag>
            </dd>
          </div>
          <div class="detail-field">
            <dt>流程实例</dt>
            <dd>{{ detailRecord?.processInstanceId || '-' }}</dd>
          </div>
        </div>
      </div>
    </Drawer>
  </Page>
</template>

<style scoped>
.runtime-page,
.list-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.list-panel {
  min-height: calc(100vh - 120px);
  padding: 8px;
  background: #fff;
  border-radius: 4px;
}

.list-header {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 8px;
}

.list-header h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #111827;
}

.list-header p {
  margin: 2px 0 0;
  font-size: 12px;
  color: #64748b;
}

.table-frame {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
}

.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 10px;
}

.table-total {
  font-size: 13px;
  color: #606b7b;
}

.detail-section {
  margin-bottom: 16px;
}

.detail-section h3 {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.detail-field {
  min-width: 0;
  padding: 8px;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
}

.detail-field dt {
  margin-bottom: 4px;
  font-size: 12px;
  color: #64748b;
}

.detail-field dd {
  min-height: 20px;
  margin: 0;
  overflow-wrap: anywhere;
  font-size: 13px;
  color: #111827;
}
</style>

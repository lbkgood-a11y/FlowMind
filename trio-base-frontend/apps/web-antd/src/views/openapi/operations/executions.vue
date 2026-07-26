<script setup lang="ts">
import type { OpenApiOperationsApi } from '#/api';
import type { TableProps } from 'ant-design-vue';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';

import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  DescriptionsItem,
  Drawer,
  FormItem,
  Input,
  InputNumber,
  message,
  Modal,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
} from 'ant-design-vue';

import {
  ACTION_TARGET_TYPES,
  ACTION_TYPES,
  captureOpenApiExecutionDiagnostic,
  createActionIdempotencyKey,
  getOpenApiExecutionDetail,
  getOpenApiExecutions,
} from '#/api';
import { useActionDispatch } from '#/composables/useActionDispatch';
import {
  BusinessActionButton,
  BusinessPageScaffold,
  CompactQueryBar,
  CompactTableFrame,
  CompactToolbar,
  MultiTableLayout,
  refreshByScopes,
} from '#/shared';

const Textarea = Input.TextArea;
const PERMISSIONS = {
  diagnosticsWrite: '/api/v1/openapi/management/executions/diagnostics:POST',
  executionRead: '/api/v1/openapi/management/executions:GET',
} as const;

const executionStates = [
  'ACCEPTED',
  'RUNNING',
  'WAITING_CALLBACK',
  'SUCCEEDED',
  'FAILED',
  'COMPENSATED',
  'CANCELLED',
] as const;
const environments = ['DEV', 'TEST', 'PROD'] as const;

const { hasAccessByCodes } = useAccess();
const { dispatchAction } = useActionDispatch();
const canReadExecutions = computed(() => hasAccessByCodes([PERMISSIONS.executionRead]));
const canCaptureDiagnostics = computed(() =>
  hasAccessByCodes([PERMISSIONS.diagnosticsWrite]),
);

const executionLoading = ref(false);
const detailLoading = ref(false);
const detailOpen = ref(false);
const diagnosticOpen = ref(false);
const diagnosticSaving = ref(false);
const startOpen = ref(false);
const starting = ref(false);
const executions = ref<OpenApiOperationsApi.Execution[]>([]);
const detail = ref<OpenApiOperationsApi.ExecutionDetail>();
const selectedExecution = ref<OpenApiOperationsApi.Execution>();
const startPayloadJson = ref('{}');
const diagnosticRequestJson = ref('{}');
const diagnosticResponseJson = ref('{}');
const diagnosticRedactionJson = ref('{}');

const executionQuery = reactive({
  applicationClientId: '',
  environment: undefined as string | undefined,
  routeDefinitionId: '',
  state: undefined as string | undefined,
  traceId: '',
});
const startForm = reactive({
  applicationClientId: '',
  environment: 'DEV',
  idempotencyKey: '',
  maxActiveWorkflows: undefined as number | undefined,
  maxConcurrency: undefined as number | undefined,
  operation: 'POST',
  policyVersion: undefined as number | undefined,
  routeKey: '',
  subscriptionId: '',
  tenantId: '',
});
const executionPagination = reactive({
  current: 1,
  pageSize: 20,
  showSizeChanger: true,
  size: 'small' as const,
  total: 0,
});

const executionStats = computed(() => {
  const pageRecords = executions.value;
  return {
    failed: pageRecords.filter((item) => item.executionState === 'FAILED').length,
    running: pageRecords.filter((item) => item.executionState === 'RUNNING').length,
    total: executionPagination.total,
    waiting: pageRecords.filter((item) => item.executionState === 'WAITING_CALLBACK').length,
  };
});
const detailExecution = computed(() => detail.value?.execution ?? selectedExecution.value);

const executionColumns: TableProps['columns'] = [
  { dataIndex: 'id', fixed: 'left', key: 'id', title: '执行 ID', width: 220 },
  { dataIndex: 'executionState', key: 'state', title: '状态', width: 140 },
  { dataIndex: 'executionMode', key: 'mode', title: '模式', width: 130 },
  { dataIndex: 'routeDefinitionId', key: 'route', title: '路由定义', width: 220 },
  { dataIndex: 'applicationClientId', key: 'client', title: '应用客户端', width: 180 },
  { dataIndex: 'environment', key: 'environment', title: '环境', width: 90 },
  { dataIndex: 'traceId', key: 'trace', title: 'TraceId', width: 220 },
  { dataIndex: 'durationMillis', key: 'duration', title: '耗时', width: 110 },
  { dataIndex: 'startedAt', key: 'startedAt', title: '开始时间', width: 180 },
  { dataIndex: 'retentionUntil', key: 'retention', title: '保留至', width: 180 },
  { fixed: 'right', key: 'action', title: '操作', width: 100 },
];
const attemptColumns: TableProps['columns'] = [
  { dataIndex: 'stepKey', fixed: 'left', key: 'stepKey', title: '步骤', width: 180 },
  { dataIndex: 'stepType', key: 'stepType', title: '类型', width: 130 },
  { dataIndex: 'attemptNumber', key: 'attemptNumber', title: '次数', width: 80 },
  { dataIndex: 'attemptState', key: 'attemptState', title: '状态', width: 130 },
  { dataIndex: 'externalStatus', key: 'externalStatus', title: '外部状态', width: 100 },
  { dataIndex: 'durationMillis', key: 'duration', title: '耗时', width: 110 },
  { dataIndex: 'actionTraceId', key: 'actionTraceId', title: 'Action TraceId', width: 220 },
  { dataIndex: 'errorCode', key: 'errorCode', title: '错误码', width: 150 },
  { dataIndex: 'startedAt', key: 'startedAt', title: '开始时间', width: 180 },
];

async function loadExecutions(resetPage = false) {
  if (!canReadExecutions.value) return;
  if (resetPage) executionPagination.current = 1;
  executionLoading.value = true;
  try {
    const result = await getOpenApiExecutions({
      applicationClientId: executionQuery.applicationClientId || undefined,
      environment: executionQuery.environment,
      page: executionPagination.current,
      routeDefinitionId: executionQuery.routeDefinitionId || undefined,
      size: executionPagination.pageSize,
      state: executionQuery.state,
      traceId: executionQuery.traceId || undefined,
    });
    executions.value = result.records;
    executionPagination.total = result.total;
  } finally {
    executionLoading.value = false;
  }
}

function resetQuery() {
  executionQuery.applicationClientId = '';
  executionQuery.environment = undefined;
  executionQuery.routeDefinitionId = '';
  executionQuery.state = undefined;
  executionQuery.traceId = '';
  loadExecutions(true);
}

async function openDetail(record: OpenApiOperationsApi.Execution) {
  selectedExecution.value = record;
  detail.value = undefined;
  detailOpen.value = true;
  detailLoading.value = true;
  try {
    detail.value = await getOpenApiExecutionDetail(record.id);
  } finally {
    detailLoading.value = false;
  }
}

function openDiagnosticDialog() {
  if (!detailExecution.value) return;
  diagnosticRequestJson.value = '{}';
  diagnosticResponseJson.value = '{}';
  diagnosticRedactionJson.value = '{}';
  diagnosticOpen.value = true;
}

async function submitDiagnostic() {
  if (!detailExecution.value) return;
  let requestPayload: Record<string, unknown>;
  let responsePayload: Record<string, unknown>;
  let redactionPolicy: Record<string, unknown>;
  try {
    requestPayload = parseJsonObject(diagnosticRequestJson.value, '请求 Payload');
    responsePayload = parseJsonObject(diagnosticResponseJson.value, '响应 Payload');
    redactionPolicy = parseJsonObject(diagnosticRedactionJson.value, '脱敏策略');
  } catch (error) {
    message.error(error instanceof Error ? error.message : '诊断内容必须是合法 JSON 对象');
    return;
  }
  diagnosticSaving.value = true;
  try {
    const result = await captureOpenApiExecutionDiagnostic(detailExecution.value.id, {
      redactionPolicy,
      requestPayload,
      responsePayload,
    });
    message.success(`诊断快照已保存，有效期至 ${result.expiresAt}`);
    diagnosticOpen.value = false;
  } finally {
    diagnosticSaving.value = false;
  }
}

function openStartDialog() {
  startForm.applicationClientId = '';
  startForm.environment = 'DEV';
  startForm.idempotencyKey = createActionIdempotencyKey(
    ACTION_TYPES.integrationOrchestrationStart,
    'executions',
  );
  startForm.maxActiveWorkflows = undefined;
  startForm.maxConcurrency = undefined;
  startForm.operation = 'POST';
  startForm.policyVersion = undefined;
  startForm.routeKey = '';
  startForm.subscriptionId = '';
  startForm.tenantId = '';
  startPayloadJson.value = '{}';
  startOpen.value = true;
}

async function submitStart() {
  if (!startForm.routeKey.trim()) {
    message.warning('请填写路由标识');
    return;
  }
  let payload: Record<string, unknown>;
  try {
    payload = parseJsonObject(startPayloadJson.value, '请求 Payload');
  } catch (error) {
    message.error(error instanceof Error ? error.message : '请求 Payload 必须是合法 JSON 对象');
    return;
  }

  const routeKey = startForm.routeKey.trim();
  const tenantId = startForm.tenantId.trim() || undefined;
  const idempotencyKey =
    startForm.idempotencyKey.trim() ||
    createActionIdempotencyKey(ACTION_TYPES.integrationOrchestrationStart, routeKey);
  starting.value = true;
  try {
    await dispatchAction<{ orchestration: OpenApiOperationsApi.OrchestrationExecution }>(
      {
        actionType: ACTION_TYPES.integrationOrchestrationStart,
        executionMode: 'WORKFLOW',
        idempotencyKey,
        payload: {
          admission: {
            applicationClientId: startForm.applicationClientId.trim() || undefined,
            environment: startForm.environment,
            maxActiveWorkflows: startForm.maxActiveWorkflows,
            maxConcurrency: startForm.maxConcurrency,
            policyVersion: startForm.policyVersion,
            subscriptionId: startForm.subscriptionId.trim() || undefined,
            tenantId,
          },
          environment: startForm.environment,
          idempotencyKey,
          operation: startForm.operation.trim() || 'POST',
          payload,
          routeKey,
        },
        source: 'GUI',
        target: {
          id: routeKey,
          ownerService: 'service-openapi',
          tenantId,
          type: ACTION_TARGET_TYPES.integrationRoute,
        },
      },
      {
        onSuccess: async (result) => {
          await refreshByScopes(result, {
            actions: () => loadExecutions(true),
            document: () => loadExecutions(true),
            list: () => loadExecutions(true),
            timeline: () => loadExecutions(true),
            workflow: () => loadExecutions(true),
          });
        },
        successMessage: '编排启动已提交',
      },
    );
    startOpen.value = false;
  } finally {
    starting.value = false;
  }
}

function parseJsonObject(source: string, label: string) {
  try {
    const parsed = JSON.parse(source.trim() || '{}');
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      throw new Error();
    }
    return parsed as Record<string, unknown>;
  } catch {
    throw new Error(`${label} 必须是合法 JSON 对象`);
  }
}

function stateColor(state?: string) {
  if (state === 'SUCCEEDED') return 'green';
  if (state === 'FAILED' || state === 'CANCELLED') return 'red';
  if (state === 'WAITING_CALLBACK' || state === 'COMPENSATED') return 'orange';
  if (state === 'RUNNING') return 'blue';
  return 'default';
}

function formatDuration(value?: number) {
  if (value === undefined || value === null) return '-';
  return `${value} ms`;
}

function formatJson(value?: Record<string, unknown>) {
  return JSON.stringify(value ?? {}, null, 2);
}

function asExecution(record: unknown) {
  return record as OpenApiOperationsApi.Execution;
}

function asAttempt(record: unknown) {
  return record as OpenApiOperationsApi.ExecutionStepAttempt;
}

function handleExecutionTableChange(next: TableProps['pagination']) {
  if (next && typeof next === 'object') {
    executionPagination.current = next.current ?? 1;
    executionPagination.pageSize = next.pageSize ?? 20;
    loadExecutions();
  }
}

onMounted(() => {
  loadExecutions();
});
</script>

<template>
  <Page
    auto-content-height
    description="运行态编排查询、失败定位、诊断快照与手动启动入口"
    title="OpenAPI 执行中心"
  >
    <BusinessPageScaffold pattern="multi-table">
      <template #query>
        <CompactQueryBar :columns="4">
          <FormItem label="应用客户端">
            <Input
              v-model:value="executionQuery.applicationClientId"
              allow-clear
              placeholder="applicationClientId"
            />
          </FormItem>
          <FormItem label="路由定义">
            <Input
              v-model:value="executionQuery.routeDefinitionId"
              allow-clear
              placeholder="routeDefinitionId"
            />
          </FormItem>
          <FormItem label="TraceId">
            <Input v-model:value="executionQuery.traceId" allow-clear placeholder="traceId" />
          </FormItem>
          <FormItem label="环境">
            <Select
              v-model:value="executionQuery.environment"
              allow-clear
              placeholder="全部"
              :options="environments.map((value) => ({ label: value, value }))"
            />
          </FormItem>
          <FormItem label="状态">
            <Select
              v-model:value="executionQuery.state"
              allow-clear
              placeholder="全部"
              :options="executionStates.map((value) => ({ label: value, value }))"
            />
          </FormItem>
          <template #actions>
            <Button @click="resetQuery">重置</Button>
            <Button type="primary" @click="loadExecutions(true)">查询</Button>
          </template>
        </CompactQueryBar>
      </template>

      <template #toolbar>
        <CompactToolbar title="执行列表" subtitle="按执行记录进入步骤尝试与诊断采集">
          <BusinessActionButton
            v-if="canReadExecutions"
            label="启动编排"
            primary
            @execute="openStartDialog"
          />
        </CompactToolbar>
      </template>

      <Alert
        v-if="!canReadExecutions"
        show-icon
        type="warning"
        message="当前账号缺少 OpenAPI 执行查询权限"
      />

      <MultiTableLayout v-else :columns="3">
        <Card size="small">
          <Statistic title="执行总数" :value="executionStats.total" />
        </Card>
        <Card size="small">
          <Statistic title="本页运行中" :value="executionStats.running" />
        </Card>
        <Card size="small">
          <Statistic title="本页待回调" :value="executionStats.waiting" />
        </Card>
      </MultiTableLayout>

      <CompactTableFrame v-if="canReadExecutions">
        <Table
          row-key="id"
          size="small"
          :columns="executionColumns"
          :data-source="executions"
          :loading="executionLoading"
          :pagination="executionPagination"
          :scroll="{ x: 1660 }"
          @change="handleExecutionTableChange"
        >
          <template #bodyCell="{ column, record }">
            <Tag v-if="column.key === 'state'" :color="stateColor(record.executionState)">
              {{ record.executionState }}
            </Tag>
            <span v-else-if="column.key === 'duration'">
              {{ formatDuration(record.durationMillis) }}
            </span>
            <Button
              v-else-if="column.key === 'action'"
              size="small"
              type="link"
              @click="openDetail(asExecution(record))"
            >
              详情
            </Button>
          </template>
        </Table>
      </CompactTableFrame>
    </BusinessPageScaffold>

    <Drawer
      v-model:open="detailOpen"
      :footer="null"
      placement="right"
      title="执行详情"
      width="920"
    >
      <Space direction="vertical" size="middle" class="detail-stack">
        <Descriptions v-if="detailExecution" bordered :column="2" size="small">
          <DescriptionsItem label="执行 ID">{{ detailExecution.id }}</DescriptionsItem>
          <DescriptionsItem label="状态">
            <Tag :color="stateColor(detailExecution.executionState)">
              {{ detailExecution.executionState }}
            </Tag>
          </DescriptionsItem>
          <DescriptionsItem label="租户">{{ detailExecution.tenantId }}</DescriptionsItem>
          <DescriptionsItem label="环境">{{ detailExecution.environment }}</DescriptionsItem>
          <DescriptionsItem label="路由定义">{{ detailExecution.routeDefinitionId }}</DescriptionsItem>
          <DescriptionsItem label="应用客户端">
            {{ detailExecution.applicationClientId || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="Workflow">{{ detailExecution.workflowId || '-' }}</DescriptionsItem>
          <DescriptionsItem label="TraceId">{{ detailExecution.traceId || '-' }}</DescriptionsItem>
          <DescriptionsItem label="开始时间">{{ detailExecution.startedAt }}</DescriptionsItem>
          <DescriptionsItem label="完成时间">{{ detailExecution.completedAt || '-' }}</DescriptionsItem>
          <DescriptionsItem label="耗时">
            {{ formatDuration(detailExecution.durationMillis) }}
          </DescriptionsItem>
          <DescriptionsItem label="保留至">{{ detailExecution.retentionUntil }}</DescriptionsItem>
          <DescriptionsItem v-if="detailExecution.errorCode" label="错误码">
            {{ detailExecution.errorCode }}
          </DescriptionsItem>
          <DescriptionsItem v-if="detailExecution.sanitizedError" label="错误">
            {{ detailExecution.sanitizedError }}
          </DescriptionsItem>
        </Descriptions>

        <CompactToolbar title="步骤尝试" subtitle="外部调用与 Action 关联证据">
          <Button
            v-if="canCaptureDiagnostics"
            size="small"
            type="primary"
            @click="openDiagnosticDialog"
          >
            采集诊断
          </Button>
        </CompactToolbar>

        <Table
          row-key="id"
          size="small"
          :columns="attemptColumns"
          :data-source="detail?.attempts ?? []"
          :loading="detailLoading"
          :pagination="false"
          :scroll="{ x: 1320 }"
        >
          <template #bodyCell="{ column, record }">
            <Tag v-if="column.key === 'attemptState'" :color="stateColor(record.attemptState)">
              {{ record.attemptState || '-' }}
            </Tag>
            <span v-else-if="column.key === 'duration'">
              {{ formatDuration(record.durationMillis) }}
            </span>
          </template>
          <template #expandedRowRender="{ record }">
            <pre class="json-block">{{ formatJson(asAttempt(record).evidence) }}</pre>
          </template>
        </Table>
      </Space>
    </Drawer>

    <Modal
      v-model:open="diagnosticOpen"
      title="采集执行诊断"
      :confirm-loading="diagnosticSaving"
      width="760px"
      @ok="submitDiagnostic"
    >
      <FormItem label="请求 Payload">
        <Textarea v-model:value="diagnosticRequestJson" :rows="6" />
      </FormItem>
      <FormItem label="响应 Payload">
        <Textarea v-model:value="diagnosticResponseJson" :rows="6" />
      </FormItem>
      <FormItem label="脱敏策略">
        <Textarea v-model:value="diagnosticRedactionJson" :rows="4" />
      </FormItem>
    </Modal>

    <Modal
      v-model:open="startOpen"
      title="启动编排"
      :confirm-loading="starting"
      width="720px"
      @ok="submitStart"
    >
      <Row :gutter="12">
        <Col :span="12">
          <FormItem label="路由标识" required>
            <Input v-model:value="startForm.routeKey" placeholder="routeKey" />
          </FormItem>
        </Col>
        <Col :span="12">
          <FormItem label="环境" required>
            <Select
              v-model:value="startForm.environment"
              :options="environments.map((value) => ({ label: value, value }))"
            />
          </FormItem>
        </Col>
        <Col :span="12">
          <FormItem label="操作">
            <Input v-model:value="startForm.operation" placeholder="POST" />
          </FormItem>
        </Col>
        <Col :span="12">
          <FormItem label="幂等键">
            <Input v-model:value="startForm.idempotencyKey" />
          </FormItem>
        </Col>
        <Col :span="12">
          <FormItem label="租户 ID">
            <Input v-model:value="startForm.tenantId" />
          </FormItem>
        </Col>
        <Col :span="12">
          <FormItem label="应用客户端">
            <Input v-model:value="startForm.applicationClientId" />
          </FormItem>
        </Col>
        <Col :span="12">
          <FormItem label="订阅 ID">
            <Input v-model:value="startForm.subscriptionId" />
          </FormItem>
        </Col>
        <Col :span="12">
          <FormItem label="策略版本">
            <InputNumber v-model:value="startForm.policyVersion" class="number-input" />
          </FormItem>
        </Col>
        <Col :span="12">
          <FormItem label="并发上限">
            <InputNumber v-model:value="startForm.maxConcurrency" class="number-input" />
          </FormItem>
        </Col>
        <Col :span="12">
          <FormItem label="活跃工作流上限">
            <InputNumber v-model:value="startForm.maxActiveWorkflows" class="number-input" />
          </FormItem>
        </Col>
        <Col :span="24">
          <FormItem label="请求 Payload">
            <Textarea v-model:value="startPayloadJson" :rows="8" />
          </FormItem>
        </Col>
      </Row>
    </Modal>
  </Page>
</template>

<style scoped>
.detail-stack {
  width: 100%;
}

.json-block {
  max-height: 280px;
  margin: 0;
  overflow: auto;
  padding: 12px;
  border: 1px solid rgb(229 231 235);
  border-radius: 6px;
  background: rgb(248 250 252);
  color: rgb(51 65 85);
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.number-input {
  width: 100%;
}
</style>

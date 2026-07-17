<script setup lang="ts">
import type { OpenApiOperationsApi } from '#/api';
import type { TableProps } from 'ant-design-vue';

import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';

import {
  Button,
  Card,
  Col,
  FormItem,
  Input,
  message,
  Modal,
  Row,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
} from 'ant-design-vue';

import {
  getCallbackQuarantine,
  getOpenApiExecutions,
  resolveCallbackQuarantine,
} from '#/api';

const TabPane = Tabs.TabPane;
const Textarea = Input.TextArea;
const PERMISSIONS = {
  executionRead: '/api/v1/openapi/management/executions:GET',
  quarantineRead: '/api/v1/openapi/management/callback-quarantine:GET',
  quarantineWrite: '/api/v1/openapi/management/callback-quarantine:POST',
} as const;

const assetGroups = [
  ['标准结构', '结构注册、版本、兼容性与 OpenAPI 导入导出'],
  ['字段映射', '映射规则、值映射、预览与契约测试'],
  ['路由发布', '路由条件、发布快照、激活与回滚'],
  ['流程编排', 'Temporal DSL、等待、补偿与执行策略'],
  ['回调配置', '验签、关联、去重、应答与 Signal'],
  ['API 产品', '产品版本、可见性、范围与默认策略'],
  ['接入应用', '环境客户端、凭证轮换与安全状态'],
  ['订阅审批', '产品订阅、双重审批与版本升级'],
  ['流控策略', '限流、配额、并发、策略快照与漂移'],
] as const;

const { hasAccessByCodes } = useAccess();
const route = useRoute();
const canReadExecutions = computed(() => hasAccessByCodes([PERMISSIONS.executionRead]));
const canReadQuarantine = computed(() => hasAccessByCodes([PERMISSIONS.quarantineRead]));
const canResolveQuarantine = computed(() => hasAccessByCodes([PERMISSIONS.quarantineWrite]));
const activeTab = ref('assets');
function syncRouteTab() {
  if (route.path.endsWith('/executions')) activeTab.value = 'executions';
  else if (route.path.endsWith('/quarantine')) activeTab.value = 'quarantine';
}
watch(() => route.path, syncRouteTab, { immediate: true });
const executionLoading = ref(false);
const quarantineLoading = ref(false);
const executions = ref<OpenApiOperationsApi.Execution[]>([]);
const quarantine = ref<OpenApiOperationsApi.CallbackInbox[]>([]);
const resolutionOpen = ref(false);
const resolving = ref(false);
const selectedInbox = ref<OpenApiOperationsApi.CallbackInbox>();

const executionQuery = reactive({
  applicationClientId: '',
  environment: undefined as string | undefined,
  state: undefined as string | undefined,
  traceId: '',
});
const executionPagination = reactive({
  current: 1,
  pageSize: 20,
  showSizeChanger: true,
  size: 'small' as const,
  total: 0,
});
const resolution = reactive({ action: 'LINK' as 'DISCARD' | 'LINK' | 'RETRY', executionId: '', note: '' });

const executionColumns: TableProps['columns'] = [
  { dataIndex: 'id', fixed: 'left', key: 'id', title: '执行 ID', width: 220 },
  { dataIndex: 'executionState', key: 'state', title: '状态', width: 130 },
  { dataIndex: 'executionMode', key: 'mode', title: '模式', width: 130 },
  { dataIndex: 'applicationClientId', key: 'client', title: '应用客户端', width: 180 },
  { dataIndex: 'environment', key: 'environment', title: '环境', width: 90 },
  { dataIndex: 'traceId', key: 'trace', title: 'TraceId', width: 220 },
  { dataIndex: 'durationMillis', key: 'duration', title: '耗时(ms)', width: 110 },
  { dataIndex: 'startedAt', key: 'startedAt', title: '开始时间', width: 180 },
  { dataIndex: 'retentionUntil', key: 'retention', title: '保留至', width: 180 },
];
const quarantineColumns: TableProps['columns'] = [
  { dataIndex: 'id', fixed: 'left', key: 'id', title: '收件箱 ID', width: 220 },
  { dataIndex: 'partnerEventId', key: 'event', title: '伙伴事件 ID', width: 180 },
  { dataIndex: 'correlationValue', key: 'correlation', title: '关联值', width: 200 },
  { dataIndex: 'quarantineReason', key: 'reason', title: '隔离原因', width: 200 },
  { dataIndex: 'applicationClientId', key: 'client', title: '应用客户端', width: 180 },
  { dataIndex: 'receivedAt', key: 'receivedAt', title: '接收时间', width: 180 },
  { fixed: 'right', key: 'action', title: '操作', width: 100 },
];

async function loadExecutions() {
  if (!canReadExecutions.value) return;
  executionLoading.value = true;
  try {
    const result = await getOpenApiExecutions({
      applicationClientId: executionQuery.applicationClientId || undefined,
      environment: executionQuery.environment,
      page: executionPagination.current,
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

async function loadQuarantine() {
  if (!canReadQuarantine.value) return;
  quarantineLoading.value = true;
  try {
    quarantine.value = await getCallbackQuarantine({ limit: 100 });
  } finally {
    quarantineLoading.value = false;
  }
}

function openResolution(record: OpenApiOperationsApi.CallbackInbox) {
  selectedInbox.value = record;
  resolution.action = 'LINK';
  resolution.executionId = record.executionId || '';
  resolution.note = '';
  resolutionOpen.value = true;
}

async function submitResolution() {
  if (!selectedInbox.value || !resolution.note.trim()) {
    message.warning('请填写处理说明');
    return;
  }
  resolving.value = true;
  try {
    await resolveCallbackQuarantine(selectedInbox.value.id, {
      action: resolution.action,
      executionId: resolution.executionId || undefined,
      note: resolution.note.trim(),
    });
    message.success('隔离回调已处理');
    resolutionOpen.value = false;
    await loadQuarantine();
  } finally {
    resolving.value = false;
  }
}

function stateColor(state?: string) {
  if (state === 'SUCCEEDED' || state === 'SIGNALLED') return 'green';
  if (state === 'FAILED' || state === 'QUARANTINED') return 'red';
  if (state === 'WAITING_CALLBACK') return 'orange';
  return 'blue';
}

function asInbox(record: Record<string, any>) {
  return record as OpenApiOperationsApi.CallbackInbox;
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
  loadQuarantine();
});
</script>

<template>
  <Page auto-content-height>
    <Tabs v-model:active-key="activeTab" size="small">
      <TabPane key="assets" tab="资产治理">
        <Row :gutter="[12, 12]">
          <Col v-for="asset in assetGroups" :key="asset[0]" :xs="24" :md="12" :xl="8">
            <Card size="small" :title="asset[0]">
              <p class="asset-description">{{ asset[1] }}</p>
              <Tag color="blue">管理 API 已接入</Tag>
            </Card>
          </Col>
        </Row>
      </TabPane>

      <TabPane key="executions" tab="执行中心">
        <Space class="toolbar" wrap>
          <Input v-model:value="executionQuery.applicationClientId" allow-clear placeholder="应用客户端 ID" />
          <Input v-model:value="executionQuery.traceId" allow-clear placeholder="TraceId" />
          <Select v-model:value="executionQuery.environment" allow-clear placeholder="环境"
                  :options="['DEV','TEST','PROD'].map((value) => ({ label: value, value }))" />
          <Select v-model:value="executionQuery.state" allow-clear placeholder="状态"
                  :options="['ACCEPTED','RUNNING','WAITING_CALLBACK','SUCCEEDED','FAILED','COMPENSATED','CANCELLED'].map((value) => ({ label: value, value }))" />
          <Button type="primary" @click="loadExecutions">查询</Button>
        </Space>
        <Table row-key="id" size="small" :columns="executionColumns" :data-source="executions"
               :loading="executionLoading" :pagination="executionPagination" :scroll="{ x: 1500 }"
               @change="handleExecutionTableChange">
          <template #bodyCell="{ column, record }">
            <Tag v-if="column.key === 'state'" :color="stateColor(record.executionState)">
              {{ record.executionState }}
            </Tag>
          </template>
        </Table>
      </TabPane>

      <TabPane key="quarantine" tab="回调隔离区">
        <Space class="toolbar"><Button type="primary" @click="loadQuarantine">刷新</Button></Space>
        <Table row-key="id" size="small" :columns="quarantineColumns" :data-source="quarantine"
               :loading="quarantineLoading" :pagination="false" :scroll="{ x: 1200 }">
          <template #bodyCell="{ column, record }">
            <Button v-if="column.key === 'action' && canResolveQuarantine" type="link" size="small"
                    @click="openResolution(asInbox(record))">处理</Button>
          </template>
        </Table>
      </TabPane>
    </Tabs>

    <Modal v-model:open="resolutionOpen" title="处理隔离回调" :confirm-loading="resolving" @ok="submitResolution">
      <FormItem label="处理动作" required>
        <Select v-model:value="resolution.action" :options="[
          { label: '关联并重试', value: 'LINK' }, { label: '按原关联重试', value: 'RETRY' },
          { label: '丢弃', value: 'DISCARD' },
        ]" />
      </FormItem>
      <FormItem v-if="resolution.action !== 'DISCARD'" label="执行 ID">
        <Input v-model:value="resolution.executionId" />
      </FormItem>
      <FormItem label="处理说明" required><Textarea v-model:value="resolution.note" :rows="4" /></FormItem>
    </Modal>
  </Page>
</template>

<style scoped>
.toolbar { margin-bottom: 12px; }
.toolbar :deep(.ant-input), .toolbar :deep(.ant-select) { width: 190px; }
.asset-description { min-height: 44px; color: rgb(100 116 139); }
</style>

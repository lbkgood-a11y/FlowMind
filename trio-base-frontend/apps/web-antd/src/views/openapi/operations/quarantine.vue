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
  Descriptions,
  DescriptionsItem,
  Drawer,
  FormItem,
  Input,
  InputNumber,
  message,
  Modal,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
} from 'ant-design-vue';

import { getCallbackQuarantine, resolveCallbackQuarantine } from '#/api';
import {
  BusinessPageScaffold,
  CompactQueryBar,
  CompactTableFrame,
  CompactToolbar,
  MultiTableLayout,
} from '#/shared';

const Textarea = Input.TextArea;
const PERMISSIONS = {
  quarantineRead: '/api/v1/openapi/management/callback-quarantine:GET',
  quarantineWrite: '/api/v1/openapi/management/callback-quarantine:POST',
} as const;

const { hasAccessByCodes } = useAccess();
const canReadQuarantine = computed(() => hasAccessByCodes([PERMISSIONS.quarantineRead]));
const canResolveQuarantine = computed(() => hasAccessByCodes([PERMISSIONS.quarantineWrite]));

const loading = ref(false);
const resolving = ref(false);
const detailOpen = ref(false);
const resolutionOpen = ref(false);
const quarantine = ref<OpenApiOperationsApi.CallbackInbox[]>([]);
const selectedInbox = ref<OpenApiOperationsApi.CallbackInbox>();

const query = reactive({
  keyword: '',
  limit: 50,
});
const resolution = reactive({
  action: 'LINK' as 'DISCARD' | 'LINK' | 'RETRY',
  executionId: '',
  note: '',
});

const filteredQuarantine = computed(() => {
  const keyword = query.keyword.trim().toLowerCase();
  if (!keyword) return quarantine.value;
  return quarantine.value.filter((item) =>
    [
      item.applicationClientId,
      item.correlationValue,
      item.executionId,
      item.id,
      item.partnerEventId,
      item.quarantineReason,
      item.tenantId,
    ]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(keyword)),
  );
});
const quarantineStats = computed(() => ({
  missingExecution: quarantine.value.filter((item) => !item.executionId).length,
  retryCandidates: quarantine.value.filter((item) => item.executionId).length,
  total: quarantine.value.length,
}));

const quarantineColumns: TableProps['columns'] = [
  { dataIndex: 'id', fixed: 'left', key: 'id', title: '收件箱 ID', width: 220 },
  { dataIndex: 'inboxState', key: 'state', title: '状态', width: 130 },
  { dataIndex: 'partnerEventId', key: 'event', title: '伙伴事件 ID', width: 180 },
  { dataIndex: 'correlationValue', key: 'correlation', title: '关联值', width: 200 },
  { dataIndex: 'executionId', key: 'executionId', title: '执行 ID', width: 220 },
  { dataIndex: 'quarantineReason', key: 'reason', title: '隔离原因', width: 220 },
  { dataIndex: 'signalAttempts', key: 'attempts', title: 'Signal 次数', width: 110 },
  { dataIndex: 'receivedAt', key: 'receivedAt', title: '接收时间', width: 180 },
  { fixed: 'right', key: 'action', title: '操作', width: 140 },
];

async function loadQuarantine() {
  if (!canReadQuarantine.value) return;
  loading.value = true;
  try {
    quarantine.value = await getCallbackQuarantine({ limit: query.limit });
  } finally {
    loading.value = false;
  }
}

function resetQuery() {
  query.keyword = '';
  query.limit = 50;
  loadQuarantine();
}

function openDetail(record: OpenApiOperationsApi.CallbackInbox) {
  selectedInbox.value = record;
  detailOpen.value = true;
}

function openResolution(record: OpenApiOperationsApi.CallbackInbox) {
  selectedInbox.value = record;
  resolution.action = 'LINK';
  resolution.executionId = record.executionId || '';
  resolution.note = '';
  resolutionOpen.value = true;
}

async function submitResolution() {
  if (!selectedInbox.value) return;
  if (!resolution.note.trim()) {
    message.warning('请填写处理说明');
    return;
  }
  if (resolution.action !== 'DISCARD' && !resolution.executionId.trim()) {
    message.warning('请填写执行 ID');
    return;
  }
  resolving.value = true;
  try {
    const result = await resolveCallbackQuarantine(selectedInbox.value.id, {
      action: resolution.action,
      executionId:
        resolution.action === 'DISCARD' ? undefined : resolution.executionId.trim() || undefined,
      note: resolution.note.trim(),
    });
    message.success('隔离回调已处理');
    resolutionOpen.value = false;
    selectedInbox.value = result;
    await loadQuarantine();
  } finally {
    resolving.value = false;
  }
}

function stateColor(state?: string) {
  if (state === 'SIGNALLED') return 'green';
  if (state === 'FAILED' || state === 'QUARANTINED') return 'red';
  if (state === 'SIGNALING') return 'blue';
  if (state === 'SIGNAL_PENDING') return 'orange';
  return 'default';
}

function formatJson(value?: Record<string, unknown>) {
  return JSON.stringify(value ?? {}, null, 2);
}

function asInbox(record: unknown) {
  return record as OpenApiOperationsApi.CallbackInbox;
}

onMounted(() => {
  loadQuarantine();
});
</script>

<template>
  <Page
    auto-content-height
    description="对无法自动关联或 Signal 失败的外部回调进行定位、补链、重试与丢弃"
    title="OpenAPI 回调隔离区"
  >
    <BusinessPageScaffold pattern="multi-table">
      <template #query>
        <CompactQueryBar :columns="3">
          <FormItem label="关键字">
            <Input
              v-model:value="query.keyword"
              allow-clear
              placeholder="事件、关联值、执行、客户端"
            />
          </FormItem>
          <FormItem label="读取条数">
            <InputNumber v-model:value="query.limit" :min="10" :max="500" class="number-input" />
          </FormItem>
          <template #actions>
            <Button @click="resetQuery">重置</Button>
            <Button type="primary" @click="loadQuarantine">刷新</Button>
          </template>
        </CompactQueryBar>
      </template>

      <template #toolbar>
        <CompactToolbar title="隔离回调" subtitle="只展示当前处于 QUARANTINED 的回调收件箱记录" />
      </template>

      <Alert
        v-if="!canReadQuarantine"
        show-icon
        type="warning"
        message="当前账号缺少 OpenAPI 回调隔离区查询权限"
      />

      <MultiTableLayout v-else :columns="3">
        <Card size="small">
          <Statistic title="隔离总数" :value="quarantineStats.total" />
        </Card>
        <Card size="small">
          <Statistic title="可重试" :value="quarantineStats.retryCandidates" />
        </Card>
        <Card size="small">
          <Statistic title="缺少执行 ID" :value="quarantineStats.missingExecution" />
        </Card>
      </MultiTableLayout>

      <CompactTableFrame v-if="canReadQuarantine">
        <Table
          row-key="id"
          size="small"
          :columns="quarantineColumns"
          :data-source="filteredQuarantine"
          :loading="loading"
          :pagination="{ pageSize: 20, showSizeChanger: true, size: 'small' }"
          :scroll="{ x: 1420 }"
        >
          <template #bodyCell="{ column, record }">
            <Tag v-if="column.key === 'state'" :color="stateColor(record.inboxState)">
              {{ record.inboxState }}
            </Tag>
            <Space v-else-if="column.key === 'action'" size="small">
              <Button size="small" type="link" @click="openDetail(asInbox(record))">
                详情
              </Button>
              <Button
                v-if="canResolveQuarantine"
                size="small"
                type="link"
                @click="openResolution(asInbox(record))"
              >
                处理
              </Button>
            </Space>
          </template>
        </Table>
      </CompactTableFrame>
    </BusinessPageScaffold>

    <Drawer
      v-model:open="detailOpen"
      :footer="null"
      placement="right"
      title="回调详情"
      width="820"
    >
      <Space v-if="selectedInbox" direction="vertical" size="middle" class="detail-stack">
        <Descriptions bordered :column="2" size="small">
          <DescriptionsItem label="收件箱 ID">{{ selectedInbox.id }}</DescriptionsItem>
          <DescriptionsItem label="状态">
            <Tag :color="stateColor(selectedInbox.inboxState)">
              {{ selectedInbox.inboxState }}
            </Tag>
          </DescriptionsItem>
          <DescriptionsItem label="租户">{{ selectedInbox.tenantId }}</DescriptionsItem>
          <DescriptionsItem label="应用客户端">{{ selectedInbox.applicationClientId }}</DescriptionsItem>
          <DescriptionsItem label="伙伴事件 ID">{{ selectedInbox.partnerEventId }}</DescriptionsItem>
          <DescriptionsItem label="关联值">{{ selectedInbox.correlationValue }}</DescriptionsItem>
          <DescriptionsItem label="执行 ID">{{ selectedInbox.executionId || '-' }}</DescriptionsItem>
          <DescriptionsItem label="Signal 名称">{{ selectedInbox.signalName || '-' }}</DescriptionsItem>
          <DescriptionsItem label="Signal 次数">{{ selectedInbox.signalAttempts }}</DescriptionsItem>
          <DescriptionsItem label="下次 Signal">{{ selectedInbox.nextSignalAt || '-' }}</DescriptionsItem>
          <DescriptionsItem label="隔离原因">{{ selectedInbox.quarantineReason || '-' }}</DescriptionsItem>
          <DescriptionsItem label="最后错误">{{ selectedInbox.lastSignalError || '-' }}</DescriptionsItem>
          <DescriptionsItem label="处理状态">{{ selectedInbox.resolutionState || '-' }}</DescriptionsItem>
          <DescriptionsItem label="处理人">{{ selectedInbox.resolvedBy || '-' }}</DescriptionsItem>
          <DescriptionsItem label="处理时间">{{ selectedInbox.resolvedAt || '-' }}</DescriptionsItem>
          <DescriptionsItem label="保留至">{{ selectedInbox.retentionUntil || '-' }}</DescriptionsItem>
          <DescriptionsItem label="Action TraceId">
            {{ selectedInbox.actionTraceId || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="Action 关联">
            {{ selectedInbox.actionCorrelationId || '-' }}
          </DescriptionsItem>
        </Descriptions>
        <Card size="small" title="映射 Payload">
          <pre class="json-block">{{ formatJson(selectedInbox.mappedPayload) }}</pre>
        </Card>
      </Space>
    </Drawer>

    <Modal
      v-model:open="resolutionOpen"
      title="处理隔离回调"
      :confirm-loading="resolving"
      @ok="submitResolution"
    >
      <FormItem label="处理动作" required>
        <Select
          v-model:value="resolution.action"
          :options="[
            { label: '关联并重试', value: 'LINK' },
            { label: '按原关联重试', value: 'RETRY' },
            { label: '丢弃', value: 'DISCARD' },
          ]"
        />
      </FormItem>
      <FormItem v-if="resolution.action !== 'DISCARD'" label="执行 ID" required>
        <Input v-model:value="resolution.executionId" />
      </FormItem>
      <FormItem label="处理说明" required>
        <Textarea v-model:value="resolution.note" :rows="4" />
      </FormItem>
    </Modal>
  </Page>
</template>

<style scoped>
.detail-stack {
  width: 100%;
}

.json-block {
  max-height: 320px;
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

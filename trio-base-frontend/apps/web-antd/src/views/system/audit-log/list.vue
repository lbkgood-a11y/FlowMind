<script setup lang="ts">
import type { SystemGovernanceApi } from '#/api';
import type { TableProps } from 'ant-design-vue';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import {
  Button,
  Descriptions,
  DescriptionsItem,
  Drawer,
  Input,
  Pagination,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
} from 'ant-design-vue';

import { getAuditLogDetail, getAuditLogPage } from '#/api';
import { ERP_TOOLBAR_ICONS } from '#/constants/erp-toolbar';

const AUDIT_PERMISSIONS = {
  query: '/api/v1/audit-logs:GET',
} as const;

const { hasAccessByCodes } = useAccess();
const canQuery = computed(() => hasAccessByCodes([AUDIT_PERMISSIONS.query]));

const logs = ref<SystemGovernanceApi.AuditLog[]>([]);
const loading = ref(false);
const detailOpen = ref(false);
const detail = ref<SystemGovernanceApi.AuditLog>();
const page = ref(1);
const size = ref(20);
const total = ref(0);

const query = reactive({
  requestPath: '',
  resultStatus: undefined as string | undefined,
  username: '',
});

const columns = computed<TableProps['columns']>(() => [
  { dataIndex: 'operatedAt', fixed: 'left', key: 'operatedAt', title: '操作时间', width: 190 },
  { dataIndex: 'username', key: 'username', title: '用户', width: 140 },
  { dataIndex: 'httpMethod', key: 'httpMethod', title: '方法', width: 90 },
  { dataIndex: 'requestPath', key: 'requestPath', title: '路径', width: 260 },
  { dataIndex: 'resultStatus', key: 'resultStatus', title: '结果', width: 100 },
  { dataIndex: 'statusCode', key: 'statusCode', title: '状态码', width: 90 },
  { dataIndex: 'latencyMs', key: 'latencyMs', title: '耗时(ms)', width: 110 },
  { dataIndex: 'clientIp', key: 'clientIp', title: 'IP', width: 140 },
  { dataIndex: 'traceId', key: 'traceId', title: 'TraceId', width: 220 },
  { fixed: 'right', key: 'action', title: '操作', width: 90 },
]);

async function loadLogs() {
  if (!canQuery.value) {
    logs.value = [];
    total.value = 0;
    return;
  }
  loading.value = true;
  try {
    const result = await getAuditLogPage({
      page: page.value,
      requestPath: query.requestPath || undefined,
      resultStatus: query.resultStatus,
      size: size.value,
      username: query.username || undefined,
    });
    logs.value = result.records;
    total.value = result.total;
  } finally {
    loading.value = false;
  }
}

async function openDetail(record: SystemGovernanceApi.AuditLog) {
  detail.value = await getAuditLogDetail(record.id);
  detailOpen.value = true;
}

function resetQuery() {
  query.username = '';
  query.requestPath = '';
  query.resultStatus = undefined;
  page.value = 1;
  loadLogs();
}

function asAudit(record: Record<string, any>) {
  return record as SystemGovernanceApi.AuditLog;
}

onMounted(loadLogs);
</script>

<template>
  <Page auto-content-height>
    <div class="erp-compact-page audit-page">
      <section class="toolbar">
        <Space wrap>
          <Input v-model:value="query.username" class="query-input" placeholder="用户" allow-clear />
          <Input v-model:value="query.requestPath" class="query-input path-input" placeholder="路径" allow-clear />
          <Select
            v-model:value="query.resultStatus"
            allow-clear
            class="query-select"
            :options="[
              { label: '成功', value: 'SUCCESS' },
              { label: '失败', value: 'FAILURE' },
            ]"
            placeholder="结果"
          />
          <Button v-if="canQuery" type="primary" @click="page = 1; loadLogs()">查询</Button>
          <Button v-if="canQuery" @click="resetQuery">重置</Button>
        <Tooltip v-if="canQuery" title="刷新">
          <Button shape="circle" @click="loadLogs">
            <IconifyIcon :icon="ERP_TOOLBAR_ICONS.refresh" class="size-4" />
          </Button>
        </Tooltip>
        </Space>
      </section>

      <section class="table-shell">
        <Table
          row-key="id"
          :columns="columns"
          :data-source="logs"
          :loading="loading"
          :pagination="false"
          :scroll="{ x: 1480 }"
          size="small"
          :sticky="{ offsetScroll: 0 }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'resultStatus'">
              <Tag :color="record.resultStatus === 'SUCCESS' ? 'green' : 'red'">
                {{ record.resultStatus === 'SUCCESS' ? '成功' : '失败' }}
              </Tag>
            </template>
            <template v-else-if="column.key === 'action'">
              <Button type="link" size="small" @click="openDetail(asAudit(record))">详情</Button>
            </template>
          </template>
        </Table>
      </section>

      <div class="pager">
        <Pagination
          v-model:current="page"
          v-model:page-size="size"
          size="small"
          show-size-changer
          :total="total"
          @change="loadLogs"
        />
      </div>
    </div>

    <Drawer v-model:open="detailOpen" title="审计详情" width="720px">
      <Descriptions v-if="detail" bordered :column="1" size="small">
        <DescriptionsItem label="用户">{{ detail.username || detail.userId || '-' }}</DescriptionsItem>
        <DescriptionsItem label="权限码">{{ detail.permissionCode || '-' }}</DescriptionsItem>
        <DescriptionsItem label="请求">{{ detail.httpMethod }} {{ detail.requestPath }}</DescriptionsItem>
        <DescriptionsItem label="查询参数">{{ detail.queryString || '-' }}</DescriptionsItem>
        <DescriptionsItem label="结果">{{ detail.resultStatus }} / {{ detail.statusCode }}</DescriptionsItem>
        <DescriptionsItem label="错误">{{ detail.errorMessage || '-' }}</DescriptionsItem>
        <DescriptionsItem label="TraceId">{{ detail.traceId || '-' }}</DescriptionsItem>
        <DescriptionsItem label="User-Agent">{{ detail.userAgent || '-' }}</DescriptionsItem>
      </Descriptions>
    </Drawer>
  </Page>
</template>

<style scoped>
.audit-page {
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

.query-input {
  width: 160px;
}

.path-input {
  width: 240px;
}

.query-select {
  width: 130px;
}

.pager {
  display: flex;
  justify-content: flex-end;
}
</style>

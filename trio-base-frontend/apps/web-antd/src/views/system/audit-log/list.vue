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
  Descriptions,
  DescriptionsItem,
  Drawer,
  FormItem,
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
import {
  BusinessPageScaffold,
  CompactQueryBar,
  CompactTableFrame,
  CompactToolbar,
  restoreTableColumnSettings,
  TableColumnSettings,
} from '#/shared';

const AUDIT_PERMISSIONS = {
  query: '/api/v1/audit-logs:GET',
} as const;

const { hasAccessByCodes } = useAccess();
const canQuery = computed(() => hasAccessByCodes([AUDIT_PERMISSIONS.query]));

const logs = ref<SystemGovernanceApi.AuditLog[]>([]);
const loading = ref(false);
const detailOpen = ref(false);
const detail = ref<SystemGovernanceApi.AuditLog>();
const collapsed = ref(false);
const queryHidden = ref(false);
const blockFullscreen = ref(false);
const tableKey = ref(0);
const page = ref(1);
const size = ref(20);
const total = ref(0);

const query = reactive({
  actionStatus: undefined as string | undefined,
  actionType: '',
  requestPath: '',
  resultStatus: undefined as string | undefined,
  username: '',
});

const baseColumns: NonNullable<TableProps['columns']> = [
  { dataIndex: 'operatedAt', fixed: 'left', key: 'operatedAt', title: '操作时间', width: 190 },
  { dataIndex: 'username', key: 'username', title: '用户', width: 100 },
  { dataIndex: 'httpMethod', key: 'httpMethod', title: '方法', width: 70 },
  { dataIndex: 'requestPath', ellipsis: true, key: 'requestPath', title: '路径', width: 220 },
  { dataIndex: 'actionType', ellipsis: true, key: 'actionType', title: 'Action 类型', width: 150 },
  { dataIndex: 'actionStatus', key: 'actionStatus', title: 'Action 状态', width: 100 },
  { dataIndex: 'actionTargetId', ellipsis: true, key: 'actionTargetId', title: 'Action 目标', width: 130 },
  { dataIndex: 'resultStatus', key: 'resultStatus', title: '结果', width: 80 },
  { dataIndex: 'statusCode', key: 'statusCode', title: '状态码', width: 75 },
  { dataIndex: 'latencyMs', key: 'latencyMs', title: '耗时(ms)', width: 85 },
  { dataIndex: 'clientIp', key: 'clientIp', title: 'IP', width: 115 },
  { dataIndex: 'traceId', ellipsis: true, key: 'traceId', title: 'TraceId', width: 190 },
  { fixed: 'right', key: 'action', title: '操作', width: 70 },
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
    'triobase:table-columns:system-audit-log',
    defaultColumnSettings,
  ),
);
const columns = computed<TableProps['columns']>(() =>
  columnSettings
    .filter((setting) => setting.visible)
    .map((setting) => ({
      ...baseColumns.find((column) => String(column.key) === setting.key),
      fixed: setting.fixed,
      width: setting.width,
    })),
);

async function loadLogs() {
  if (!canQuery.value) {
    logs.value = [];
    total.value = 0;
    return;
  }
  loading.value = true;
  try {
    const result = await getAuditLogPage({
      actionStatus: query.actionStatus,
      actionType: query.actionType || undefined,
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
  query.actionType = '';
  query.actionStatus = undefined;
  page.value = 1;
  loadLogs();
}

async function handleToolbarSearch() {
  page.value = 1;
  await loadLogs();
  queryHidden.value = true;
}

function applyColumnSettings(settings: TableColumnSetting[]) {
  columnSettings.splice(0, columnSettings.length, ...settings);
  tableKey.value += 1;
}

function toggleFullscreen() {
  blockFullscreen.value = !blockFullscreen.value;
}

function asAudit(record: Record<string, any>) {
  return record as SystemGovernanceApi.AuditLog;
}

onMounted(loadLogs);
</script>

<template>
  <Page auto-content-height>
    <BusinessPageScaffold
      class="audit-page"
      pattern="single-table"
      :fullscreen="blockFullscreen"
      :class="{ 'is-block-fullscreen': blockFullscreen, 'is-query-hidden': queryHidden }"
    >
      <template #query>
        <CompactQueryBar v-show="!queryHidden" :collapsed="collapsed" :columns="4">
          <FormItem label="用户"><Input v-model:value="query.username" allow-clear placeholder="请输入" /></FormItem>
          <FormItem label="请求路径"><Input v-model:value="query.requestPath" allow-clear placeholder="请输入" /></FormItem>
          <FormItem label="Action 类型"><Input v-model:value="query.actionType" allow-clear placeholder="请输入" /></FormItem>
          <FormItem v-if="!collapsed" label="结果">
<Select
            v-model:value="query.resultStatus"
            allow-clear
            :options="[
              { label: '成功', value: 'SUCCESS' },
              { label: '失败', value: 'FAILURE' },
            ]"
            placeholder="请选择"
          />
</FormItem>
          <FormItem v-if="!collapsed" label="Action 状态">
<Select
            v-model:value="query.actionStatus"
            allow-clear
            :options="['CREATED','VALIDATING','REJECTED','AUTHORIZED','ACCEPTED','RUNNING','SUCCEEDED','FAILED','CANCELLED','COMPENSATING','COMPENSATED'].map((value) => ({ label: value, value }))"
            placeholder="请选择"
          />
</FormItem>
          <template #actions>
            <Button v-if="canQuery" @click="resetQuery">重置</Button>
            <Button v-if="canQuery" type="primary" @click="page = 1; loadLogs()">搜索</Button>
            <Button type="link" @click="collapsed = !collapsed">
              {{ collapsed ? '展开' : '收起' }}
              <IconifyIcon :icon="collapsed ? ERP_TOOLBAR_ICONS.expand : ERP_TOOLBAR_ICONS.collapse" class="ml-1 size-4" />
            </Button>
          </template>
        </CompactQueryBar>
      </template>

      <template #toolbar>
        <CompactToolbar>
          <template #title><div class="list-title"><h2>审计日志</h2><Button v-if="queryHidden" type="link" @click="queryHidden = false">展开搜索</Button></div></template>
          <Space :size="8">
            <Tooltip v-if="canQuery" title="查询并隐藏搜索栏"><Button shape="circle" type="primary" @click="handleToolbarSearch"><i aria-hidden="true" class="vxe-button--item vxe-table-icon-search"></i></Button></Tooltip>
            <Tooltip v-if="canQuery" title="刷新"><Button shape="circle" @click="loadLogs"><i aria-hidden="true" class="vxe-button--item vxe-table-icon-refresh"></i></Button></Tooltip>
            <Tooltip :title="blockFullscreen ? '还原' : '全屏'"><Button shape="circle" @click="toggleFullscreen"><i aria-hidden="true" class="vxe-button--item vxe-button--prefix-icon" :class="blockFullscreen ? 'vxe-table-icon-minimize' : 'vxe-table-icon-fullscreen'"></i></Button></Tooltip>
            <TableColumnSettings :defaults="defaultColumnSettings" :model-value="columnSettings" storage-key="triobase:table-columns:system-audit-log" @apply="applyColumnSettings" />
          </Space>
        </CompactToolbar>
      </template>

      <CompactTableFrame>
        <Table
          :key="tableKey"
          row-key="id"
          :columns="columns"
          :data-source="logs"
          :loading="loading"
          :pagination="false"
          :scroll="{ x: 1575 }"
          size="small"
          table-layout="fixed"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'resultStatus'">
              <Tag :color="record.resultStatus === 'SUCCESS' ? 'green' : 'red'">
                {{ record.resultStatus === 'SUCCESS' ? '成功' : '失败' }}
              </Tag>
            </template>
            <template v-else-if="column.key === 'actionStatus'">
              <Tag v-if="record.actionStatus" color="blue">{{ record.actionStatus }}</Tag>
              <span v-else>-</span>
            </template>
            <template v-else-if="column.key === 'action'">
              <Button type="link" size="small" @click="openDetail(asAudit(record))">详情</Button>
            </template>
          </template>
        </Table>
        <template #footer>
          <div class="table-total">共 {{ total }} 条记录</div>
          <Pagination
            v-model:current="page"
            v-model:page-size="size"
            :page-size-options="['10', '20', '50', '100']"
            size="small"
            :total="total"
            show-less-items
            show-size-changer
          @change="loadLogs"
          />
        </template>
      </CompactTableFrame>
    </BusinessPageScaffold>

    <Drawer v-model:open="detailOpen" title="审计详情" width="720px">
      <Descriptions v-if="detail" bordered :column="1" size="small">
        <DescriptionsItem label="用户">{{ detail.username || detail.userId || '-' }}</DescriptionsItem>
        <DescriptionsItem label="权限码">{{ detail.permissionCode || '-' }}</DescriptionsItem>
        <DescriptionsItem label="请求">{{ detail.httpMethod }} {{ detail.requestPath }}</DescriptionsItem>
        <DescriptionsItem label="查询参数">{{ detail.queryString || '-' }}</DescriptionsItem>
        <DescriptionsItem label="ActionId">{{ detail.actionId || '-' }}</DescriptionsItem>
        <DescriptionsItem label="Action 类型">{{ detail.actionType || '-' }}</DescriptionsItem>
        <DescriptionsItem label="Action 来源">{{ detail.actionSource || '-' }}</DescriptionsItem>
        <DescriptionsItem label="Action 状态">{{ detail.actionStatus || '-' }}</DescriptionsItem>
        <DescriptionsItem label="Action 目标">
          {{ detail.actionTargetType || '-' }} / {{ detail.actionTargetId || '-' }}
        </DescriptionsItem>
        <DescriptionsItem label="Action 关联">{{ detail.actionCorrelationId || '-' }}</DescriptionsItem>
        <DescriptionsItem label="幂等 Key">{{ detail.actionIdempotencyKey || '-' }}</DescriptionsItem>
        <DescriptionsItem label="Action 摘要">{{ detail.actionSummary || '-' }}</DescriptionsItem>
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

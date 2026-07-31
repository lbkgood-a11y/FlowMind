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
  FormItem,
  Input,
  message,
  Pagination,
  Popconfirm,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Tooltip,
} from 'ant-design-vue';

import { getLoginLogPage, getSessionPage, revokeSession } from '#/api';
import { ERP_TOOLBAR_ICONS } from '#/constants/erp-toolbar';
import {
  BusinessPageScaffold,
  CompactQueryBar,
  CompactTableFrame,
  CompactToolbar,
  restoreTableColumnSettings,
  TableColumnSettings,
} from '#/shared';

const TabPane = Tabs.TabPane;

const SESSION_PERMISSIONS = {
  query: '/api/v1/sessions:GET',
  revoke: '/api/v1/sessions/*:PUT',
} as const;

const { hasAccessByCodes } = useAccess();
const canQuery = computed(() => hasAccessByCodes([SESSION_PERMISSIONS.query]));
const canRevoke = computed(() => hasAccessByCodes([SESSION_PERMISSIONS.revoke]));

const activeTab = ref('sessions');
const loading = ref(false);
const sessions = ref<SystemGovernanceApi.UserSession[]>([]);
const loginLogs = ref<SystemGovernanceApi.LoginLog[]>([]);
const page = ref(1);
const size = ref(20);
const total = ref(0);
const collapsed = ref(false);
const queryHidden = ref(false);
const blockFullscreen = ref(false);
const tableKey = ref(0);

const query = reactive({
  loginResult: undefined as string | undefined,
  sessionStatus: undefined as string | undefined,
  username: '',
});

const sessionBaseColumns: NonNullable<TableProps['columns']> = [
  { dataIndex: 'username', fixed: 'left', key: 'username', title: '用户', width: 140 },
  { dataIndex: 'sessionStatus', key: 'sessionStatus', title: '状态', width: 120 },
  { dataIndex: 'clientIp', key: 'clientIp', title: 'IP', width: 140 },
  { dataIndex: 'issuedAt', key: 'issuedAt', title: '签发时间', width: 190 },
  { dataIndex: 'expiresAt', key: 'expiresAt', title: '过期时间', width: 190 },
  { dataIndex: 'lastActiveAt', key: 'lastActiveAt', title: '最后活跃', width: 190 },
  { dataIndex: 'userAgent', ellipsis: true, key: 'userAgent', title: 'User-Agent', width: 360 },
  { fixed: 'right', key: 'action', title: '操作', width: 110 },
];

const loginBaseColumns: NonNullable<TableProps['columns']> = [
  { dataIndex: 'loginAt', fixed: 'left', key: 'loginAt', title: '登录时间', width: 190 },
  { dataIndex: 'username', key: 'username', title: '用户', width: 140 },
  { dataIndex: 'loginResult', key: 'loginResult', title: '结果', width: 110 },
  { dataIndex: 'failureReason', ellipsis: true, key: 'failureReason', title: '失败原因', width: 180 },
  { dataIndex: 'clientIp', key: 'clientIp', title: 'IP', width: 140 },
  { dataIndex: 'traceId', ellipsis: true, key: 'traceId', title: 'TraceId', width: 220 },
  { dataIndex: 'userAgent', ellipsis: true, key: 'userAgent', title: 'User-Agent', width: 360 },
];

function defaultsFrom(columns: NonNullable<TableProps['columns']>) {
  return columns.map((column) => ({
    fixed: column.fixed === true ? 'left' : column.fixed || undefined,
    key: String(column.key),
    required: column.key === 'action',
    title: String(column.title),
    visible: true,
    width: Number(column.width || 120),
  })) as TableColumnSetting[];
}

const sessionDefaults = defaultsFrom(sessionBaseColumns);
const loginDefaults = defaultsFrom(loginBaseColumns);
const sessionSettings = reactive(
  restoreTableColumnSettings('triobase:table-columns:system-session', sessionDefaults),
);
const loginSettings = reactive(
  restoreTableColumnSettings('triobase:table-columns:system-login-log', loginDefaults),
);
function resolveColumns(
  base: NonNullable<TableProps['columns']>,
  settings: TableColumnSetting[],
) {
  return settings.filter((item) => item.visible).map((item) => ({
    ...base.find((column) => String(column.key) === item.key),
    fixed: item.fixed,
    width: item.width,
  }));
}
const sessionColumns = computed<TableProps['columns']>(() =>
  resolveColumns(sessionBaseColumns, sessionSettings),
);
const loginColumns = computed<TableProps['columns']>(() =>
  resolveColumns(loginBaseColumns, loginSettings),
);
const activeDefaults = computed(() =>
  activeTab.value === 'sessions' ? sessionDefaults : loginDefaults,
);
const activeSettings = computed(() =>
  activeTab.value === 'sessions' ? sessionSettings : loginSettings,
);
const activeStorageKey = computed(() =>
  activeTab.value === 'sessions'
    ? 'triobase:table-columns:system-session'
    : 'triobase:table-columns:system-login-log',
);

async function loadData() {
  if (!canQuery.value) {
    sessions.value = [];
    loginLogs.value = [];
    total.value = 0;
    return;
  }
  loading.value = true;
  try {
    if (activeTab.value === 'sessions') {
      const result = await getSessionPage({
        page: page.value,
        sessionStatus: query.sessionStatus,
        size: size.value,
        username: query.username || undefined,
      });
      sessions.value = result.records;
      total.value = result.total;
    } else {
      const result = await getLoginLogPage({
        loginResult: query.loginResult,
        page: page.value,
        size: size.value,
        username: query.username || undefined,
      });
      loginLogs.value = result.records;
      total.value = result.total;
    }
  } finally {
    loading.value = false;
  }
}

async function revoke(record: SystemGovernanceApi.UserSession) {
  await revokeSession(record.id);
  message.success('会话已失效');
  await loadData();
}

function changeTab(key: string) {
  activeTab.value = key;
  page.value = 1;
  loadData();
}

function resetQuery() {
  query.username = '';
  query.loginResult = undefined;
  query.sessionStatus = undefined;
  page.value = 1;
  loadData();
}

async function handleToolbarSearch() {
  page.value = 1;
  await loadData();
  queryHidden.value = true;
}

function applyColumnSettings(settings: TableColumnSetting[]) {
  const target = activeTab.value === 'sessions' ? sessionSettings : loginSettings;
  target.splice(0, target.length, ...settings);
  tableKey.value += 1;
}

function toggleFullscreen() {
  blockFullscreen.value = !blockFullscreen.value;
}

function asSession(record: Record<string, any>) {
  return record as SystemGovernanceApi.UserSession;
}

onMounted(loadData);
</script>

<template>
  <Page auto-content-height>
    <BusinessPageScaffold class="session-page" pattern="single-table" :fullscreen="blockFullscreen" :class="{ 'is-block-fullscreen': blockFullscreen, 'is-query-hidden': queryHidden }">
      <template #query>
        <CompactQueryBar v-show="!queryHidden" :collapsed="collapsed" :columns="3">
          <FormItem label="用户"><Input v-model:value="query.username" placeholder="请输入" allow-clear /></FormItem>
          <FormItem v-if="!collapsed" label="状态">
            <Select
              v-if="activeTab === 'sessions'"
              v-model:value="query.sessionStatus"
              allow-clear
              :options="[
                { label: '活跃', value: 'ACTIVE' },
                { label: '已退出', value: 'LOGGED_OUT' },
                { label: '已失效', value: 'REVOKED' },
              ]"
              placeholder="请选择"
            />
            <Select
              v-else
              v-model:value="query.loginResult"
              allow-clear
              :options="[
                { label: '成功', value: 'SUCCESS' },
                { label: '失败', value: 'FAILURE' },
              ]"
              placeholder="请选择"
            />
          </FormItem>
          <template #actions>
            <Button v-if="canQuery" @click="resetQuery">重置</Button>
            <Button v-if="canQuery" type="primary" @click="page = 1; loadData()">搜索</Button>
            <Button type="link" @click="collapsed = !collapsed">{{ collapsed ? '展开' : '收起' }}<IconifyIcon :icon="collapsed ? ERP_TOOLBAR_ICONS.expand : ERP_TOOLBAR_ICONS.collapse" class="ml-1 size-4" /></Button>
          </template>
        </CompactQueryBar>
      </template>

      <template #toolbar>
        <CompactToolbar>
          <template #title><div class="list-title"><h2>会话与登录日志</h2><Button v-if="queryHidden" type="link" @click="queryHidden = false">展开搜索</Button></div></template>
          <Space :size="8">
            <Tooltip v-if="canQuery" title="查询并隐藏搜索栏"><Button shape="circle" type="primary" @click="handleToolbarSearch"><i aria-hidden="true" class="vxe-button--item vxe-table-icon-search"></i></Button></Tooltip>
            <Tooltip v-if="canQuery" title="刷新"><Button shape="circle" @click="loadData"><i aria-hidden="true" class="vxe-button--item vxe-table-icon-refresh"></i></Button></Tooltip>
            <Tooltip :title="blockFullscreen ? '还原' : '全屏'"><Button shape="circle" @click="toggleFullscreen"><i aria-hidden="true" class="vxe-button--item vxe-button--prefix-icon" :class="blockFullscreen ? 'vxe-table-icon-minimize' : 'vxe-table-icon-fullscreen'"></i></Button></Tooltip>
            <TableColumnSettings :defaults="activeDefaults" :model-value="activeSettings" :storage-key="activeStorageKey" @apply="applyColumnSettings" />
          </Space>
        </CompactToolbar>
      </template>

      <Tabs :active-key="activeTab" size="small" @change="(key) => changeTab(String(key))">
        <TabPane key="sessions" tab="会话">
          <CompactTableFrame>
            <Table
              :key="`${tableKey}-sessions`"
              row-key="id"
              :columns="sessionColumns"
              :data-source="sessions"
              :loading="loading"
              :pagination="false"
              :scroll="{ x: 1480 }"
              size="small"
              table-layout="fixed"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'sessionStatus'">
                  <Tag :color="record.sessionStatus === 'ACTIVE' ? 'green' : 'default'">
                    {{ record.sessionStatus }}
                  </Tag>
                </template>
                <template v-else-if="column.key === 'action'">
                  <Popconfirm
                    v-if="canRevoke && record.sessionStatus === 'ACTIVE'"
                    title="确认强制失效该会话？"
                    @confirm="revoke(asSession(record))"
                  >
                    <Button danger type="link" size="small">失效</Button>
                  </Popconfirm>
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
                @change="loadData"
              />
            </template>
          </CompactTableFrame>
        </TabPane>
        <TabPane key="loginLogs" tab="登录日志">
          <CompactTableFrame>
            <Table
              :key="`${tableKey}-login-logs`"
              row-key="id"
              :columns="loginColumns"
              :data-source="loginLogs"
              :loading="loading"
              :pagination="false"
              :scroll="{ x: 1420 }"
              size="small"
              table-layout="fixed"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'loginResult'">
                  <Tag :color="record.loginResult === 'SUCCESS' ? 'green' : 'red'">
                    {{ record.loginResult === 'SUCCESS' ? '成功' : '失败' }}
                  </Tag>
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
                @change="loadData"
              />
            </template>
          </CompactTableFrame>
        </TabPane>
      </Tabs>
    </BusinessPageScaffold>
  </Page>
</template>

<style scoped>
.session-page {
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

.query-input,
.query-select {
  width: 160px;
}

.pager {
  display: flex;
  width: 100%;
  justify-content: flex-end;
}

.session-page :deep(.ant-tabs) {
  display: flex;
  min-height: 0;
  flex: 1 1 auto;
  flex-direction: column;
  overflow: hidden;
}

.session-page :deep(.ant-tabs-content-holder),
.session-page :deep(.ant-tabs-content),
.session-page :deep(.ant-tabs-tabpane-active) {
  display: flex;
  min-height: 0;
  flex: 1 1 auto;
  flex-direction: column;
  overflow: hidden;
}
</style>

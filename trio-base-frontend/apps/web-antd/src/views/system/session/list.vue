<script setup lang="ts">
import type { SystemGovernanceApi } from '#/api';
import type { TableProps } from 'ant-design-vue';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import {
  Button,
  Input,
  Pagination,
  Popconfirm,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Tooltip,
  message,
} from 'ant-design-vue';

import { getLoginLogPage, getSessionPage, revokeSession } from '#/api';
import { ERP_TOOLBAR_ICONS } from '#/constants/erp-toolbar';

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

const query = reactive({
  loginResult: undefined as string | undefined,
  sessionStatus: undefined as string | undefined,
  username: '',
});

const sessionColumns = computed<TableProps['columns']>(() => [
  { dataIndex: 'username', fixed: 'left', key: 'username', title: '用户', width: 140 },
  { dataIndex: 'sessionStatus', key: 'sessionStatus', title: '状态', width: 120 },
  { dataIndex: 'clientIp', key: 'clientIp', title: 'IP', width: 140 },
  { dataIndex: 'issuedAt', key: 'issuedAt', title: '签发时间', width: 190 },
  { dataIndex: 'expiresAt', key: 'expiresAt', title: '过期时间', width: 190 },
  { dataIndex: 'lastActiveAt', key: 'lastActiveAt', title: '最后活跃', width: 190 },
  { dataIndex: 'userAgent', key: 'userAgent', title: 'User-Agent', width: 360 },
  { fixed: 'right', key: 'action', title: '操作', width: 110 },
]);

const loginColumns = computed<TableProps['columns']>(() => [
  { dataIndex: 'loginAt', fixed: 'left', key: 'loginAt', title: '登录时间', width: 190 },
  { dataIndex: 'username', key: 'username', title: '用户', width: 140 },
  { dataIndex: 'loginResult', key: 'loginResult', title: '结果', width: 110 },
  { dataIndex: 'failureReason', key: 'failureReason', title: '失败原因', width: 180 },
  { dataIndex: 'clientIp', key: 'clientIp', title: 'IP', width: 140 },
  { dataIndex: 'traceId', key: 'traceId', title: 'TraceId', width: 220 },
  { dataIndex: 'userAgent', key: 'userAgent', title: 'User-Agent', width: 360 },
]);

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

function asSession(record: Record<string, any>) {
  return record as SystemGovernanceApi.UserSession;
}

onMounted(loadData);
</script>

<template>
  <Page auto-content-height>
    <div class="erp-compact-page session-page">
      <section class="toolbar">
        <Space wrap>
          <Input v-model:value="query.username" class="query-input" placeholder="用户" allow-clear />
          <Select
            v-if="activeTab === 'sessions'"
            v-model:value="query.sessionStatus"
            allow-clear
            class="query-select"
            :options="[
              { label: '活跃', value: 'ACTIVE' },
              { label: '已退出', value: 'LOGGED_OUT' },
              { label: '已失效', value: 'REVOKED' },
            ]"
            placeholder="会话状态"
          />
          <Select
            v-else
            v-model:value="query.loginResult"
            allow-clear
            class="query-select"
            :options="[
              { label: '成功', value: 'SUCCESS' },
              { label: '失败', value: 'FAILURE' },
            ]"
            placeholder="登录结果"
          />
          <Button v-if="canQuery" type="primary" @click="page = 1; loadData()">查询</Button>
          <Button v-if="canQuery" @click="resetQuery">重置</Button>
          <Tooltip v-if="canQuery" title="刷新">
            <Button shape="circle" @click="loadData">
              <IconifyIcon :icon="ERP_TOOLBAR_ICONS.refresh" class="size-4" />
            </Button>
          </Tooltip>
        </Space>
      </section>

      <Tabs :active-key="activeTab" size="small" @change="(key) => changeTab(String(key))">
        <TabPane key="sessions" tab="会话">
          <section class="table-shell">
            <Table
              row-key="id"
              :columns="sessionColumns"
              :data-source="sessions"
              :loading="loading"
              :pagination="false"
              :scroll="{ x: 1480 }"
              size="small"
              :sticky="{ offsetScroll: 0 }"
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
          </section>
        </TabPane>
        <TabPane key="loginLogs" tab="登录日志">
          <section class="table-shell">
            <Table
              row-key="id"
              :columns="loginColumns"
              :data-source="loginLogs"
              :loading="loading"
              :pagination="false"
              :scroll="{ x: 1420 }"
              size="small"
              :sticky="{ offsetScroll: 0 }"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'loginResult'">
                  <Tag :color="record.loginResult === 'SUCCESS' ? 'green' : 'red'">
                    {{ record.loginResult === 'SUCCESS' ? '成功' : '失败' }}
                  </Tag>
                </template>
              </template>
            </Table>
          </section>
        </TabPane>
      </Tabs>

      <div class="pager">
        <Pagination
          v-model:current="page"
          v-model:page-size="size"
          size="small"
          show-size-changer
          :total="total"
          @change="loadData"
        />
      </div>
    </div>
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
  justify-content: flex-end;
}
</style>

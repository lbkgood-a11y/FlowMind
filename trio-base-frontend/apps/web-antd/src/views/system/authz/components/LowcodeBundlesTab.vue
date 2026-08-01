<script setup lang="ts">
import type { TableProps } from 'ant-design-vue';
import type { SystemAuthorizationApi } from '#/api';

import { computed, inject, onMounted, ref, watch } from 'vue';
import { IconifyIcon } from '@vben/icons';
import { Alert, Button, message, Select, Space, Tag, Tooltip } from 'ant-design-vue';

import {
  applyLowcodeAuthorizationBundle,
  getLowcodeAuthorizationPublications,
  previewLowcodeAuthorizationBundle,
  reconcileLowcodeAuthorizationPublication,
  retryLowcodeAuthorizationPublication,
} from '#/api';
import { ERP_TOOLBAR_ICONS } from '#/constants/erp-toolbar';
import { ClientPaginatedTable } from '#/shared';

const ctx = inject<any>('authzContext')!;
const applicationResourceCode = ref('');
const preset = ref<SystemAuthorizationApi.LowcodeAuthorizationBundleRequest['preset']>('APPLICANT');
const preview = ref<SystemAuthorizationApi.LowcodeAuthorizationBundleResult>();
const publications = ref<SystemAuthorizationApi.LowcodeAuthorizationPublication[]>([]);
const publicationsError = ref('');
const requestKey = ref('');

const applicationOptions = computed(() => ctx.resourceList.value
  .filter((item: SystemAuthorizationApi.ResourceNode) => item.resourceType === 'LOWCODE_APP' && item.lifecycleStatus === 'ACTIVE')
  .map((item: SystemAuthorizationApi.ResourceNode) => ({
    label: item.displayName ? `${item.displayName} · ${item.resourceCode}` : item.resourceCode,
    value: item.resourceCode,
  })));

const presetOptions = [
  { label: '申请人', value: 'APPLICANT' },
  { label: '审批人', value: 'APPROVER' },
  { label: '设计者', value: 'DESIGNER' },
  { label: '管理员', value: 'ADMIN' },
];
const changeColumns: TableProps['columns'] = [
  { title: '资源', dataIndex: 'resourceCode' },
  { title: '动作', dataIndex: 'actionCode', width: 150 },
  { title: '差异', dataIndex: 'state', width: 120 },
];
const publicationColumns: TableProps['columns'] = [
  { title: '对象', dataIndex: 'aggregateType', width: 110 },
  { title: '操作', dataIndex: 'operation', width: 100 },
  { title: '状态', dataIndex: 'status', width: 130 },
  { title: '尝试', dataIndex: 'attemptCount', width: 80 },
  { title: '最近错误', dataIndex: 'lastError', ellipsis: true },
  { title: '处置', key: 'actions', width: 160 },
];

function payload() {
  return {
    applicationResourceCode: applicationResourceCode.value,
    preset: preset.value,
    roleId: ctx.selectedRoleId.value,
  };
}

async function dryRun() {
  if (!ctx.selectedRoleId.value || !applicationResourceCode.value) {
    message.warning('请先选择角色和已发布应用');
    return;
  }
  ctx.saving.value = true;
  try {
    preview.value = await previewLowcodeAuthorizationBundle(payload());
    requestKey.value = `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  } finally {
    ctx.saving.value = false;
  }
}

async function applyBundle() {
  if (!preview.value || !requestKey.value) return;
  ctx.saving.value = true;
  try {
    preview.value = await applyLowcodeAuthorizationBundle({ ...payload(), idempotencyKey: requestKey.value });
    message.success('授权套餐已原子应用');
  } finally {
    ctx.saving.value = false;
  }
}

async function loadPublications() {
  publicationsError.value = '';
  try {
    publications.value = await getLowcodeAuthorizationPublications();
  } catch {
    publications.value = [];
    publicationsError.value = '发布记录服务暂不可用，请确认网关和低代码服务已更新并正常运行。';
  }
}

async function retry(eventId: string) {
  await retryLowcodeAuthorizationPublication(eventId);
  message.success('已进入重试队列');
  await loadPublications();
}

async function reconcile(eventId: string) {
  await reconcileLowcodeAuthorizationPublication(eventId);
  message.success('已创建对账修复事件');
  await loadPublications();
}

onMounted(loadPublications);
watch(() => ctx.selectedRoleId.value, () => {
  preview.value = undefined;
  requestKey.value = '';
});
</script>

<template>
  <div class="space-y-5">
    <div>
      <div class="mb-3 font-medium">低代码应用授权套餐</div>
      <Space wrap>
        <Select v-model:value="applicationResourceCode" :options="applicationOptions" placeholder="选择已发布应用" style="width: 340px" />
        <Select v-model:value="preset" :options="presetOptions" style="width: 140px" />
        <Button :loading="ctx.saving.value" @click="dryRun">预览差异</Button>
        <Button type="primary" :disabled="!preview || preview.applied || !ctx.canCreate.value" :loading="ctx.saving.value" @click="applyBundle">原子应用</Button>
      </Space>
      <ClientPaginatedTable v-if="preview" class="mt-3" :columns="changeColumns" :data-source="preview.changes" :page-size="10" :row-key="(record: any) => `${record.resourceCode}:${record.actionCode}`">
          <template #bodyCell="{ column, record }: any">
            <Tag v-if="column.key === 'state' || column.dataIndex === 'state'" :color="record.state === 'ADD' ? 'processing' : 'default'">{{ record.state === 'ADD' ? '新增' : '已存在' }}</Tag>
          </template>
      </ClientPaginatedTable>
    </div>

    <div>
      <div class="mb-3 flex items-center justify-between">
        <div><div class="font-medium">发布授权链路诊断</div><div class="text-muted-foreground text-sm">只有当前快照收到授权中心确认后才会进入运行态。</div></div>
        <Tooltip title="刷新">
          <Button shape="circle" @click="loadPublications">
            <IconifyIcon :icon="ERP_TOOLBAR_ICONS.refresh" class="size-4" />
          </Button>
        </Tooltip>
      </div>
      <Alert v-if="publicationsError" class="mb-3" type="warning" show-icon :message="publicationsError" />
      <ClientPaginatedTable :columns="publicationColumns" :data-source="publications" :page-size="10" row-key="eventId">
          <template #bodyCell="{ column, record }: any">
            <Tag v-if="column.dataIndex === 'status'" :color="record.status === 'ACKNOWLEDGED' ? 'success' : record.status === 'FAILED' ? 'error' : 'processing'">{{ record.status }}</Tag>
            <Space v-if="column.key === 'actions'">
              <Button size="small" :disabled="record.status === 'ACKNOWLEDGED'" @click="retry(record.eventId)">重试</Button>
              <Button size="small" @click="reconcile(record.eventId)">对账</Button>
            </Space>
          </template>
      </ClientPaginatedTable>
    </div>
  </div>
</template>

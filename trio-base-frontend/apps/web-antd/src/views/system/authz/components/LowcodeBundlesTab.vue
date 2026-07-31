<script setup lang="ts">
import type { TableProps } from 'ant-design-vue';
import type { SystemAuthorizationApi } from '#/api';

import { computed, inject, onMounted, ref } from 'vue';
import { Button, message, Select, Space, Table, Tag } from 'ant-design-vue';

import {
  applyLowcodeAuthorizationBundle,
  getLowcodeAuthorizationPublications,
  previewLowcodeAuthorizationBundle,
  reconcileLowcodeAuthorizationPublication,
  retryLowcodeAuthorizationPublication,
} from '#/api';
import { CompactTableFrame } from '#/shared';

const ctx = inject<any>('authzContext')!;
const applicationResourceCode = ref('');
const preset = ref<SystemAuthorizationApi.LowcodeAuthorizationBundleRequest['preset']>('APPLICANT');
const preview = ref<SystemAuthorizationApi.LowcodeAuthorizationBundleResult>();
const publications = ref<SystemAuthorizationApi.LowcodeAuthorizationPublication[]>([]);
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
const pagination = { pageSize: 10, showSizeChanger: true, showTotal: (total: number) => `共 ${total} 条` };

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
  publications.value = await getLowcodeAuthorizationPublications();
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
      <CompactTableFrame v-if="preview" class="mt-3">
        <Table :columns="changeColumns" :data-source="preview.changes" :pagination="pagination" :row-key="(record: any) => `${record.resourceCode}:${record.actionCode}`" size="small" bordered>
          <template #bodyCell="{ column, record }: any">
            <Tag v-if="column.key === 'state' || column.dataIndex === 'state'" :color="record.state === 'ADD' ? 'processing' : 'default'">{{ record.state === 'ADD' ? '新增' : '已存在' }}</Tag>
          </template>
        </Table>
      </CompactTableFrame>
    </div>

    <div>
      <div class="mb-3 flex items-center justify-between">
        <div><div class="font-medium">发布授权链路诊断</div><div class="text-muted-foreground text-sm">只有当前快照收到授权中心确认后才会进入运行态。</div></div>
        <Button @click="loadPublications">刷新诊断</Button>
      </div>
      <CompactTableFrame>
        <Table :columns="publicationColumns" :data-source="publications" :pagination="pagination" row-key="eventId" size="small" bordered>
          <template #bodyCell="{ column, record }: any">
            <Tag v-if="column.dataIndex === 'status'" :color="record.status === 'ACKNOWLEDGED' ? 'success' : record.status === 'FAILED' ? 'error' : 'processing'">{{ record.status }}</Tag>
            <Space v-if="column.key === 'actions'">
              <Button size="small" :disabled="record.status === 'ACKNOWLEDGED'" @click="retry(record.eventId)">重试</Button>
              <Button size="small" @click="reconcile(record.eventId)">对账</Button>
            </Space>
          </template>
        </Table>
      </CompactTableFrame>
    </div>
  </div>
</template>

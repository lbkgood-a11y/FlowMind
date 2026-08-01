<script setup lang="ts">
import type { TableProps } from 'ant-design-vue';
import type { SystemAuthorizationApi } from '#/api';

import { inject, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Button, message, Space, Tag, Tooltip } from 'ant-design-vue';

import {
  getAuthorizationCompatibilityDashboard,
  getAuthorizationManagementMode,
  updateAuthorizationManagementMode,
} from '#/api';
import { ERP_TOOLBAR_ICONS } from '#/constants/erp-toolbar';
import { ClientPaginatedTable } from '#/shared';

const ctx = inject<any>('authzContext')!;

const compatibilityDashboard = ref<SystemAuthorizationApi.AuthorizationCompatibilityDashboard>();
const managementMode = ref<SystemAuthorizationApi.AuthorizationManagementMode>();

const acceptanceColumns: TableProps['columns'] = [
  { title: '角色', dataIndex: 'roleName', key: 'roleName' },
  { title: '验收状态', dataIndex: 'status', key: 'status', width: 150 },
  { title: '缺少已发布权限', dataIndex: 'missingProjectionCount', key: 'missingProjectionCount', width: 150 },
  { title: '版本外权限', dataIndex: 'unintendedExpansionCount', key: 'unintendedExpansionCount', width: 130 },
];

async function loadCompatibilityDashboard() {
  if (!ctx.canQuery.value) return;
  ctx.loading.value = true;
  try {
    const [dashboard, mode] = await Promise.all([
      getAuthorizationCompatibilityDashboard(),
      getAuthorizationManagementMode(),
    ]);
    compatibilityDashboard.value = dashboard;
    managementMode.value = mode;
  } finally {
    ctx.loading.value = false;
  }
}

async function enablePageCapabilityMode() {
  if (!compatibilityDashboard.value?.cutoverReady) return;
  ctx.saving.value = true;
  try {
    managementMode.value = await updateAuthorizationManagementMode('PAGE_CAPABILITY');
    message.success('已切换为页面功能授权；旧授权入口已停止写入');
    await loadCompatibilityDashboard();
  } catch {
    message.error('切换失败，请刷新验收结果并处理阻断项');
  } finally {
    ctx.saving.value = false;
  }
}
</script>

<template>
  <div>
    <div class="mb-3 flex items-center justify-between">
      <div>
        <div class="font-medium">页面功能授权生产验收</div>
        <div class="text-muted-foreground text-sm">
          所有结果均根据当前目录、已发布版本和运行时权限实时计算，不能手工勾选通过。
        </div>
      </div>
      <Space>
        <Tag color="blue">当前模式：{{ managementMode?.managementMode || '-' }}</Tag>
        <Tooltip title="刷新">
          <Button shape="circle" @click="loadCompatibilityDashboard">
            <IconifyIcon :icon="ERP_TOOLBAR_ICONS.refresh" class="size-4" />
          </Button>
        </Tooltip>
        <Button
          v-if="ctx.canUpdate.value && managementMode?.managementMode !== 'PAGE_CAPABILITY'"
          type="primary"
          :disabled="!compatibilityDashboard?.cutoverReady"
          :loading="ctx.saving.value"
          @click="enablePageCapabilityMode"
        >
          验收通过并切换
        </Button>
      </Space>
    </div>

    <div v-if="compatibilityDashboard" class="space-y-4">
      <div class="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
        <div class="rounded border p-3">
          <div class="text-muted-foreground text-xs">目录就绪</div>
          <div class="mt-1 text-lg font-semibold">
            {{ compatibilityDashboard.catalogReadyCount }}/{{ compatibilityDashboard.catalogCapabilityCount }}
          </div>
        </div>
        <div class="rounded border p-3">
          <div class="text-muted-foreground text-xs">角色已发布</div>
          <div class="mt-1 text-lg font-semibold">
            {{ compatibilityDashboard.publishedRoleCount }}/{{ compatibilityDashboard.totalRoleCount }}
          </div>
        </div>
        <div class="rounded border p-3">
          <div class="text-muted-foreground text-xs">决策一致角色</div>
          <div class="mt-1 text-lg font-semibold">{{ compatibilityDashboard.decisionEquivalentRoleCount }}</div>
        </div>
        <div class="rounded border p-3">
          <div class="text-muted-foreground text-xs">版本外权限</div>
          <div class="mt-1 text-lg font-semibold">{{ compatibilityDashboard.unintendedExpansionCount }}</div>
        </div>
        <div class="rounded border p-3">
          <div class="text-muted-foreground text-xs">开放漂移</div>
          <div class="mt-1 text-lg font-semibold">{{ compatibilityDashboard.openDriftCount }}</div>
        </div>
        <div class="rounded border p-3">
          <div class="text-muted-foreground text-xs">发布失败 / 回滚</div>
          <div class="mt-1 text-lg font-semibold">
            {{ compatibilityDashboard.publicationFailureCount }} / {{ compatibilityDashboard.rollbackCount }}
          </div>
        </div>
      </div>

      <div
        class="rounded border p-3 text-sm"
        :class="compatibilityDashboard.cutoverReady ? 'border-green-300 bg-green-50 text-green-800' : 'border-orange-300 bg-orange-50 text-orange-900'"
      >
        <template v-if="compatibilityDashboard.cutoverReady">
          已通过上线门禁：目录映射、角色迁移、运行时决策、扩权复核和漂移检查均无阻断项。
        </template>
        <template v-else>
          <div class="mb-1 font-medium">切换被阻止，请先处理：</div>
          <ul class="list-disc space-y-1 pl-5">
            <li v-for="blocker in compatibilityDashboard.blockers" :key="blocker">{{ blocker }}</li>
          </ul>
        </template>
      </div>

      <ClientPaginatedTable
          :columns="acceptanceColumns"
          :data-source="compatibilityDashboard.roleStatuses"
          row-key="roleId"
        >
          <template #bodyCell="{ column, record }: any">
            <template v-if="column.key === 'status'">
              <Tag :color="record.status === 'EQUIVALENT' ? 'success' : record.status === 'MISMATCH' ? 'error' : 'warning'">
                {{ record.status === 'EQUIVALENT' ? '决策一致' : record.status === 'MISMATCH' ? '运行时不一致' : '待迁移发布' }}
              </Tag>
            </template>
          </template>
      </ClientPaginatedTable>
    </div>
  </div>
</template>

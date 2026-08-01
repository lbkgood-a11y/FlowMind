<script setup lang="ts">
import type { TableProps } from 'ant-design-vue';
import type { SystemAuthorizationApi } from '#/api';

import { inject, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Button, Space, Tag, Tooltip } from 'ant-design-vue';

import { getPageCapabilityDiagnostics } from '#/api';
import { ERP_TOOLBAR_ICONS } from '#/constants/erp-toolbar';
import { ClientPaginatedTable } from '#/shared';

const ctx = inject<any>('authzContext')!;

const capabilityDiagnostics = ref<SystemAuthorizationApi.PageCapabilityDiagnostic[]>([]);

const diagnosticColumns: TableProps['columns'] = [
  { title: '页面', dataIndex: 'pageCode', key: 'pageCode', width: 180 },
  { title: '页面功能', dataIndex: 'capabilityCode', key: 'capabilityCode', width: 220 },
  { title: '就绪状态', dataIndex: 'readiness', key: 'readiness', width: 110 },
  { title: '后台连接', key: 'targets', width: 380 },
  { title: '依赖功能', key: 'dependencies', width: 240 },
  { title: '说明', dataIndex: 'readinessMessage', key: 'readinessMessage', width: 260 },
];

async function loadCapabilityDiagnostics() {
  if (!ctx.canQuery.value) return;
  ctx.loading.value = true;
  try {
    capabilityDiagnostics.value = await getPageCapabilityDiagnostics();
  } finally {
    ctx.loading.value = false;
  }
}
</script>

<template>
  <div>
    <div class="mb-3 flex items-center justify-between">
      <span class="text-muted-foreground text-sm">
        仅供平台管理员排查页面功能与后台权限连接，角色实施人员无需理解这些代码
      </span>
      <Tooltip title="刷新">
        <Button shape="circle" @click="loadCapabilityDiagnostics">
          <IconifyIcon :icon="ERP_TOOLBAR_ICONS.refresh" class="size-4" />
        </Button>
      </Tooltip>
    </div>
    <ClientPaginatedTable
        :columns="diagnosticColumns"
        :data-source="capabilityDiagnostics"
        :loading="ctx.loading.value"
        row-key="capabilityId"
      >
        <template #bodyCell="{ column, record }: any">
          <template v-if="column.key === 'readiness'">
            <Tag :color="record.readiness === 'READY' ? 'success' : record.readiness === 'PARTIAL' ? 'warning' : 'error'">
              {{ record.readiness }}
            </Tag>
          </template>
          <template v-else-if="column.key === 'targets'">
            <Space wrap :size="4">
              <Tag v-for="target in record.targets" :key="`${target.resourceCode}:${target.actionCode}`" :color="target.active ? 'blue' : 'error'">
                {{ target.resourceCode }} / {{ target.actionCode }}
              </Tag>
            </Space>
          </template>
          <template v-else-if="column.key === 'dependencies'">
            <Space wrap :size="4">
              <Tag v-for="dependency in record.requiredCapabilityCodes" :key="dependency">{{ dependency }}</Tag>
            </Space>
          </template>
        </template>
    </ClientPaginatedTable>
  </div>
</template>

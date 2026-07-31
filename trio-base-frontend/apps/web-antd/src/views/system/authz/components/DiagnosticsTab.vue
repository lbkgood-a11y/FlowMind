<script setup lang="ts">
import type { TableProps } from 'ant-design-vue';
import type { SystemAuthorizationApi } from '#/api';

import { inject, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Button, Space, Table, Tag } from 'ant-design-vue';

import { getPageCapabilityDiagnostics } from '#/api';
import { CompactTableFrame } from '#/shared';

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

const clientPagination: TableProps['pagination'] = {
  pageSize: 20,
  showQuickJumper: true,
  showSizeChanger: true,
  showTotal: (total, range) => `共 ${total} 条记录，本页 ${range[0]}-${range[1]} 条`,
};

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
      <Button class="!h-8 !w-8" @click="loadCapabilityDiagnostics">
        <IconifyIcon icon="lucide:refresh-cw" />
      </Button>
    </div>
    <CompactTableFrame>
      <Table
        :columns="diagnosticColumns"
        :data-source="capabilityDiagnostics"
        :loading="ctx.loading.value"
        :pagination="clientPagination"
        row-key="capabilityId"
        size="small"
        bordered
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
      </Table>
    </CompactTableFrame>
  </div>
</template>

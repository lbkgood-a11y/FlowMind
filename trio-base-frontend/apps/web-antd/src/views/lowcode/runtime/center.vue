<script setup lang="ts">
import type { LowcodeApi } from '#/api/lowcode';
import type { TableProps } from 'ant-design-vue';

import { h, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  Button,
  Pagination,
  Space,
  Table,
  Tag,
  Tooltip,
} from 'ant-design-vue';

import { getRuntimeApplicationList } from '#/api/lowcode';
import {
  BusinessPageScaffold,
  CompactTableFrame,
  CompactToolbar,
} from '#/shared';

const router = useRouter();
const loading = ref(false);
const records = ref<LowcodeApi.RuntimeApplicationSummary[]>([]);

const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
});

const columns: TableProps['columns'] = [
  { dataIndex: 'name', key: 'name', title: '应用名称', width: 180 },
  { dataIndex: 'appKey', key: 'appKey', title: '应用标识', width: 200 },
  { align: 'center', dataIndex: 'version', key: 'version', title: '版本', width: 80 },
  { dataIndex: 'formKey', key: 'formKey', title: '主表单', width: 160 },
  {
    dataIndex: 'schemaHash',
    ellipsis: true,
    key: 'schemaHash',
    title: 'Schema Hash',
    width: 160,
    customRender: ({ text }: { text?: string }) => text?.slice(0, 12) || '-',
  },
  {
    align: 'center',
    key: 'status',
    title: '状态',
    width: 100,
    customRender: () => h(Tag, { color: 'success' }, () => '已发布'),
  },
  { align: 'center', dataIndex: 'publishedAt', key: 'publishedAt', title: '发布时间', width: 180 },
  { align: 'center', fixed: 'right', key: 'action', title: '操作', width: 120 },
];

async function loadRecords(page = pagination.current) {
  loading.value = true;
  try {
    const result = await getRuntimeApplicationList({
      page,
      size: pagination.pageSize,
    });
    records.value = result.items;
    pagination.current = page;
    pagination.total = result.total;
  } finally {
    loading.value = false;
  }
}

function openApp(record: LowcodeApi.RuntimeApplicationSummary) {
  void router.push({
    name: 'LowcodeRuntimeApp',
    params: { appKey: record.appKey },
    query: { version: record.version },
  });
}

function onPageChange(page: number, pageSize: number) {
  pagination.pageSize = pageSize;
  loadRecords(page);
}

onMounted(() => loadRecords(1));
</script>

<template>
  <Page auto-content-height>
    <BusinessPageScaffold class="runtime-page" pattern="single-table">
      <template #toolbar>
        <CompactToolbar title="应用中心" subtitle="仅显示当前租户已发布且具备访问权限的快速应用">
          <Tooltip title="刷新">
            <Button shape="circle" @click="loadRecords()">
              <span class="text-lg">↻</span>
            </Button>
          </Tooltip>
        </CompactToolbar>
      </template>

      <CompactTableFrame>
          <Table
            :columns="columns"
            :data-source="records"
            :loading="loading"
            :pagination="false"
            :scroll="{ x: 1180 }"
            bordered
            row-key="versionId"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'action'">
                <Space>
                  <Button
                    size="small"
                    type="link"
                    @click="openApp(record as LowcodeApi.RuntimeApplicationSummary)"
                  >
                    打开
                  </Button>
                </Space>
              </template>
            </template>
          </Table>

        <template #footer>
          <div class="table-total">共 {{ pagination.total }} 个应用</div>
          <Pagination
            v-model:current="pagination.current"
            v-model:page-size="pagination.pageSize"
            :page-size-options="['10', '20', '50']"
            :total="pagination.total"
            show-less-items
            show-size-changer
            size="small"
            @change="onPageChange"
            @show-size-change="onPageChange"
          />
        </template>
      </CompactTableFrame>
    </BusinessPageScaffold>
  </Page>
</template>

<style scoped>
.runtime-page,
.list-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.list-panel {
  min-height: 0;
  padding: 8px;
  overflow: hidden;
  background: #fff;
  border-radius: 4px;
}

.list-header {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 8px;
}

.list-header h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #111827;
}

.list-header p {
  margin: 2px 0 0;
  font-size: 12px;
  color: #64748b;
}

.table-frame {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
}

.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 10px;
}

.table-total {
  font-size: 13px;
  color: #606b7b;
}
</style>

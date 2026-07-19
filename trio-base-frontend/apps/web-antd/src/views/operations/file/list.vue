<script setup lang="ts">
import type { OperationsApi } from '#/api';
import type { TableProps } from 'ant-design-vue';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { IconifyIcon, Plus } from '@vben/icons';

import {
  Button,
  Input,
  message,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Upload,
} from 'ant-design-vue';

import {
  deleteFile,
  getFileDownloadUrl,
  getFilePage,
  updateFileStatus,
  uploadFile,
} from '#/api';
import { ERP_TOOLBAR_ICONS } from '#/constants/erp-toolbar';
import {
  BusinessPageScaffold,
  CompactQueryBar,
  CompactTableFrame,
  CompactToolbar,
} from '#/shared';

const PERMISSIONS = {
  delete: '/api/v1/files/*:DELETE',
  query: '/api/v1/files:GET',
  status: '/api/v1/files/*/status:PUT',
  upload: '/api/v1/files:POST',
} as const;

const { hasAccessByCodes } = useAccess();
const canQuery = computed(() => hasAccessByCodes([PERMISSIONS.query]));
const canUpload = computed(() => hasAccessByCodes([PERMISSIONS.upload]));
const canUpdateStatus = computed(() => hasAccessByCodes([PERMISSIONS.status]));
const canDelete = computed(() => hasAccessByCodes([PERMISSIONS.delete]));

const loading = ref(false);
const uploading = ref(false);
const records = ref<OperationsApi.OpsFile[]>([]);

const query = reactive({
  keyword: '',
  status: undefined as 0 | 1 | undefined,
});

const pagination = reactive({
  current: 1,
  pageSize: 20,
  size: 'small' as const,
  showSizeChanger: true,
  total: 0,
});

const columns = computed<TableProps['columns']>(() => [
  { dataIndex: 'originalName', fixed: 'left', key: 'originalName', title: '文件名', width: 260 },
  { dataIndex: 'contentType', key: 'contentType', title: '类型', width: 180 },
  { dataIndex: 'fileSize', key: 'fileSize', title: '大小', width: 110 },
  { dataIndex: 'downloadCount', key: 'downloadCount', title: '下载次数', width: 100 },
  { dataIndex: 'status', key: 'status', title: '状态', width: 100 },
  { dataIndex: 'updatedAt', key: 'updatedAt', title: '更新时间', width: 180 },
  { fixed: 'right', key: 'action', title: '操作', width: 250 },
]);

async function load() {
  if (!canQuery.value) {
    records.value = [];
    pagination.total = 0;
    return;
  }
  loading.value = true;
  try {
    const result = await getFilePage({
      keyword: query.keyword || undefined,
      page: pagination.current,
      size: pagination.pageSize,
      status: query.status,
    });
    records.value = result.records;
    pagination.total = result.total;
  } finally {
    loading.value = false;
  }
}

function resetQuery() {
  query.keyword = '';
  query.status = undefined;
  pagination.current = 1;
  load();
}

async function beforeUpload(file: File) {
  uploading.value = true;
  try {
    await uploadFile(file);
    message.success('文件已上传');
    await load();
  } finally {
    uploading.value = false;
  }
  return false;
}

async function toggleStatus(record: OperationsApi.OpsFile) {
  await updateFileStatus(record.id, record.status === 1 ? 0 : 1);
  message.success(record.status === 1 ? '文件已禁用' : '文件已启用');
  await load();
}

async function remove(record: OperationsApi.OpsFile) {
  await deleteFile(record.id);
  message.success('文件已删除');
  await load();
}

function handleTableChange(next: TableProps['pagination']) {
  if (next && typeof next === 'object') {
    pagination.current = next.current ?? 1;
    pagination.pageSize = next.pageSize ?? 20;
    load();
  }
}

function formatFileSize(size?: number) {
  if (!size) {
    return '0 B';
  }
  const units = ['B', 'KB', 'MB', 'GB'];
  let value = size;
  let unitIndex = 0;
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex += 1;
  }
  return `${value.toFixed(value >= 10 || unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
}

function asFile(record: Record<string, any>) {
  return record as OperationsApi.OpsFile;
}

onMounted(load);
</script>

<template>
  <Page auto-content-height>
    <BusinessPageScaffold class="ops-page" pattern="single-table">
      <template #query>
        <CompactQueryBar :columns="3">
          <Input v-model:value="query.keyword" allow-clear class="query-input" placeholder="文件名/扩展名" />
          <Select
            v-model:value="query.status"
            allow-clear
            class="query-select"
            :options="[
              { label: '启用', value: 1 },
              { label: '禁用', value: 0 },
            ]"
            placeholder="状态"
          />
          <template #actions>
            <Button v-if="canQuery" type="primary" @click="load">查询</Button>
            <Button v-if="canQuery" @click="resetQuery">重置</Button>
            <Tooltip v-if="canQuery" title="刷新">
              <Button shape="circle" @click="load">
                <IconifyIcon :icon="ERP_TOOLBAR_ICONS.refresh" class="size-4" />
              </Button>
            </Tooltip>
            <Upload v-if="canUpload" :before-upload="beforeUpload" :show-upload-list="false">
              <Button :loading="uploading" type="primary">
                <Plus class="size-4" />
                上传文件
              </Button>
            </Upload>
          </template>
        </CompactQueryBar>
      </template>

      <template #toolbar>
        <CompactToolbar title="文件管理" subtitle="上传、下载和维护平台文件状态" />
      </template>

      <CompactTableFrame>
        <Table
          row-key="id"
          :columns="columns"
          :data-source="records"
          :loading="loading"
          :pagination="pagination"
          :scroll="{ x: 1180 }"
          size="small"
          :sticky="{ offsetScroll: 0 }"
          @change="handleTableChange"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'fileSize'">
              {{ formatFileSize(record.fileSize) }}
            </template>
            <template v-else-if="column.key === 'status'">
              <Tag :color="record.status === 1 ? 'green' : 'default'">
                {{ record.status === 1 ? '启用' : '禁用' }}
              </Tag>
            </template>
            <template v-else-if="column.key === 'action'">
              <Space>
                <Button :href="getFileDownloadUrl(record.id)" target="_blank" type="link" size="small">
                  下载
                </Button>
                <Button v-if="canUpdateStatus" type="link" size="small" @click="toggleStatus(asFile(record))">
                  {{ record.status === 1 ? '禁用' : '启用' }}
                </Button>
                <Popconfirm v-if="canDelete" title="确认删除该文件？" @confirm="remove(asFile(record))">
                  <Button danger type="link" size="small">删除</Button>
                </Popconfirm>
              </Space>
            </template>
          </template>
        </Table>
      </CompactTableFrame>
    </BusinessPageScaffold>
  </Page>
</template>

<style scoped>
.ops-page {
  display: flex;
  min-height: 100%;
  flex-direction: column;
  gap: 8px;
}

.toolbar {
  width: 100%;
}

.query-input {
  width: 220px;
}

.query-select {
  width: 140px;
}
</style>

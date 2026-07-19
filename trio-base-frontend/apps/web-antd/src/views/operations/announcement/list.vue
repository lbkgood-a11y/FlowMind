<script setup lang="ts">
import type { OperationsApi } from '#/api';
import type { TableProps } from 'ant-design-vue';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { IconifyIcon, Plus } from '@vben/icons';

import {
  Button,
  FormItem,
  Input,
  message,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
} from 'ant-design-vue';

import {
  createAnnouncement,
  deleteAnnouncement,
  getAnnouncementPage,
  publishAnnouncement,
  unpublishAnnouncement,
  updateAnnouncement,
} from '#/api';
import { ERP_TOOLBAR_ICONS } from '#/constants/erp-toolbar';
import {
  BusinessPageScaffold,
  CompactQueryBar,
  CompactTableFrame,
  CompactToolbar,
} from '#/shared';

const Textarea = Input.TextArea;

const PERMISSIONS = {
  create: '/api/v1/announcements:POST',
  delete: '/api/v1/announcements/*:DELETE',
  publish: '/api/v1/announcements/*/publish:POST',
  query: '/api/v1/announcements:GET',
  unpublish: '/api/v1/announcements/*/unpublish:POST',
  update: '/api/v1/announcements/*:PUT',
} as const;

const { hasAccessByCodes } = useAccess();
const canQuery = computed(() => hasAccessByCodes([PERMISSIONS.query]));
const canCreate = computed(() => hasAccessByCodes([PERMISSIONS.create]));
const canUpdate = computed(() => hasAccessByCodes([PERMISSIONS.update]));
const canDelete = computed(() => hasAccessByCodes([PERMISSIONS.delete]));
const canPublish = computed(() => hasAccessByCodes([PERMISSIONS.publish]));
const canUnpublish = computed(() => hasAccessByCodes([PERMISSIONS.unpublish]));

const loading = ref(false);
const saving = ref(false);
const modalOpen = ref(false);
const editing = ref<OperationsApi.Announcement>();
const records = ref<OperationsApi.Announcement[]>([]);

const query = reactive({
  keyword: '',
  priority: undefined as string | undefined,
  status: undefined as string | undefined,
});

const form = reactive<OperationsApi.SaveAnnouncementParams>({
  content: '',
  priority: 'NORMAL',
  targetOrgIds: '',
  targetType: 'ALL',
  targetUserIds: '',
  title: '',
});

const pagination = reactive({
  current: 1,
  pageSize: 20,
  size: 'small' as const,
  showSizeChanger: true,
  total: 0,
});

const columns = computed<TableProps['columns']>(() => [
  { dataIndex: 'title', fixed: 'left', key: 'title', title: '标题', width: 220 },
  { dataIndex: 'priority', key: 'priority', title: '优先级', width: 100 },
  { dataIndex: 'status', key: 'status', title: '状态', width: 110 },
  { dataIndex: 'targetType', key: 'targetType', title: '目标范围', width: 110 },
  { dataIndex: 'publishAt', key: 'publishAt', title: '发布时间', width: 180 },
  { dataIndex: 'updatedAt', key: 'updatedAt', title: '更新时间', width: 180 },
  { fixed: 'right', key: 'action', title: '操作', width: 260 },
]);

async function load() {
  if (!canQuery.value) {
    records.value = [];
    pagination.total = 0;
    return;
  }
  loading.value = true;
  try {
    const result = await getAnnouncementPage({
      keyword: query.keyword || undefined,
      page: pagination.current,
      priority: query.priority,
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
  query.priority = undefined;
  query.status = undefined;
  pagination.current = 1;
  load();
}

function openCreate() {
  editing.value = undefined;
  form.title = '';
  form.content = '';
  form.priority = 'NORMAL';
  form.targetType = 'ALL';
  form.targetOrgIds = '';
  form.targetUserIds = '';
  modalOpen.value = true;
}

function openEdit(record: OperationsApi.Announcement) {
  editing.value = record;
  form.title = record.title;
  form.content = record.content;
  form.priority = record.priority || 'NORMAL';
  form.targetType = record.targetType || 'ALL';
  form.targetOrgIds = record.targetOrgIds || '';
  form.targetUserIds = record.targetUserIds || '';
  modalOpen.value = true;
}

async function submit() {
  if (!form.title.trim() || !form.content.trim()) {
    message.warning('请输入公告标题和内容');
    return;
  }
  saving.value = true;
  try {
    const payload = {
      content: form.content.trim(),
      priority: form.priority,
      targetOrgIds: form.targetOrgIds?.trim() || undefined,
      targetType: form.targetType,
      targetUserIds: form.targetUserIds?.trim() || undefined,
      title: form.title.trim(),
    };
    if (editing.value) {
      await updateAnnouncement(editing.value.id, payload);
      message.success('公告已更新');
    } else {
      await createAnnouncement(payload);
      message.success('公告已创建');
    }
    modalOpen.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function publish(record: OperationsApi.Announcement) {
  await publishAnnouncement(record.id);
  message.success('公告已发布');
  await load();
}

async function unpublish(record: OperationsApi.Announcement) {
  await unpublishAnnouncement(record.id);
  message.success('公告已下线');
  await load();
}

async function remove(record: OperationsApi.Announcement) {
  await deleteAnnouncement(record.id);
  message.success('公告已删除');
  await load();
}

function handleTableChange(next: TableProps['pagination']) {
  if (next && typeof next === 'object') {
    pagination.current = next.current ?? 1;
    pagination.pageSize = next.pageSize ?? 20;
    load();
  }
}

function statusColor(status?: string) {
  if (status === 'PUBLISHED') return 'green';
  if (status === 'OFFLINE') return 'default';
  return 'blue';
}

function statusLabel(status?: string) {
  return { DRAFT: '草稿', OFFLINE: '已下线', PUBLISHED: '已发布' }[status || ''] || status || '-';
}

function asAnnouncement(record: Record<string, any>) {
  return record as OperationsApi.Announcement;
}

onMounted(load);
</script>

<template>
  <Page auto-content-height>
    <BusinessPageScaffold class="ops-page" pattern="single-table">
      <template #query>
        <CompactQueryBar :columns="3">
          <Input v-model:value="query.keyword" class="query-input" placeholder="标题/内容" allow-clear />
          <Select
            v-model:value="query.status"
            allow-clear
            class="query-select"
            :options="[
              { label: '草稿', value: 'DRAFT' },
              { label: '已发布', value: 'PUBLISHED' },
              { label: '已下线', value: 'OFFLINE' },
            ]"
            placeholder="状态"
          />
          <Select
            v-model:value="query.priority"
            allow-clear
            class="query-select"
            :options="[
              { label: '普通', value: 'NORMAL' },
              { label: '重要', value: 'IMPORTANT' },
              { label: '紧急', value: 'URGENT' },
            ]"
            placeholder="优先级"
          />
          <template #actions>
          <Button v-if="canQuery" type="primary" @click="load">查询</Button>
          <Button v-if="canQuery" @click="resetQuery">重置</Button>
          <Tooltip v-if="canQuery" title="刷新">
            <Button shape="circle" @click="load">
              <IconifyIcon :icon="ERP_TOOLBAR_ICONS.refresh" class="size-4" />
            </Button>
          </Tooltip>
          <Button v-if="canCreate" type="primary" @click="openCreate">
            <Plus class="size-4" />
            新增公告
          </Button>
          </template>
        </CompactQueryBar>
      </template>

      <template #toolbar>
        <CompactToolbar title="公告管理" subtitle="维护公告草稿、发布和下线状态" />
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
          <template v-if="column.key === 'status'">
            <Tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</Tag>
          </template>
          <template v-else-if="column.key === 'priority'">
            <Tag :color="record.priority === 'URGENT' ? 'red' : record.priority === 'IMPORTANT' ? 'orange' : 'blue'">
              {{ record.priority || 'NORMAL' }}
            </Tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <Space>
              <Button v-if="canUpdate" type="link" size="small" @click="openEdit(asAnnouncement(record))">
                编辑
              </Button>
              <Button
                v-if="canPublish && record.status !== 'PUBLISHED'"
                type="link"
                size="small"
                @click="publish(asAnnouncement(record))"
              >
                发布
              </Button>
              <Button
                v-if="canUnpublish && record.status === 'PUBLISHED'"
                type="link"
                size="small"
                @click="unpublish(asAnnouncement(record))"
              >
                下线
              </Button>
              <Popconfirm v-if="canDelete" title="确认删除该公告？" @confirm="remove(asAnnouncement(record))">
                <Button danger type="link" size="small">删除</Button>
              </Popconfirm>
            </Space>
          </template>
        </template>
      </Table>
      </CompactTableFrame>
    </BusinessPageScaffold>

    <Modal
      v-model:open="modalOpen"
      :confirm-loading="saving"
      :title="editing ? '编辑公告' : '新增公告'"
      ok-text="保存"
      width="760px"
      @ok="submit"
    >
      <div class="form-grid">
        <FormItem class="form-wide" label="标题" required>
          <Input v-model:value="form.title" placeholder="请输入公告标题" />
        </FormItem>
        <FormItem label="优先级">
          <Select
            v-model:value="form.priority"
            :options="[
              { label: '普通', value: 'NORMAL' },
              { label: '重要', value: 'IMPORTANT' },
              { label: '紧急', value: 'URGENT' },
            ]"
          />
        </FormItem>
        <FormItem label="目标范围">
          <Select
            v-model:value="form.targetType"
            :options="[
              { label: '全部用户', value: 'ALL' },
              { label: '指定组织', value: 'ORG' },
              { label: '指定用户', value: 'USER' },
            ]"
          />
        </FormItem>
        <FormItem v-if="form.targetType === 'ORG'" class="form-wide" label="组织ID">
          <Input v-model:value="form.targetOrgIds" placeholder="多个组织ID用英文逗号分隔" />
        </FormItem>
        <FormItem v-if="form.targetType === 'USER'" class="form-wide" label="用户ID">
          <Input v-model:value="form.targetUserIds" placeholder="多个用户ID用英文逗号分隔" />
        </FormItem>
        <FormItem class="form-wide" label="内容" required>
          <Textarea v-model:value="form.content" :rows="6" placeholder="请输入公告内容" />
        </FormItem>
      </div>
    </Modal>
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

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
}

.form-wide {
  grid-column: 1 / -1;
}

@media (max-width: 900px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>

<script setup lang="ts">
import type { OperationsApi } from '#/api';
import type { TableProps } from 'ant-design-vue';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { Plus } from '@vben/icons';

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
  Tabs,
  Tag,
} from 'ant-design-vue';

import {
  deleteInboxMessage,
  deleteMessage,
  getInboxMessages,
  getMessagePage,
  markInboxMessageRead,
  sendMessage,
} from '#/api';

const Textarea = Input.TextArea;
const TabPane = Tabs.TabPane;

const PERMISSIONS = {
  delete: '/api/v1/messages/*:DELETE',
  query: '/api/v1/messages:GET',
  send: '/api/v1/messages:POST',
} as const;

const { hasAccessByCodes } = useAccess();
const canQuery = computed(() => hasAccessByCodes([PERMISSIONS.query]));
const canSend = computed(() => hasAccessByCodes([PERMISSIONS.send]));
const canDelete = computed(() => hasAccessByCodes([PERMISSIONS.delete]));

const activeTab = ref('admin');
const adminLoading = ref(false);
const inboxLoading = ref(false);
const sending = ref(false);
const sendOpen = ref(false);
const adminRecords = ref<OperationsApi.MessageAdminResponse[]>([]);
const inboxRecords = ref<OperationsApi.MessageInboxResponse[]>([]);

const adminQuery = reactive({
  keyword: '',
  messageType: undefined as string | undefined,
});

const inboxQuery = reactive({
  readStatus: undefined as 0 | 1 | undefined,
});

const sendForm = reactive({
  content: '',
  messageType: 'SYSTEM',
  recipientUserIds: '',
  title: '',
});

const adminPagination = reactive({
  current: 1,
  pageSize: 20,
  size: 'small' as const,
  showSizeChanger: true,
  total: 0,
});

const inboxPagination = reactive({
  current: 1,
  pageSize: 20,
  size: 'small' as const,
  showSizeChanger: true,
  total: 0,
});

const adminColumns = computed<TableProps['columns']>(() => [
  { dataIndex: ['message', 'title'], fixed: 'left', key: 'title', title: '标题', width: 220 },
  { dataIndex: ['message', 'messageType'], key: 'messageType', title: '类型', width: 110 },
  { dataIndex: 'recipientCount', key: 'recipientCount', title: '接收人数', width: 100 },
  { dataIndex: 'readCount', key: 'readCount', title: '已读', width: 90 },
  { dataIndex: 'unreadCount', key: 'unreadCount', title: '未读', width: 90 },
  { dataIndex: ['message', 'createdAt'], key: 'createdAt', title: '发送时间', width: 180 },
  { fixed: 'right', key: 'action', title: '操作', width: 120 },
]);

const inboxColumns = computed<TableProps['columns']>(() => [
  { dataIndex: ['message', 'title'], fixed: 'left', key: 'title', title: '标题', width: 220 },
  { dataIndex: ['message', 'messageType'], key: 'messageType', title: '类型', width: 110 },
  { dataIndex: ['recipient', 'readStatus'], key: 'readStatus', title: '状态', width: 90 },
  { dataIndex: ['message', 'senderName'], key: 'senderName', title: '发送人', width: 140 },
  { dataIndex: ['message', 'createdAt'], key: 'createdAt', title: '发送时间', width: 180 },
  { fixed: 'right', key: 'action', title: '操作', width: 150 },
]);

async function loadAdmin() {
  if (!canQuery.value) {
    adminRecords.value = [];
    adminPagination.total = 0;
    return;
  }
  adminLoading.value = true;
  try {
    const result = await getMessagePage({
      keyword: adminQuery.keyword || undefined,
      messageType: adminQuery.messageType,
      page: adminPagination.current,
      size: adminPagination.pageSize,
    });
    adminRecords.value = result.records;
    adminPagination.total = result.total;
  } finally {
    adminLoading.value = false;
  }
}

async function loadInbox() {
  inboxLoading.value = true;
  try {
    const result = await getInboxMessages({
      page: inboxPagination.current,
      readStatus: inboxQuery.readStatus,
      size: inboxPagination.pageSize,
    });
    inboxRecords.value = result.records;
    inboxPagination.total = result.total;
  } finally {
    inboxLoading.value = false;
  }
}

function resetAdminQuery() {
  adminQuery.keyword = '';
  adminQuery.messageType = undefined;
  adminPagination.current = 1;
  loadAdmin();
}

function resetInboxQuery() {
  inboxQuery.readStatus = undefined;
  inboxPagination.current = 1;
  loadInbox();
}

function openSend() {
  sendForm.title = '';
  sendForm.content = '';
  sendForm.messageType = 'SYSTEM';
  sendForm.recipientUserIds = '';
  sendOpen.value = true;
}

async function submitSend() {
  const recipientUserIds = sendForm.recipientUserIds
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
  if (!sendForm.title.trim() || !sendForm.content.trim() || recipientUserIds.length === 0) {
    message.warning('请输入标题、内容和接收用户ID');
    return;
  }
  sending.value = true;
  try {
    await sendMessage({
      content: sendForm.content.trim(),
      messageType: sendForm.messageType,
      recipientUserIds,
      title: sendForm.title.trim(),
    });
    message.success('消息已发送');
    sendOpen.value = false;
    await loadAdmin();
  } finally {
    sending.value = false;
  }
}

async function removeAdmin(record: OperationsApi.MessageAdminResponse) {
  await deleteMessage(record.message.id);
  message.success('消息已删除');
  await loadAdmin();
}

async function markRead(record: OperationsApi.MessageInboxResponse) {
  await markInboxMessageRead(record.recipient.id);
  message.success('已标记为已读');
  await loadInbox();
}

async function removeInbox(record: OperationsApi.MessageInboxResponse) {
  await deleteInboxMessage(record.recipient.id);
  message.success('消息已删除');
  await loadInbox();
}

function handleAdminTableChange(next: TableProps['pagination']) {
  if (next && typeof next === 'object') {
    adminPagination.current = next.current ?? 1;
    adminPagination.pageSize = next.pageSize ?? 20;
    loadAdmin();
  }
}

function handleInboxTableChange(next: TableProps['pagination']) {
  if (next && typeof next === 'object') {
    inboxPagination.current = next.current ?? 1;
    inboxPagination.pageSize = next.pageSize ?? 20;
    loadInbox();
  }
}

function asAdmin(record: Record<string, any>) {
  return record as OperationsApi.MessageAdminResponse;
}

function asInbox(record: Record<string, any>) {
  return record as OperationsApi.MessageInboxResponse;
}

onMounted(() => {
  loadAdmin();
  loadInbox();
});
</script>

<template>
  <Page auto-content-height>
    <div class="erp-compact-page ops-page">
      <Tabs v-model:active-key="activeTab" size="small">
        <TabPane key="admin" tab="消息管理">
          <section class="toolbar">
            <Space wrap>
              <Input v-model:value="adminQuery.keyword" class="query-input" placeholder="标题/内容" allow-clear />
              <Select
                v-model:value="adminQuery.messageType"
                allow-clear
                class="query-select"
                :options="[
                  { label: '系统', value: 'SYSTEM' },
                  { label: '公告', value: 'ANNOUNCEMENT' },
                  { label: '任务', value: 'TASK' },
                ]"
                placeholder="类型"
              />
              <Button v-if="canQuery" type="primary" @click="loadAdmin">查询</Button>
              <Button v-if="canQuery" @click="resetAdminQuery">重置</Button>
              <Button v-if="canSend" type="primary" @click="openSend">
                <Plus class="size-4" />
                发送消息
              </Button>
            </Space>
          </section>

          <Table
            row-key="message.id"
            :columns="adminColumns"
            :data-source="adminRecords"
            :loading="adminLoading"
            :pagination="adminPagination"
            :scroll="{ x: 960 }"
            size="small"
            :sticky="{ offsetScroll: 0 }"
            @change="handleAdminTableChange"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'messageType'">
                <Tag color="blue">{{ record.message.messageType || 'SYSTEM' }}</Tag>
              </template>
              <template v-else-if="column.key === 'action'">
                <Popconfirm v-if="canDelete" title="确认删除该消息？" @confirm="removeAdmin(asAdmin(record))">
                  <Button danger type="link" size="small">删除</Button>
                </Popconfirm>
              </template>
            </template>
          </Table>
        </TabPane>

        <TabPane key="inbox" tab="我的收件箱">
          <section class="toolbar">
            <Space wrap>
              <Select
                v-model:value="inboxQuery.readStatus"
                allow-clear
                class="query-select"
                :options="[
                  { label: '未读', value: 0 },
                  { label: '已读', value: 1 },
                ]"
                placeholder="阅读状态"
              />
              <Button type="primary" @click="loadInbox">查询</Button>
              <Button @click="resetInboxQuery">重置</Button>
            </Space>
          </section>

          <Table
            row-key="recipient.id"
            :columns="inboxColumns"
            :data-source="inboxRecords"
            :loading="inboxLoading"
            :pagination="inboxPagination"
            :scroll="{ x: 960 }"
            size="small"
            :sticky="{ offsetScroll: 0 }"
            @change="handleInboxTableChange"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'messageType'">
                <Tag color="blue">{{ record.message.messageType || 'SYSTEM' }}</Tag>
              </template>
              <template v-else-if="column.key === 'readStatus'">
                <Tag :color="record.recipient.readStatus === 1 ? 'green' : 'orange'">
                  {{ record.recipient.readStatus === 1 ? '已读' : '未读' }}
                </Tag>
              </template>
              <template v-else-if="column.key === 'action'">
                <Space>
                  <Button
                    v-if="record.recipient.readStatus !== 1"
                    type="link"
                    size="small"
                    @click="markRead(asInbox(record))"
                  >
                    标记已读
                  </Button>
                  <Popconfirm title="确认删除该收件消息？" @confirm="removeInbox(asInbox(record))">
                    <Button danger type="link" size="small">删除</Button>
                  </Popconfirm>
                </Space>
              </template>
            </template>
          </Table>
        </TabPane>
      </Tabs>
    </div>

    <Modal
      v-model:open="sendOpen"
      :confirm-loading="sending"
      title="发送站内消息"
      ok-text="发送"
      width="720px"
      @ok="submitSend"
    >
      <div class="form-grid">
        <FormItem class="form-wide" label="标题" required>
          <Input v-model:value="sendForm.title" placeholder="请输入消息标题" />
        </FormItem>
        <FormItem label="类型">
          <Select
            v-model:value="sendForm.messageType"
            :options="[
              { label: '系统', value: 'SYSTEM' },
              { label: '公告', value: 'ANNOUNCEMENT' },
              { label: '任务', value: 'TASK' },
            ]"
          />
        </FormItem>
        <FormItem class="form-wide" label="接收用户" required>
          <Input v-model:value="sendForm.recipientUserIds" placeholder="多个用户ID用英文逗号分隔" />
        </FormItem>
        <FormItem class="form-wide" label="内容" required>
          <Textarea v-model:value="sendForm.content" :rows="5" placeholder="请输入消息内容" />
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
  margin-bottom: 8px;
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

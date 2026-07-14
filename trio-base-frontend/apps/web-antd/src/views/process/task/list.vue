<script setup lang="ts">
import type { TableProps } from 'ant-design-vue';

import { computed, h, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';

import {
  Button,
  message,
  Pagination,
  Space,
  Table,
  Tag,
  Tooltip,
} from 'ant-design-vue';

import { getMyCompletedTasks, getMyPendingTasks } from '#/api/process';
import type { ProcessApi } from '#/api/process';

import TaskActionDialog, {
  type TaskActionType,
} from '../components/TaskActionDialog.vue';

const PERMISSIONS = {
  approve: '/api/v1/tasks/*/approve:POST',
  addSign: '/api/v1/tasks/*/add-sign:POST',
  pending: '/api/v1/tasks/my-pending:GET',
  completed: '/api/v1/tasks/my-completed:GET',
  reject: '/api/v1/tasks/*/reject:POST',
  transfer: '/api/v1/tasks/*/transfer:POST',
} as const;

const { hasAccessByCodes } = useAccess();
const canApprove = computed(() => hasAccessByCodes([PERMISSIONS.approve]));
const canAddSign = computed(() => hasAccessByCodes([PERMISSIONS.addSign]));
const canQueryPending = computed(() => hasAccessByCodes([PERMISSIONS.pending]));
const canQueryCompleted = computed(() => hasAccessByCodes([PERMISSIONS.completed]));
const canReject = computed(() => hasAccessByCodes([PERMISSIONS.reject]));
const canTransfer = computed(() => hasAccessByCodes([PERMISSIONS.transfer]));

const activeTab = ref<'pending' | 'completed'>('pending');
const loading = ref(false);
const actionOpen = ref(false);
const actionTarget = ref<ProcessApi.TaskItem>();
const actionType = ref<TaskActionType>('APPROVE');

const pendingTasks = ref<ProcessApi.TaskItem[]>([]);
const completedTasks = ref<ProcessApi.TaskItem[]>([]);

const pendingPagination = reactive({ current: 1, pageSize: 20, total: 0 });
const completedPagination = reactive({ current: 1, pageSize: 20, total: 0 });

const columns: TableProps['columns'] = [
  { dataIndex: 'title', key: 'title', title: '任务标题', width: 200 },
  { dataIndex: 'processName', key: 'processName', title: '所属流程', width: 150 },
  { dataIndex: 'nodeName', key: 'nodeName', title: '当前节点', width: 120 },
  {
    dataIndex: 'nodeType', key: 'nodeType', title: '节点类型', width: 100, align: 'center',
    customRender: ({ text }: { text: string }) => {
      const map: Record<string, string> = { APPROVAL: '审批', COUNTERSIGN: '会签', NOTIFY: '抄送' };
      return h(Tag, {}, () => map[text] || text);
    },
  },
  { dataIndex: 'assigneeName', key: 'assigneeName', title: '处理人', width: 100, align: 'center' },
  {
    dataIndex: 'status', key: 'status', title: '状态', width: 100, align: 'center',
    customRender: ({ text }: { text: string }) => {
      const colorMap: Record<string, string> = { PENDING: 'processing', APPROVED: 'success', REJECTED: 'error', CANCELLED: 'default' };
      const labelMap: Record<string, string> = { PENDING: '待处理', APPROVED: '已通过', REJECTED: '已驳回', CANCELLED: '已取消' };
      return h(Tag, { color: colorMap[text] || 'default' }, () => labelMap[text] || text);
    },
  },
  { dataIndex: 'createdAt', key: 'createdAt', title: '创建时间', width: 180, align: 'center' },
  { dataIndex: 'completedAt', key: 'completedAt', title: '完成时间', width: 180, align: 'center' },
  { dataIndex: 'comment', key: 'comment', title: '审批意见', width: 150, ellipsis: true },
  { key: 'action', title: '操作', width: 160, align: 'center', fixed: 'right' },
];

async function loadPending(page = pendingPagination.current) {
  if (!canQueryPending.value) {
    pendingTasks.value = [];
    pendingPagination.total = 0;
    return;
  }
  loading.value = true;
  try {
    const result = await getMyPendingTasks({ page, size: pendingPagination.pageSize });
    pendingTasks.value = result.items;
    pendingPagination.current = page;
    pendingPagination.total = result.total;
  } finally {
    loading.value = false;
  }
}

async function loadCompleted(page = completedPagination.current) {
  if (!canQueryCompleted.value) {
    completedTasks.value = [];
    completedPagination.total = 0;
    return;
  }
  loading.value = true;
  try {
    const result = await getMyCompletedTasks({ page, size: completedPagination.pageSize });
    completedTasks.value = result.items;
    completedPagination.current = page;
    completedPagination.total = result.total;
  } finally {
    loading.value = false;
  }
}

function switchTab(tab: 'pending' | 'completed') {
  activeTab.value = tab;
  if (tab === 'pending') loadPending(1);
  else loadCompleted(1);
}

function openAction(task: ProcessApi.TaskItem, action: TaskActionType) {
  actionTarget.value = task;
  actionType.value = action;
  actionOpen.value = true;
}

async function handleActionSuccess(action: TaskActionType) {
  const labels: Record<TaskActionType, string> = {
    ADD_SIGN: '加签任务已创建',
    APPROVE: '任务已通过',
    REJECT: '任务已驳回',
    TRANSFER: '任务已转办',
  };
  message.success(labels[action]);
  await loadPending();
  if (canQueryCompleted.value) await loadCompleted();
}

function onPendingPageChange(page: number, pageSize: number) {
  pendingPagination.pageSize = pageSize;
  loadPending(page);
}

function onCompletedPageChange(page: number, pageSize: number) {
  completedPagination.pageSize = pageSize;
  loadCompleted(page);
}

onMounted(() => loadPending(1));
</script>

<template>
  <Page auto-content-height>
    <div class="erp-compact-page">
      <section class="list-panel">
        <div class="list-header">
          <div class="flex items-center gap-4">
            <h2>任务中心</h2>
            <div class="flex gap-1">
              <Button :type="activeTab === 'pending' ? 'primary' : 'default'" size="small" @click="switchTab('pending')">
                我的待办
                <span v-if="pendingPagination.total > 0" class="ml-1">({{ pendingPagination.total }})</span>
              </Button>
              <Button :type="activeTab === 'completed' ? 'primary' : 'default'" size="small" @click="switchTab('completed')">
                我的已办
              </Button>
            </div>
          </div>
          <Tooltip title="刷新">
            <Button shape="circle" @click="activeTab === 'pending' ? loadPending() : loadCompleted()">
              <span class="text-lg">↻</span>
            </Button>
          </Tooltip>
        </div>

        <div class="table-frame">
          <Table
            :key="activeTab"
            :columns="columns"
            :data-source="activeTab === 'pending' ? pendingTasks : completedTasks"
            :loading="loading"
            :pagination="false"
            :scroll="{ x: 1280 }"
            bordered
            row-key="id"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'action' && activeTab === 'pending'">
                <Space v-if="record.status === 'PENDING'">
                  <Button v-if="canApprove" size="small" type="primary" @click="openAction(record as ProcessApi.TaskItem, 'APPROVE')">通过</Button>
                  <Button v-if="canReject" size="small" danger @click="openAction(record as ProcessApi.TaskItem, 'REJECT')">驳回</Button>
                  <Button v-if="canTransfer" size="small" @click="openAction(record as ProcessApi.TaskItem, 'TRANSFER')">转办</Button>
                  <Button v-if="canAddSign" size="small" @click="openAction(record as ProcessApi.TaskItem, 'ADD_SIGN')">加签</Button>
                </Space>
                <span v-else class="text-muted-foreground text-sm">-</span>
              </template>
            </template>
          </Table>
        </div>

        <div class="table-footer">
          <div class="table-total">共 {{ activeTab === 'pending' ? pendingPagination.total : completedPagination.total }} 条记录</div>
          <Pagination
            :current="activeTab === 'pending' ? pendingPagination.current : completedPagination.current"
            :page-size="activeTab === 'pending' ? pendingPagination.pageSize : completedPagination.pageSize"
            :page-size-options="['10', '20', '50', '100']"
            :total="activeTab === 'pending' ? pendingPagination.total : completedPagination.total"
            show-less-items
            show-size-changer
            size="small"
            @change="activeTab === 'pending' ? onPendingPageChange($event, pendingPagination.pageSize) : onCompletedPageChange($event, completedPagination.pageSize)"
            @show-size-change="activeTab === 'pending' ? onPendingPageChange(pendingPagination.current, $event) : onCompletedPageChange(completedPagination.current, $event)"
          />
        </div>
      </section>
    </div>

    <TaskActionDialog
      v-model:open="actionOpen"
      :action="actionType"
      :task="actionTarget"
      @success="handleActionSuccess"
    />
  </Page>
</template>

<script setup lang="ts">
import type { TableProps } from 'ant-design-vue';

import { computed, h, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import {
  Button,
  Drawer,
  Form,
  FormItem,
  Input,
  message,
  Modal,
  Pagination,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
} from 'ant-design-vue';

import { getProcessInstanceList, getProcessPackageList, startProcessInstance } from '#/api/process';
import type { ProcessApi } from '#/api/process';

const Textarea = Input.TextArea;

const PERMISSIONS = {
  query: '/api/v1/process-instances:GET',
  start: '/api/v1/process-instances/start:POST',
} as const;

const { hasAccessByCodes } = useAccess();
const canQuery = computed(() => hasAccessByCodes([PERMISSIONS.query]));
const canStart = computed(() => hasAccessByCodes([PERMISSIONS.start]));

const loading = ref(false);
const saving = ref(false);
const formOpen = ref(false);
const detailOpen = ref(false);
const detailRecord = ref<ProcessApi.ProcessInstance>();
const instances = ref<ProcessApi.ProcessInstance[]>([]);
const packageOptions = ref<Array<{ label: string; value: string }>>([]);

const query = reactive({
  keyword: '',
  status: undefined as string | undefined,
});

const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
});

const formModel = reactive({
  processKey: '',
  title: '',
  formData: '{}' as string,
});

const statusFilterOptions = [
  { label: '全部', value: undefined },
  { label: '运行中', value: 'RUNNING' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已终止', value: 'TERMINATED' },
  { label: '已挂起', value: 'SUSPENDED' },
];

const columns: TableProps['columns'] = [
  { dataIndex: 'title', key: 'title', title: '标题', width: 200 },
  { dataIndex: 'processName', key: 'processName', title: '流程', width: 150 },
  { dataIndex: 'initiatorName', key: 'initiatorName', title: '发起人', width: 100, align: 'center' },
  {
    dataIndex: 'status', key: 'status', title: '状态', width: 100, align: 'center',
    customRender: ({ text }: { text: string }) => {
      const colorMap: Record<string, string> = { RUNNING: 'processing', COMPLETED: 'success', TERMINATED: 'error', SUSPENDED: 'warning' };
      const labelMap: Record<string, string> = { RUNNING: '运行中', COMPLETED: '已完成', TERMINATED: '已终止', SUSPENDED: '已挂起' };
      return h(Tag, { color: colorMap[text] || 'default' }, () => labelMap[text] || text);
    },
  },
  { dataIndex: 'currentNodeId', key: 'currentNodeId', title: '当前节点', width: 120, align: 'center' },
  { dataIndex: 'version', key: 'version', title: '版本', width: 60, align: 'center' },
  { dataIndex: 'startedAt', key: 'startedAt', title: '发起时间', width: 180, align: 'center' },
  { dataIndex: 'completedAt', key: 'completedAt', title: '完成时间', width: 180, align: 'center' },
  { key: 'action', title: '操作', width: 100, align: 'center', fixed: 'right' },
];

async function loadInstances(page = pagination.current) {
  if (!canQuery.value) {
    instances.value = [];
    pagination.total = 0;
    return;
  }
  loading.value = true;
  try {
    const params: Record<string, any> = { page, size: pagination.pageSize };
    if (query.status) params.status = query.status;
    const result = await getProcessInstanceList(params);
    instances.value = result.items;
    pagination.current = page;
    pagination.total = result.total;
  } finally {
    loading.value = false;
  }
}

async function loadPackageOptions() {
  try {
    const result = await getProcessPackageList({ size: 100 });
    packageOptions.value = result.items
      .filter((p) => p.status === 'PUBLISHED')
      .map((p) => ({ label: `${p.name} (${p.processKey})`, value: p.processKey }));
  } catch {
    packageOptions.value = [];
  }
}

function openStart() {
  formModel.processKey = '';
  formModel.title = '';
  formModel.formData = '{}';
  loadPackageOptions();
  formOpen.value = true;
}

function openDetail(record: ProcessApi.ProcessInstance) {
  detailRecord.value = record;
  detailOpen.value = true;
}

async function submitStart() {
  if (!formModel.processKey) {
    message.warning('请选择流程');
    return;
  }
  saving.value = true;
  try {
    let formDataObj: Record<string, any> = {};
    try {
      formDataObj = JSON.parse(formModel.formData);
    } catch {
      message.warning('表单数据 JSON 格式不正确');
      return;
    }
    await startProcessInstance({
      processKey: formModel.processKey,
      title: formModel.title || undefined,
      formData: formDataObj,
    });
    message.success('流程已发起');
    formOpen.value = false;
    await loadInstances();
  } finally {
    saving.value = false;
  }
}

function onPageChange(page: number, pageSize: number) {
  pagination.pageSize = pageSize;
  loadInstances(page);
}

onMounted(() => loadInstances(1));
</script>

<template>
  <Page auto-content-height>
    <div class="erp-compact-page">
      <section class="list-panel">
        <div class="list-header">
          <h2>流程实例</h2>
          <Space :size="8">
            <Button v-if="canStart" type="primary" @click="openStart">
              <Plus class="size-4" />
              发起流程
            </Button>
            <Select
              v-model:value="query.status"
              allow-clear
              placeholder="状态筛选"
              style="width: 120px"
              :options="statusFilterOptions.filter(o => o.value !== undefined).map(o => ({ label: o.label as string, value: o.value as string }))"
              @change="loadInstances(1)"
            />
            <Tooltip title="刷新">
              <Button shape="circle" @click="loadInstances()">
                <span class="text-lg">↻</span>
              </Button>
            </Tooltip>
          </Space>
        </div>

        <div class="table-frame">
          <Table
            :columns="columns"
            :data-source="instances"
            :loading="loading"
            :pagination="false"
            :scroll="{ x: 1200 }"
            bordered
            row-key="id"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'action'">
                <Button size="small" type="link" @click="openDetail(record as ProcessApi.ProcessInstance)">详情</Button>
              </template>
            </template>
          </Table>
        </div>

        <div class="table-footer">
          <div class="table-total">共 {{ pagination.total }} 条记录</div>
          <Pagination
            v-model:current="pagination.current"
            v-model:page-size="pagination.pageSize"
            :page-size-options="['10', '20', '50', '100']"
            :total="pagination.total"
            show-less-items
            show-size-changer
            size="small"
            @change="onPageChange"
            @show-size-change="onPageChange"
          />
        </div>
      </section>
    </div>

    <Drawer
      v-model:open="formOpen"
      title="发起流程"
      destroy-on-close
      placement="right"
      width="560"
    >
      <Form layout="vertical">
        <FormItem label="选择流程" required>
          <Select v-model:value="formModel.processKey" placeholder="选择已发布的流程" :options="packageOptions" />
        </FormItem>
        <FormItem label="标题">
          <Input v-model:value="formModel.title" placeholder="不填则自动生成" />
        </FormItem>
        <FormItem label="表单数据 (JSON)">
          <Textarea v-model:value="formModel.formData" :rows="8" placeholder='{"amount": 3000, "reason": "出差费用", "dept": "技术部"}' />
        </FormItem>
      </Form>
      <template #footer>
        <Space>
          <Button @click="formOpen = false">取消</Button>
          <Button :loading="saving" type="primary" @click="submitStart">发起</Button>
        </Space>
      </template>
    </Drawer>

    <Drawer v-model:open="detailOpen" :footer="null" title="流程实例详情" placement="right" width="520">
      <template v-if="detailRecord">
        <p><strong>标题：</strong>{{ detailRecord.title }}</p>
        <p><strong>流程：</strong>{{ detailRecord.processName }} ({{ detailRecord.processKey }})</p>
        <p><strong>版本：</strong>{{ detailRecord.version }}</p>
        <p><strong>状态：</strong>
          <Tag :color="detailRecord.status === 'COMPLETED' ? 'success' : detailRecord.status === 'RUNNING' ? 'processing' : 'error'">
            {{ detailRecord.status }}
          </Tag>
        </p>
        <p><strong>发起人：</strong>{{ detailRecord.initiatorName }}</p>
        <p><strong>当前节点：</strong>{{ detailRecord.currentNodeId || '-' }}</p>
        <p><strong>发起时间：</strong>{{ detailRecord.startedAt }}</p>
        <p><strong>完成时间：</strong>{{ detailRecord.completedAt || '-' }}</p>
        <div v-if="detailRecord.formData" class="mt-2">
          <strong>表单数据：</strong>
          <pre class="json-preview mt-1">{{ detailRecord.formData }}</pre>
        </div>
      </template>
    </Drawer>
  </Page>
</template>

<style scoped>
.json-preview {
  max-height: 300px;
  overflow: auto;
  padding: 8px;
  background: #f5f5f5;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>

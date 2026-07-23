<script setup lang="ts">
import type { TableProps } from 'ant-design-vue';

import type { LowcodeApi } from '#/api/lowcode';
import type { ProcessApi } from '#/api/process';

import { computed, h, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

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
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
} from 'ant-design-vue';

import { getFormDefinitionList } from '#/api/lowcode';
import {
  createProcessPackage,
  createProcessPackageVersion,
  deleteProcessPackage,
  getProcessPackageList,
  offlineProcessPackage,
  publishProcessPackage,
  updateProcessPackage,
} from '#/api/process';
import {
  BusinessPageScaffold,
  CompactQueryBar,
  CompactTableFrame,
  CompactToolbar,
} from '#/shared';

import { validateProcessDefinition } from '../components/process-designer';

const Textarea = Input.TextArea;

const PERMISSIONS = {
  create: '/api/v1/process-packages:POST',
  delete: '/api/v1/process-packages/*:DELETE',
  offline: '/api/v1/process-packages/*/offline:PUT',
  publish: '/api/v1/process-packages/*/publish:PUT',
  query: '/api/v1/process-packages:GET',
  update: '/api/v1/process-packages/*:PUT',
  version: '/api/v1/process-packages/*/versions:POST',
} as const;

const { hasAccessByCodes } = useAccess();
const router = useRouter();
const canQuery = computed(() => hasAccessByCodes([PERMISSIONS.query]));
const canCreate = computed(() => hasAccessByCodes([PERMISSIONS.create]));
const canUpdate = computed(() => hasAccessByCodes([PERMISSIONS.update]));
const canVersion = computed(() => hasAccessByCodes([PERMISSIONS.version]));
const canDelete = computed(() => hasAccessByCodes([PERMISSIONS.delete]));
const canPublish = computed(() => hasAccessByCodes([PERMISSIONS.publish]));
const canOffline = computed(() => hasAccessByCodes([PERMISSIONS.offline]));

type ProcessPackageItem = ProcessApi.ProcessPackage;

const loading = ref(false);
const saving = ref(false);
const formOpen = ref(false);
const editing = ref<ProcessPackageItem>();
const formDefinitions = ref<LowcodeApi.FormDefinition[]>([]);
const records = ref<ProcessPackageItem[]>([]);
const jsonPreviewOpen = ref(false);
const previewPackage = ref<ProcessPackageItem>();

const query = reactive({
  keyword: '',
});

const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
});

const formModel = reactive({
  processKey: '',
  name: '',
  category: 'approval',
  description: '',
  formDefinitionId: '',
  processJson: '{\n  "version": "1.0.0",\n  "processKey": "",\n  "name": "",\n  "category": "approval",\n  "flow": {\n    "nodes": [\n      {"id":"start","type":"START","name":"开始","next":[{"condition":"true","target":"approve1"}]},\n      {"id":"approve1","type":"APPROVAL","name":"审批","assignment":{"type":"ROLE","roleCode":"DEPT_HEAD"},"next":[{"condition":"true","target":"end"}]},\n      {"id":"end","type":"END","name":"结束"}\n    ]\n  }\n}',
});

const publishedFormOptions = computed(() =>
  formDefinitions.value
    .filter((item) => item.status === 'PUBLISHED')
    .map((item) => ({
      label: `${item.name} (${item.formKey}) · v${item.version}`,
      value: item.id,
    })),
);

const columns: TableProps['columns'] = [
  { dataIndex: 'name', key: 'name', title: '流程名称', width: 180 },
  { dataIndex: 'processKey', key: 'processKey', title: '流程标识', width: 200 },
  { dataIndex: 'version', key: 'version', title: '版本', width: 70, align: 'center' },
  {
    dataIndex: 'category', key: 'category', title: '分类', width: 100, align: 'center',
    customRender: ({ text }: { text: string }) => {
      const map: Record<string, string> = { approval: '审批流', business: '业务流', integration: '集成流' };
      return map[text] || text;
    },
  },
  {
    dataIndex: 'status', key: 'status', title: '状态', width: 100, align: 'center',
    customRender: ({ text }: { text: string }) => {
      const colorMap: Record<string, string> = { DRAFT: 'default', PUBLISHED: 'success', OFFLINE: 'warning' };
      const labelMap: Record<string, string> = { DRAFT: '草稿', PUBLISHED: '已发布', OFFLINE: '已下架' };
      return h(Tag, { color: colorMap[text] || 'default' }, () => labelMap[text] || text);
    },
  },
  { dataIndex: 'description', key: 'description', title: '描述', width: 200, ellipsis: true },
  { dataIndex: 'updatedAt', key: 'updatedAt', title: '更新时间', width: 180, align: 'center' },
  { key: 'action', title: '操作', width: 350, align: 'center', fixed: 'right' },
];

async function loadRecords(page = pagination.current) {
  if (!canQuery.value) {
    records.value = [];
    pagination.total = 0;
    return;
  }
  loading.value = true;
  try {
    const result = await getProcessPackageList({
      keyword: query.keyword || undefined,
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

function resetQuery() {
  query.keyword = '';
  loadRecords(1);
}

async function loadFormDefinitions() {
  try {
    const result = await getFormDefinitionList({ page: 1, size: 100 });
    formDefinitions.value = result.items;
  } catch {
    formDefinitions.value = [];
  }
}

function resetForm() {
  editing.value = undefined;
  formModel.processKey = '';
  formModel.name = '';
  formModel.category = 'approval';
  formModel.description = '';
  formModel.formDefinitionId = '';
  formModel.processJson = '{\n  "version": "1.0.0",\n  "processKey": "",\n  "name": "",\n  "category": "approval",\n  "flow": {\n    "nodes": [\n      {"id":"start","type":"START","name":"开始","next":[{"condition":"true","target":"approve1"}]},\n      {"id":"approve1","type":"APPROVAL","name":"审批","assignment":{"type":"ROLE","roleCode":"DEPT_HEAD"},"next":[{"condition":"true","target":"end"}]},\n      {"id":"end","type":"END","name":"结束"}\n    ]\n  }\n}';
}

function openCreate() {
  resetForm();
  formOpen.value = true;
}

function openEdit(record: ProcessPackageItem) {
  editing.value = record;
  formModel.processKey = record.processKey;
  formModel.name = record.name;
  formModel.category = record.category;
  formModel.description = record.description || '';
  formModel.formDefinitionId = record.formDefinitionId || '';
  formModel.processJson = record.processJson || '';
  formOpen.value = true;
}

async function submitForm() {
  if (!formModel.processKey.trim()) {
    message.warning('请输入流程标识');
    return;
  }
  if (!formModel.name.trim()) {
    message.warning('请输入流程名称');
    return;
  }
  const validationErrors = validateProcessDefinition(formModel.processJson);
  if (validationErrors.length > 0) {
    message.error(validationErrors[0]);
    return;
  }
  saving.value = true;
  try {
    if (editing.value) {
      await updateProcessPackage(editing.value.id, {
        category: formModel.category,
        description: formModel.description || undefined,
        formDefinitionId: formModel.formDefinitionId,
        name: formModel.name.trim(),
        processJson: formModel.processJson,
      });
      message.success('草稿已更新');
    } else {
      await createProcessPackage({
        processKey: formModel.processKey.trim(),
        name: formModel.name.trim(),
        category: formModel.category,
        description: formModel.description || undefined,
        formDefinitionId: formModel.formDefinitionId || undefined,
        processJson: formModel.processJson,
      });
      message.success('流程包已创建');
    }
    formOpen.value = false;
    await loadRecords();
  } finally {
    saving.value = false;
  }
}

async function handlePublish(record: ProcessPackageItem) {
  const validationErrors = validateProcessDefinition(record.processJson);
  if (validationErrors.length > 0) {
    message.error(validationErrors[0]);
    return;
  }
  await publishProcessPackage(record.id);
  message.success('流程包已发布');
  await loadRecords();
}

async function handleNewVersion(id: string) {
  const draft = await createProcessPackageVersion(id);
  message.success(`已创建版本 ${draft.version} 草稿`);
  await loadRecords();
}

async function handleOffline(id: string) {
  await offlineProcessPackage(id);
  message.success('流程包已下架');
  await loadRecords();
}

async function handleDelete(id: string) {
  await deleteProcessPackage(id);
  message.success('流程包已删除');
  await loadRecords();
}

function openPreview(record: ProcessPackageItem) {
  previewPackage.value = record;
  jsonPreviewOpen.value = true;
}

function openDesigner(record: ProcessPackageItem) {
  void router.push({
    name: 'ProcessDesigner',
    query: { packageId: record.id },
  });
}

function onPageChange(page: number, pageSize: number) {
  pagination.pageSize = pageSize;
  loadRecords(page);
}

onMounted(() => loadRecords(1));
onMounted(loadFormDefinitions);
</script>

<template>
  <Page auto-content-height>
    <BusinessPageScaffold pattern="single-table">
      <template #query>
        <CompactQueryBar :columns="3">
          <FormItem label="关键字">
            <Input
              v-model:value="query.keyword"
              allow-clear
              placeholder="流程名称 / 标识"
              @press-enter="loadRecords(1)"
            />
          </FormItem>
          <template #actions>
            <Button v-if="canQuery" @click="resetQuery">重置</Button>
            <Button v-if="canQuery" type="primary" @click="loadRecords(1)">查询</Button>
          </template>
        </CompactQueryBar>
      </template>

      <template #toolbar>
        <CompactToolbar title="流程包管理" subtitle="维护流程包草稿、发布版本和流程定义">
          <Space :size="8">
            <Button v-if="canCreate" type="primary" @click="openCreate">
              <Plus class="size-4" />
              新建流程
            </Button>
            <Tooltip title="刷新">
              <Button shape="circle" @click="loadRecords()">
                <span class="text-lg">↻</span>
              </Button>
            </Tooltip>
          </Space>
        </CompactToolbar>
      </template>

      <CompactTableFrame>
          <Table
            :columns="columns"
            :data-source="records"
            :loading="loading"
            :pagination="false"
            :scroll="{ x: 1280 }"
            bordered
            row-key="id"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'action'">
                <Space>
                  <Button size="small" type="link" @click="openDesigner(record as ProcessPackageItem)">
                    {{ record.status === 'DRAFT' && canUpdate ? '设计' : '查看设计图' }}
                  </Button>
                  <Button size="small" type="link" @click="openPreview(record as ProcessPackageItem)">预览</Button>
                  <Button v-if="record.status === 'DRAFT' && canUpdate" size="small" type="link" @click="openEdit(record as ProcessPackageItem)">编辑</Button>
                  <Button v-if="record.status === 'DRAFT' && canPublish" size="small" type="link" @click="handlePublish(record as ProcessPackageItem)">发布</Button>
                  <Button v-if="record.status === 'PUBLISHED' && canOffline" size="small" type="link" @click="handleOffline(record.id)">下架</Button>
                  <Button v-if="record.status !== 'DRAFT' && canVersion" size="small" type="link" @click="handleNewVersion(record.id)">新版本</Button>
                  <Popconfirm title="确认删除？" @confirm="handleDelete(record.id)">
                    <Button v-if="record.status === 'DRAFT' && canDelete" danger size="small" type="link">删除</Button>
                  </Popconfirm>
                </Space>
              </template>
            </template>
          </Table>

        <template #footer>
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
        </template>
      </CompactTableFrame>
    </BusinessPageScaffold>

    <Drawer
      v-model:open="formOpen"
      :title="editing ? '编辑流程包' : '新建流程包'"
      destroy-on-close
      placement="right"
      width="640"
    >
      <Form layout="vertical">
        <FormItem label="流程标识" required>
          <Input v-model:value="formModel.processKey" :disabled="!!editing" placeholder="如 expense_report" />
        </FormItem>
        <FormItem label="流程名称" required>
          <Input v-model:value="formModel.name" placeholder="如 费用报销" />
        </FormItem>
        <FormItem label="分类">
          <Select
v-model:value="formModel.category" :options="[
            { label: '审批流', value: 'approval' },
            { label: '业务流', value: 'business' },
            { label: '集成流', value: 'integration' },
          ]"
/>
        </FormItem>
        <FormItem label="描述">
          <Textarea v-model:value="formModel.description" placeholder="流程说明" :rows="2" />
        </FormItem>
        <FormItem label="挂载表单">
          <Select
            v-model:value="formModel.formDefinitionId"
            allow-clear
            show-search
            :filter-option="(input, option) => String(option?.label ?? '').toLowerCase().includes(input.toLowerCase())"
            :options="publishedFormOptions"
            placeholder="选择已发布的快速开发表单；发布时固化快照"
          />
        </FormItem>
        <FormItem label="流程定义 JSON">
          <Textarea v-model:value="formModel.processJson" :rows="12" placeholder="流程包 JSON 定义" />
        </FormItem>
      </Form>
      <template #footer>
        <Space>
          <Button @click="formOpen = false">取消</Button>
          <Button :loading="saving" type="primary" @click="submitForm">保存</Button>
        </Space>
      </template>
    </Drawer>

    <Modal v-model:open="jsonPreviewOpen" :footer="null" title="流程定义预览" width="800">
      <pre class="json-preview">{{ previewPackage?.processJson }}</pre>
    </Modal>
  </Page>
</template>

<style scoped>
.json-preview {
  max-height: 500px;
  padding: 12px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.6;
  word-break: break-all;
  white-space: pre-wrap;
  background: #f5f5f5;
  border-radius: 4px;
}
</style>

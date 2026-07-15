<script setup lang="ts">
import type { LowcodeApi } from '#/api/lowcode';
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
  Popconfirm,
  Space,
  Table,
  Tag,
  Tooltip,
} from 'ant-design-vue';

import {
  createFormDefinition,
  getFormDefinitionList,
  publishFormDefinition,
} from '#/api/lowcode';

import DynamicProcessForm from '../../process/components/DynamicProcessForm.vue';
import { validateFormDefinition } from '../../process/components/process-form';

const Textarea = Input.TextArea;

const PERMISSIONS = {
  create: '/api/v1/forms:POST',
  publish: '/api/v1/forms/*/publish:PUT',
  query: '/api/v1/forms:GET',
} as const;

const defaultSchema = JSON.stringify(
  {
    additionalProperties: false,
    properties: {
      amount: { minimum: 0, title: '金额', type: 'number' },
      reason: { maxLength: 200, title: '事由', type: 'string' },
    },
    required: ['amount', 'reason'],
    title: '费用报销',
    type: 'object',
  },
  null,
  2,
);

const defaultUiSchema = JSON.stringify(
  {
    amount: { 'ui:placeholder': '请输入金额', 'ui:widget': 'money' },
    reason: { 'ui:placeholder': '请输入事由', 'ui:widget': 'textarea' },
  },
  null,
  2,
);

const { hasAccessByCodes } = useAccess();
const canQuery = computed(() => hasAccessByCodes([PERMISSIONS.query]));
const canCreate = computed(() => hasAccessByCodes([PERMISSIONS.create]));
const canPublish = computed(() => hasAccessByCodes([PERMISSIONS.publish]));

const loading = ref(false);
const saving = ref(false);
const formOpen = ref(false);
const previewOpen = ref(false);
const records = ref<LowcodeApi.FormDefinition[]>([]);
const previewRecord = ref<LowcodeApi.FormDefinition>();
const previewModel = ref<Record<string, unknown>>({});

const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
});

const formModel = reactive({
  description: '',
  formKey: '',
  name: '',
  schemaJson: defaultSchema,
  uiSchemaJson: defaultUiSchema,
});

const columns: TableProps['columns'] = [
  { dataIndex: 'name', key: 'name', title: '表单名称', width: 180 },
  { dataIndex: 'formKey', key: 'formKey', title: '表单标识', width: 200 },
  { align: 'center', dataIndex: 'version', key: 'version', title: '版本', width: 70 },
  {
    align: 'center',
    dataIndex: 'status',
    key: 'status',
    title: '状态',
    width: 100,
    customRender: ({ text }: { text: string }) => {
      const colorMap: Record<string, string> = {
        DRAFT: 'default',
        PUBLISHED: 'success',
      };
      const labelMap: Record<string, string> = {
        DRAFT: '草稿',
        PUBLISHED: '已发布',
      };
      return h(Tag, { color: colorMap[text] || 'default' }, () => labelMap[text] || text);
    },
  },
  { dataIndex: 'description', ellipsis: true, key: 'description', title: '说明', width: 220 },
  { align: 'center', dataIndex: 'createdAt', key: 'createdAt', title: '创建时间', width: 180 },
  { align: 'center', fixed: 'right', key: 'action', title: '操作', width: 190 },
];

async function loadRecords(page = pagination.current) {
  if (!canQuery.value) {
    records.value = [];
    pagination.total = 0;
    return;
  }
  loading.value = true;
  try {
    const result = await getFormDefinitionList({
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

function resetForm() {
  formModel.description = '';
  formModel.formKey = '';
  formModel.name = '';
  formModel.schemaJson = defaultSchema;
  formModel.uiSchemaJson = defaultUiSchema;
}

function openCreate() {
  resetForm();
  formOpen.value = true;
}

function openPreview(record: LowcodeApi.FormDefinition) {
  previewRecord.value = record;
  previewModel.value = {};
  previewOpen.value = true;
}

function validateForm() {
  if (!formModel.formKey.trim()) {
    message.warning('请输入表单标识');
    return false;
  }
  if (!formModel.name.trim()) {
    message.warning('请输入表单名称');
    return false;
  }
  const errors = validateFormDefinition(formModel.schemaJson, formModel.uiSchemaJson);
  if (errors.length > 0) {
    message.error(errors[0]);
    return false;
  }
  return true;
}

async function submitForm() {
  if (!validateForm()) {
    return;
  }
  saving.value = true;
  try {
    await createFormDefinition({
      description: formModel.description || undefined,
      formKey: formModel.formKey.trim(),
      name: formModel.name.trim(),
      schemaJson: formModel.schemaJson,
      uiSchemaJson: formModel.uiSchemaJson,
    });
    message.success('表单已创建');
    formOpen.value = false;
    await loadRecords(1);
  } finally {
    saving.value = false;
  }
}

async function handlePublish(id: string) {
  await publishFormDefinition(id);
  message.success('表单已发布，可挂载到流程并配置数据权限');
  await loadRecords();
}

function onPageChange(page: number, pageSize: number) {
  pagination.pageSize = pageSize;
  loadRecords(page);
}

onMounted(() => loadRecords(1));
</script>

<template>
  <Page auto-content-height>
    <div class="erp-compact-page">
      <section class="list-panel">
        <div class="list-header">
          <h2>表单管理</h2>
          <Space :size="8">
            <Button v-if="canCreate" type="primary" @click="openCreate">
              <Plus class="size-4" />
              新建表单
            </Button>
            <Tooltip title="刷新">
              <Button shape="circle" @click="loadRecords()">↻</Button>
            </Tooltip>
          </Space>
        </div>

        <div class="table-frame">
          <Table
            :columns="columns"
            :data-source="records"
            :loading="loading"
            :pagination="false"
            :scroll="{ x: 1100 }"
            bordered
            row-key="id"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'action'">
                <Space>
                  <Button size="small" type="link" @click="openPreview(record as LowcodeApi.FormDefinition)">预览</Button>
                  <Popconfirm
                    v-if="record.status === 'DRAFT' && canPublish"
                    title="发布后可被流程包挂载，确认发布？"
                    @confirm="handlePublish(record.id)"
                  >
                    <Button size="small" type="link">发布</Button>
                  </Popconfirm>
                </Space>
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
      destroy-on-close
      placement="right"
      title="新建表单"
      width="720"
    >
      <Form layout="vertical">
        <FormItem label="表单标识" required>
          <Input v-model:value="formModel.formKey" placeholder="如 expense_report" />
        </FormItem>
        <FormItem label="表单名称" required>
          <Input v-model:value="formModel.name" placeholder="如 费用报销" />
        </FormItem>
        <FormItem label="说明">
          <Textarea v-model:value="formModel.description" :rows="2" placeholder="表单用途说明" />
        </FormItem>
        <FormItem label="JSON Schema" required>
          <Textarea v-model:value="formModel.schemaJson" :rows="14" />
        </FormItem>
        <FormItem label="UI Schema">
          <Textarea v-model:value="formModel.uiSchemaJson" :rows="8" />
        </FormItem>
      </Form>
      <template #footer>
        <Space>
          <Button @click="formOpen = false">取消</Button>
          <Button :loading="saving" type="primary" @click="submitForm">保存</Button>
        </Space>
      </template>
    </Drawer>

    <Modal v-model:open="previewOpen" :footer="null" title="表单预览" width="640">
      <DynamicProcessForm
        v-if="previewRecord"
        v-model="previewModel"
        :schema-json="previewRecord.schemaJson"
        :ui-schema-json="previewRecord.uiSchemaJson"
      />
    </Modal>
  </Page>
</template>

<style scoped>
.list-panel {
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 120px);
  padding: 8px;
  background: #fff;
  border-radius: 4px;
}

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 34px;
  margin-bottom: 8px;
}

.list-header h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
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

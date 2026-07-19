<script setup lang="ts">
import type { LowcodeApi } from '#/api/lowcode';
import type { TableProps } from 'ant-design-vue';

import type { LowcodeEditorField } from './form-schema-editor';

import { computed, h, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import {
  Alert,
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
  Switch,
  Table,
  Tabs,
  Tag,
  Tooltip,
} from 'ant-design-vue';

import {
  createFormDefinition,
  deriveFormDefinitionVersion,
  getFormDefinitionById,
  getFormDefinitionList,
  getFormDefinitionVersions,
  offlineFormDefinition,
  publishFormDefinition,
  updateFormDefinition,
} from '#/api/lowcode';
import {
  BusinessPageScaffold,
  CompactTableFrame,
  CompactToolbar,
} from '#/shared';

import DynamicProcessForm from '../../process/components/DynamicProcessForm.vue';
import { validateFormDefinition } from '../../process/components/process-form';
import {
  buildLowcodeFormSchemas,
  createDefaultLowcodeFields,
  createFieldsFromDefinition,
  createLowcodeEditorField,
  getLowcodeLifecycleActions,
  LOWCODE_WIDGET_OPTIONS,
  normalizeLowcodeFieldWidget,
  validateLowcodeEditorFields,
} from './form-schema-editor';

const Textarea = Input.TextArea;

const PERMISSIONS = {
  create: '/api/v1/forms:POST',
  offline: '/api/v1/forms/*/offline:PUT',
  publish: '/api/v1/forms/*/publish:PUT',
  query: '/api/v1/forms:GET',
  update: '/api/v1/forms/*:PUT',
  version: '/api/v1/forms/*/versions:POST',
  versions: '/api/v1/forms/*/versions:GET',
} as const;

const { hasAccessByCodes } = useAccess();
const canQuery = computed(() => hasAccessByCodes([PERMISSIONS.query]));
const canCreate = computed(() => hasAccessByCodes([PERMISSIONS.create]));
const canUpdate = computed(() => hasAccessByCodes([PERMISSIONS.update]));
const canVersion = computed(() => hasAccessByCodes([PERMISSIONS.version]));
const canVersions = computed(() => hasAccessByCodes([PERMISSIONS.versions]));
const canPublish = computed(() => hasAccessByCodes([PERMISSIONS.publish]));
const canOffline = computed(() => hasAccessByCodes([PERMISSIONS.offline]));

const loading = ref(false);
const detailLoading = ref(false);
const saving = ref(false);
const versionLoading = ref(false);
const formOpen = ref(false);
const previewOpen = ref(false);
const versionsOpen = ref(false);
const records = ref<LowcodeApi.FormDefinition[]>([]);
const editing = ref<LowcodeApi.FormDefinition>();
const previewRecord = ref<LowcodeApi.FormDefinition>();
const previewModel = ref<Record<string, unknown>>({});
const versionRows = ref<LowcodeApi.FormDefinition[]>([]);
const versionContext = ref<LowcodeApi.FormDefinition>();

const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
});

const formModel = reactive<{
  description: string;
  fields: LowcodeEditorField[];
  formKey: string;
  name: string;
}>({
  description: '',
  fields: createDefaultLowcodeFields(),
  formKey: '',
  name: '',
});

const drawerTitle = computed(() =>
  editing.value
    ? `编辑表单草稿 · v${editing.value.version}`
    : '新建表单',
);

const schemaBuildError = computed(() => {
  const errors = validateLowcodeEditorFields(formModel.fields);
  return errors[0];
});

const generatedSchemas = computed(() => {
  try {
    return buildLowcodeFormSchemas(formModel.name || '未命名表单', formModel.fields);
  } catch {
    return undefined;
  }
});

const schemaPreview = computed(
  () => generatedSchemas.value?.schemaJson || '字段配置完整后生成 JSON Schema',
);
const uiSchemaPreview = computed(
  () => generatedSchemas.value?.uiSchemaJson || '字段配置完整后生成 UI Schema',
);

const columns: TableProps['columns'] = [
  { dataIndex: 'name', key: 'name', title: '表单名称', width: 180 },
  { dataIndex: 'formKey', key: 'formKey', title: '表单标识', width: 190 },
  { align: 'center', dataIndex: 'version', key: 'version', title: '版本', width: 70 },
  {
    align: 'center',
    dataIndex: 'status',
    key: 'status',
    title: '状态',
    width: 100,
    customRender: ({ text }: { text: string }) => renderStatusTag(text),
  },
  { dataIndex: 'tenantId', key: 'tenantId', title: '租户', width: 110 },
  {
    dataIndex: 'schemaHash',
    ellipsis: true,
    key: 'schemaHash',
    title: 'Schema Hash',
    width: 150,
    customRender: ({ text }: { text?: string }) => text?.slice(0, 12) || '-',
  },
  { dataIndex: 'description', ellipsis: true, key: 'description', title: '说明', width: 200 },
  { align: 'center', dataIndex: 'createdAt', key: 'createdAt', title: '创建时间', width: 170 },
  { align: 'center', fixed: 'right', key: 'action', title: '操作', width: 360 },
];

const versionColumns: TableProps['columns'] = [
  { align: 'center', dataIndex: 'version', key: 'version', title: '版本', width: 80 },
  {
    align: 'center',
    dataIndex: 'status',
    key: 'status',
    title: '状态',
    width: 100,
    customRender: ({ text }: { text: string }) => renderStatusTag(text),
  },
  { dataIndex: 'schemaHash', ellipsis: true, key: 'schemaHash', title: 'Schema Hash', width: 180 },
  { align: 'center', dataIndex: 'publishedAt', key: 'publishedAt', title: '发布时间', width: 180 },
  { align: 'center', dataIndex: 'offlineAt', key: 'offlineAt', title: '下线时间', width: 180 },
];

function renderStatusTag(status: string) {
  const colorMap: Record<string, string> = {
    DRAFT: 'default',
    OFFLINE: 'warning',
    PUBLISHED: 'success',
  };
  const labelMap: Record<string, string> = {
    DRAFT: '草稿',
    OFFLINE: '已下线',
    PUBLISHED: '已发布',
  };
  return h(Tag, { color: colorMap[status] || 'default' }, () => labelMap[status] || status);
}

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
  editing.value = undefined;
  formModel.description = '';
  formModel.formKey = '';
  formModel.name = '';
  replaceFields(createDefaultLowcodeFields());
}

function replaceFields(fields: LowcodeEditorField[]) {
  formModel.fields.splice(0, formModel.fields.length, ...fields);
}

function openCreate() {
  resetForm();
  formOpen.value = true;
}

async function openEdit(record: LowcodeApi.FormDefinition) {
  detailLoading.value = true;
  try {
    const detail = await getFormDefinitionById(record.id);
    editing.value = detail;
    formModel.description = detail.description || '';
    formModel.formKey = detail.formKey;
    formModel.name = detail.name;
    replaceFields(createFieldsFromDefinition(detail));
    formOpen.value = true;
  } finally {
    detailLoading.value = false;
  }
}

function openPreview(record: LowcodeApi.FormDefinition) {
  previewRecord.value = record;
  previewModel.value = {};
  previewOpen.value = true;
}

function addField() {
  formModel.fields.push(createLowcodeEditorField(formModel.fields.length));
}

function removeField(index: number) {
  if (formModel.fields.length === 1) {
    message.warning('至少保留一个字段');
    return;
  }
  formModel.fields.splice(index, 1);
}

function onWidgetChange(field: LowcodeEditorField) {
  normalizeLowcodeFieldWidget(field);
}

function fieldTypeLabel(field: LowcodeEditorField) {
  const map: Record<string, string> = {
    boolean: 'boolean',
    date: 'string(date)',
    integer: 'integer',
    money: 'number',
    number: 'number',
    select: 'string(enum)',
    string: 'string',
    textarea: 'string',
  };
  return map[field.widget] || 'string';
}

function lifecycleActions(record: LowcodeApi.FormDefinition) {
  return getLowcodeLifecycleActions(record.status, {
    canOffline: canOffline.value,
    canPublish: canPublish.value,
    canUpdate: canUpdate.value,
    canVersion: canVersion.value,
    canVersions: canVersions.value,
  });
}

function buildValidatedPayload() {
  if (!editing.value && !formModel.formKey.trim()) {
    message.warning('请输入表单标识');
    return undefined;
  }
  if (!formModel.name.trim()) {
    message.warning('请输入表单名称');
    return undefined;
  }
  const errors = validateLowcodeEditorFields(formModel.fields);
  if (errors.length > 0) {
    message.error(errors[0]);
    return undefined;
  }
  const payload = buildLowcodeFormSchemas(formModel.name, formModel.fields);
  const runtimeErrors = validateFormDefinition(payload.schemaJson, payload.uiSchemaJson);
  if (runtimeErrors.length > 0) {
    message.error(runtimeErrors[0]);
    return undefined;
  }
  return payload;
}

async function submitForm() {
  const payload = buildValidatedPayload();
  if (!payload) {
    return;
  }
  saving.value = true;
  try {
    if (editing.value) {
      await updateFormDefinition(editing.value.id, {
        description: formModel.description || undefined,
        fields: payload.fields,
        name: formModel.name.trim(),
        schemaJson: payload.schemaJson,
        uiSchemaJson: payload.uiSchemaJson,
      });
      message.success('表单草稿已更新');
    } else {
      await createFormDefinition({
        description: formModel.description || undefined,
        fields: payload.fields,
        formKey: formModel.formKey.trim(),
        name: formModel.name.trim(),
        schemaJson: payload.schemaJson,
        uiSchemaJson: payload.uiSchemaJson,
      });
      message.success('表单草稿已创建');
    }
    formOpen.value = false;
    await loadRecords(editing.value ? pagination.current : 1);
  } finally {
    saving.value = false;
  }
}

async function handlePublish(record: LowcodeApi.FormDefinition) {
  await publishFormDefinition(record.id);
  message.success('表单已发布，可挂载到流程并配置数据权限');
  await loadRecords();
}

async function handleOffline(record: LowcodeApi.FormDefinition) {
  await offlineFormDefinition(record.id);
  message.success('表单已下线，新流程将无法再挂载该版本');
  await loadRecords();
}

async function handleNewVersion(record: LowcodeApi.FormDefinition) {
  const draft = await deriveFormDefinitionVersion(record.id);
  message.success(`已派生 v${draft.version} 草稿`);
  await loadRecords();
  await openEdit(draft);
}

async function openVersions(record: LowcodeApi.FormDefinition) {
  versionContext.value = record;
  versionsOpen.value = true;
  versionLoading.value = true;
  try {
    versionRows.value = await getFormDefinitionVersions(record.formKey);
  } finally {
    versionLoading.value = false;
  }
}

function onPageChange(page: number, pageSize: number) {
  pagination.pageSize = pageSize;
  loadRecords(page);
}

onMounted(() => loadRecords(1));
</script>

<template>
  <Page auto-content-height>
    <BusinessPageScaffold pattern="single-table">
      <template #toolbar>
        <CompactToolbar title="表单管理" subtitle="维护低代码表单草稿、发布版本和字段元数据">
          <Space :size="8">
            <Button v-if="canCreate" type="primary" @click="openCreate">
              <Plus class="size-4" />
              新建表单
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
            :loading="loading || detailLoading"
            :pagination="false"
            :scroll="{ x: 1420 }"
            bordered
            row-key="id"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'action'">
                <Space>
                  <Button
                    size="small"
                    type="link"
                    @click="openPreview(record as LowcodeApi.FormDefinition)"
                  >
                    预览
                  </Button>
                  <Button
                    v-if="lifecycleActions(record as LowcodeApi.FormDefinition).edit"
                    size="small"
                    type="link"
                    @click="openEdit(record as LowcodeApi.FormDefinition)"
                  >
                    编辑
                  </Button>
                  <Popconfirm
                    v-if="lifecycleActions(record as LowcodeApi.FormDefinition).publish"
                    title="发布后版本内容不可原地修改，确认发布？"
                    @confirm="handlePublish(record as LowcodeApi.FormDefinition)"
                  >
                    <Button size="small" type="link">发布</Button>
                  </Popconfirm>
                  <Popconfirm
                    v-if="lifecycleActions(record as LowcodeApi.FormDefinition).offline"
                    title="下线后新流程无法再挂载该版本，确认下线？"
                    @confirm="handleOffline(record as LowcodeApi.FormDefinition)"
                  >
                    <Button danger size="small" type="link">下线</Button>
                  </Popconfirm>
                  <Button
                    v-if="lifecycleActions(record as LowcodeApi.FormDefinition).newVersion"
                    size="small"
                    type="link"
                    @click="handleNewVersion(record as LowcodeApi.FormDefinition)"
                  >
                    新版本
                  </Button>
                  <Button
                    v-if="lifecycleActions(record as LowcodeApi.FormDefinition).history"
                    size="small"
                    type="link"
                    @click="openVersions(record as LowcodeApi.FormDefinition)"
                  >
                    历史
                  </Button>
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
      :title="drawerTitle"
      destroy-on-close
      placement="right"
      width="860"
    >
      <Form layout="vertical">
        <div class="form-grid">
          <FormItem label="表单标识" required>
            <Input
              v-model:value="formModel.formKey"
              :disabled="!!editing"
              placeholder="如 expense_report"
            />
          </FormItem>
          <FormItem label="表单名称" required>
            <Input v-model:value="formModel.name" placeholder="如 费用报销" />
          </FormItem>
        </div>
        <FormItem label="说明">
          <Textarea v-model:value="formModel.description" :rows="2" placeholder="表单用途说明" />
        </FormItem>

        <Tabs size="small">
          <Tabs.TabPane key="fields" tab="字段配置">
            <Alert
              v-if="schemaBuildError"
              :message="schemaBuildError"
              class="mb-3"
              show-icon
              type="warning"
            />
            <div class="field-toolbar">
              <div class="field-count">字段 {{ formModel.fields.length }}</div>
              <Button size="small" type="primary" @click="addField">
                <Plus class="size-4" />
                添加字段
              </Button>
            </div>
            <div
              v-for="(field, index) in formModel.fields"
              :key="`${field.fieldKey}-${index}`"
              class="field-row"
            >
              <div class="field-row-head">
                <span>字段 {{ index + 1 }}</span>
                <Space :size="8">
                  <Tag>{{ fieldTypeLabel(field) }}</Tag>
                  <Button danger size="small" type="link" @click="removeField(index)">删除</Button>
                </Space>
              </div>
              <div class="field-grid">
                <FormItem label="字段标识" required>
                  <Input v-model:value="field.fieldKey" placeholder="如 amount" />
                </FormItem>
                <FormItem label="字段名称" required>
                  <Input v-model:value="field.label" placeholder="如 金额" />
                </FormItem>
                <FormItem label="控件">
                  <Select
                    v-model:value="field.widget"
                    :options="LOWCODE_WIDGET_OPTIONS"
                    @change="onWidgetChange(field)"
                  />
                </FormItem>
                <FormItem label="必填">
                  <Switch v-model:checked="field.required" />
                </FormItem>
              </div>
              <FormItem label="占位提示">
                <Input v-model:value="field.placeholder" placeholder="输入时显示的提示文案" />
              </FormItem>
              <FormItem v-if="field.widget === 'select'" label="选项">
                <Textarea
                  v-model:value="field.optionsText"
                  :rows="3"
                  placeholder="每行一个选项，如 TRAVEL=差旅"
                />
              </FormItem>
            </div>
          </Tabs.TabPane>
          <Tabs.TabPane key="json" tab="诊断 JSON">
            <FormItem label="JSON Schema">
              <Textarea :rows="13" :value="schemaPreview" readonly />
            </FormItem>
            <FormItem label="UI Schema">
              <Textarea :rows="8" :value="uiSchemaPreview" readonly />
            </FormItem>
          </Tabs.TabPane>
        </Tabs>
      </Form>
      <template #footer>
        <Space>
          <Button @click="formOpen = false">取消</Button>
          <Button :loading="saving" type="primary" @click="submitForm">保存草稿</Button>
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

    <Modal
      v-model:open="versionsOpen"
      :footer="null"
      :title="`${versionContext?.name || '表单'} · 版本历史`"
      width="760"
    >
      <Table
        :columns="versionColumns"
        :data-source="versionRows"
        :loading="versionLoading"
        :pagination="false"
        bordered
        row-key="id"
        size="small"
      />
    </Modal>
  </Page>
</template>

<style scoped>
.list-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 8px;
  overflow: hidden;
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

.table-total,
.field-count {
  font-size: 13px;
  color: #606b7b;
}

.form-grid,
.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.field-grid {
  grid-template-columns: minmax(140px, 1.1fr) minmax(140px, 1.1fr) minmax(140px, 1fr) 78px;
}

.field-toolbar,
.field-row-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.field-toolbar {
  margin-bottom: 8px;
}

.field-row {
  padding: 10px;
  margin-bottom: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
}

.field-row-head {
  height: 26px;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
}
</style>

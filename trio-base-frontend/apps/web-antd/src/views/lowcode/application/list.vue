<script setup lang="ts">
import type { LowcodeApi } from '#/api/lowcode';
import type { TableProps } from 'ant-design-vue';

import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import {
  Button,
  Checkbox,
  Drawer,
  Empty,
  Form,
  FormItem,
  Input,
  InputNumber,
  message,
  Pagination,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
} from 'ant-design-vue';

import {
  createApplication,
  deriveApplicationVersion,
  getApplicationList,
  getFormDefinitionById,
  getFormDefinitionList,
  offlineApplication,
  publishApplication,
  updateApplication,
} from '#/api/lowcode';
import {
  BusinessPageScaffold,
  CompactQueryBar,
  CompactTableFrame,
  CompactToolbar,
} from '#/shared';

import {
  defaultActions,
  defaultListDesign,
  defaultPages,
  validateRelationDepth,
} from './application-designer';

const PERMISSIONS = {
  create: '/api/v1/lowcode-applications:POST',
  derive: '/api/v1/lowcode-applications/*/versions:POST',
  offline: '/api/v1/lowcode-applications/*/offline:PUT',
  publish: '/api/v1/lowcode-applications/*/publish:PUT',
  query: '/api/v1/lowcode-applications:GET',
  update: '/api/v1/lowcode-applications/*:PUT',
} as const;

const { hasAccessByCodes } = useAccess();
const router = useRouter();
const canCreate = computed(() => hasAccessByCodes([PERMISSIONS.create]));
const canUpdate = computed(() => hasAccessByCodes([PERMISSIONS.update]));
const canPublish = computed(() => hasAccessByCodes([PERMISSIONS.publish]));
const canOffline = computed(() => hasAccessByCodes([PERMISSIONS.offline]));
const canDerive = computed(() => hasAccessByCodes([PERMISSIONS.derive]));

const loading = ref(false);
const saving = ref(false);
const records = ref<LowcodeApi.ApplicationDraft[]>([]);
const forms = ref<LowcodeApi.FormDefinition[]>([]);
const primaryFields = ref<LowcodeApi.FormFieldSchema[]>([]);
const drawerOpen = ref(false);
const editing = ref<LowcodeApi.ApplicationDraft>();
const keyword = ref('');
const activeTab = ref('basic');
const pagination = reactive({ current: 1, pageSize: 20, total: 0 });

const model = reactive<LowcodeApi.SaveApplication & { appKey: string }>({
  actions: [],
  appKey: '',
  description: '',
  name: '',
  pages: [],
  primaryFormDefinitionId: '',
  relations: [],
  viewPermissionCode: '/api/v1/lowcode-runtime/apps/*:GET',
});

const listDesign = computed(() => {
  const page = model.pages.find((item) => item.pageType === 'LIST');
  try {
    return page ? JSON.parse(page.metadataJson) : defaultListDesign(primaryFields.value);
  } catch {
    return defaultListDesign(primaryFields.value);
  }
});

const formOptions = computed(() => forms.value
  .filter((form) => form.status === 'PUBLISHED')
  .map((form) => ({ label: `${form.name} · ${form.formKey} · v${form.version}`, value: form.id })));
const fieldOptions = computed(() => primaryFields.value.map((field) => ({ label: field.label, value: field.fieldKey })));

const columns: TableProps['columns'] = [
  { dataIndex: 'name', key: 'name', title: '应用名称', width: 180 },
  { dataIndex: 'appKey', key: 'appKey', title: '应用标识', width: 180 },
  { dataIndex: 'formKey', key: 'formKey', title: '主表单', width: 160 },
  { align: 'center', dataIndex: 'version', key: 'version', title: '版本', width: 80 },
  { align: 'center', key: 'status', title: '状态', width: 100 },
  { dataIndex: 'publishedAt', key: 'publishedAt', title: '发布时间', width: 180 },
  { fixed: 'right', key: 'action', title: '操作', width: 330 },
];

async function load(page = pagination.current) {
  loading.value = true;
  try {
    const result = await getApplicationList({ page, size: pagination.pageSize });
    records.value = keyword.value.trim()
      ? result.items.filter((item) => `${item.name}${item.appKey}`.toLowerCase().includes(keyword.value.trim().toLowerCase()))
      : result.items;
    pagination.current = page;
    pagination.total = keyword.value.trim() ? records.value.length : result.total;
  } finally {
    loading.value = false;
  }
}

async function loadForms() {
  const result = await getFormDefinitionList({ page: 1, size: 200 });
  forms.value = result.items;
}

function resetModel() {
  Object.assign(model, {
    actions: [], appKey: '', description: '', name: '', pages: [],
    primaryFormDefinitionId: '', relations: [],
    viewPermissionCode: '/api/v1/lowcode-runtime/apps/*:GET',
  });
  primaryFields.value = [];
  activeTab.value = 'basic';
}

async function openCreate() {
  editing.value = undefined;
  resetModel();
  await loadForms();
  drawerOpen.value = true;
}

async function openEdit(record: LowcodeApi.ApplicationDraft) {
  editing.value = record;
  await loadForms();
  Object.assign(model, {
    actions: record.actions ?? [],
    appKey: record.appKey,
    description: record.description ?? '',
    name: record.name,
    pages: record.pages ?? [],
    primaryFormDefinitionId: record.primaryFormDefinitionId,
    relations: record.relations ?? [],
    viewPermissionCode: record.viewPermissionCode || '/api/v1/lowcode-runtime/apps/*:GET',
  });
  await loadPrimaryFields(false);
  activeTab.value = 'basic';
  drawerOpen.value = true;
}

async function loadPrimaryFields(resetDesign = true) {
  if (!model.primaryFormDefinitionId) return;
  const form = await getFormDefinitionById(model.primaryFormDefinitionId);
  primaryFields.value = form.fields ?? [];
  if (resetDesign) {
    model.pages = defaultPages(primaryFields.value);
    model.actions = defaultActions(model.primaryFormDefinitionId);
    model.relations = [];
  }
}

function updateListFields(selected: Array<boolean | number | string>) {
  const selectedSet = new Set(selected.map(String));
  const design = defaultListDesign(primaryFields.value.filter((field) => selectedSet.has(field.fieldKey)));
  replaceListDesign(design);
}

function updateFilterFields(selected: Array<boolean | number | string>) {
  const design = { ...listDesign.value, filters: primaryFields.value
    .filter((field) => selected.map(String).includes(field.fieldKey))
    .map((field) => ({ fieldKey: field.fieldKey, label: field.label, operator: 'contains' })) };
  replaceListDesign(design);
}

function replaceListDesign(design: unknown) {
  const page = model.pages.find((item) => item.pageType === 'LIST');
  if (page) page.metadataJson = JSON.stringify(design);
}

function addRelation() {
  model.relations.push({
    cardinality: 'MANY', cascadeDelete: false, cascadeSave: true,
    childForeignKeyField: 'parentId', childFormDefinitionId: '',
    parentFormDefinitionId: model.primaryFormDefinitionId, parentKeyField: 'id',
    relationCode: `DETAIL_${model.relations.length + 1}`, sortOrder: model.relations.length * 10 + 10,
  });
}

function removeRelation(index: number) {
  model.relations.splice(index, 1);
}

async function submit() {
  if (!model.appKey.trim() || !model.name.trim() || !model.primaryFormDefinitionId) {
    message.warning('请填写应用标识、名称并选择已发布主表单');
    return;
  }
  if (!model.pages.length || !model.actions.length) {
    message.warning('请完成页面与动作编排');
    return;
  }
  const relationError = validateRelationDepth(model.primaryFormDefinitionId, model.relations);
  if (relationError) {
    message.warning(relationError);
    activeTab.value = 'relations';
    return;
  }
  saving.value = true;
  try {
    const payload = JSON.parse(JSON.stringify(model));
    if (editing.value) await updateApplication(editing.value.versionId, payload);
    else await createApplication(payload);
    message.success('应用草稿已保存');
    drawerOpen.value = false;
    await load(1);
  } finally {
    saving.value = false;
  }
}

async function publish(record: LowcodeApi.ApplicationDraft) {
  await publishApplication(record.versionId);
  message.success('应用已发布，请在角色授权中授予应用 VIEW 和表单业务动作');
  await load();
}

async function offline(record: LowcodeApi.ApplicationDraft) {
  await offlineApplication(record.versionId);
  message.success('应用已下线');
  await load();
}

async function derive(record: LowcodeApi.ApplicationDraft) {
  const draft = await deriveApplicationVersion(record.versionId);
  message.success('已派生新草稿版本');
  await load();
  await openEdit(draft);
}

function openRuntime(record: LowcodeApi.ApplicationDraft) {
  void router.push({ name: 'LowcodeRuntimeApp', params: { appKey: record.appKey }, query: { version: record.version } });
}

onMounted(() => load(1));
</script>

<template>
  <Page auto-content-height>
    <BusinessPageScaffold pattern="single-table">
      <template #query>
        <CompactQueryBar :columns="3">
          <Input v-model:value="keyword" allow-clear placeholder="应用名称 / 标识" @press-enter="load(1)" />
          <template #actions>
            <Button @click="keyword = ''; load(1)">重置</Button>
            <Button type="primary" @click="load(1)">查询</Button>
          </template>
        </CompactQueryBar>
      </template>
      <template #toolbar>
        <CompactToolbar title="应用管理" subtitle="编排表单、列表、动作和主子孙关系，发布后进入应用中心">
          <Button v-if="canCreate" type="primary" @click="openCreate"><Plus class="size-4" />新建应用</Button>
        </CompactToolbar>
      </template>
      <CompactTableFrame>
        <Table :columns="columns" :data-source="records" :loading="loading" :pagination="false" :scroll="{ x: 1180 }" bordered row-key="versionId" size="small">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <Tag :color="record.status === 'PUBLISHED' ? 'success' : record.status === 'DRAFT' ? 'processing' : 'default'">{{ record.status }}</Tag>
            </template>
            <template v-else-if="column.key === 'action'">
              <Space :size="2">
                <Button v-if="record.status === 'DRAFT' && canUpdate" size="small" type="link" @click="openEdit(record as LowcodeApi.ApplicationDraft)">设计</Button>
                <Button v-if="record.status === 'DRAFT' && canPublish" size="small" type="link" @click="publish(record as LowcodeApi.ApplicationDraft)">发布</Button>
                <Button v-if="record.status === 'PUBLISHED'" size="small" type="link" @click="openRuntime(record as LowcodeApi.ApplicationDraft)">打开</Button>
                <Button v-if="record.status !== 'DRAFT' && canDerive" size="small" type="link" @click="derive(record as LowcodeApi.ApplicationDraft)">新版本</Button>
                <Popconfirm v-if="record.status === 'PUBLISHED' && canOffline" title="确认下线该应用？" @confirm="offline(record as LowcodeApi.ApplicationDraft)">
                  <Button danger size="small" type="link">下线</Button>
                </Popconfirm>
              </Space>
            </template>
          </template>
        </Table>
        <template #footer>
          <span>共 {{ pagination.total }} 个应用</span>
          <Pagination v-model:current="pagination.current" v-model:page-size="pagination.pageSize" :total="pagination.total" size="small" @change="load" />
        </template>
      </CompactTableFrame>
    </BusinessPageScaffold>

    <Drawer v-model:open="drawerOpen" :title="editing ? `设计应用 · v${editing.version}` : '新建应用'" destroy-on-close width="900">
      <Tabs v-model:active-key="activeTab">
        <Tabs.TabPane key="basic" tab="基本信息">
          <Form layout="vertical">
            <div class="form-grid">
              <FormItem label="应用标识" required><Input v-model:value="model.appKey" :disabled="!!editing" placeholder="如 LEAVE_APP" /></FormItem>
              <FormItem label="应用名称" required><Input v-model:value="model.name" placeholder="如 请假申请" /></FormItem>
              <FormItem class="span-2" label="已发布主表单" required>
                <Select v-model:value="model.primaryFormDefinitionId" :disabled="!!editing" :options="formOptions" show-search @change="loadPrimaryFields(true)" />
              </FormItem>
              <FormItem class="span-2" label="说明"><Input.TextArea v-model:value="model.description" :rows="2" /></FormItem>
            </div>
          </Form>
        </Tabs.TabPane>
        <Tabs.TabPane key="list" tab="列表设计">
          <Empty v-if="!primaryFields.length" description="请先选择已发布主表单" />
          <Form v-else layout="vertical">
            <FormItem label="展示列"><Checkbox.Group :options="fieldOptions" :value="listDesign.columns.map((item: any) => item.fieldKey)" @change="updateListFields" /></FormItem>
            <FormItem label="筛选字段"><Checkbox.Group :options="fieldOptions" :value="listDesign.filters.map((item: any) => item.fieldKey)" @change="updateFilterFields" /></FormItem>
            <div class="form-grid">
              <FormItem label="默认排序字段"><Select :options="fieldOptions" :value="listDesign.defaultSort?.fieldKey" allow-clear @change="(value) => replaceListDesign({ ...listDesign, defaultSort: value ? { fieldKey: value, direction: 'DESC' } : undefined })" /></FormItem>
              <FormItem label="每页条数"><InputNumber :min="10" :max="200" :value="listDesign.pageSize" @change="(value) => replaceListDesign({ ...listDesign, pageSize: value || 20 })" /></FormItem>
            </div>
            <Table :data-source="listDesign.columns" :pagination="false" row-key="fieldKey" size="small">
              <Table.Column data-index="label" title="列名" />
              <Table.Column data-index="fieldKey" title="字段" />
              <Table.Column data-index="format" title="格式" />
              <Table.Column data-index="width" title="宽度" />
            </Table>
          </Form>
        </Tabs.TabPane>
        <Tabs.TabPane key="relations" tab="主子孙表单">
          <Space class="mb-3"><Button type="primary" :disabled="!model.primaryFormDefinitionId" @click="addRelation">新增下级关系</Button><span>最多三级，所有表单必须已发布</span></Space>
          <Empty v-if="!model.relations.length" description="当前为单表应用" />
          <div v-for="(relation, index) in model.relations" v-else :key="index" class="relation-card">
            <div class="relation-grid">
              <Input v-model:value="relation.relationCode" placeholder="关系编码" />
              <Select v-model:value="relation.parentFormDefinitionId" :options="formOptions" placeholder="父表单" />
              <Select v-model:value="relation.childFormDefinitionId" :options="formOptions" placeholder="子表单" />
              <Select v-model:value="relation.cardinality" :options="[{ label: '一对多', value: 'MANY' }, { label: '一对一', value: 'ONE' }]" />
              <Input v-model:value="relation.parentKeyField" placeholder="父键，默认 id" />
              <Input v-model:value="relation.childForeignKeyField" placeholder="子表外键字段" />
              <span>级联保存 <Switch v-model:checked="relation.cascadeSave" /></span>
              <Button danger type="link" @click="removeRelation(index)">删除</Button>
            </div>
          </div>
        </Tabs.TabPane>
        <Tabs.TabPane key="actions" tab="动作与授权">
          <Table :data-source="model.actions" :pagination="false" row-key="actionCode" size="small">
            <Table.Column data-index="label" title="名称" />
            <Table.Column data-index="actionCode" title="动作编码" />
            <Table.Column data-index="actionType" title="动作类型" />
            <Table.Column data-index="permissionCode" title="接口权限" />
          </Table>
          <div class="authorization-tip">发布后还需在“角色管理 → 角色授权 → 功能授权”中授予 LOWCODE_APP 的 VIEW，以及各 LOWCODE_FORM 的 VIEW/CREATE/EDIT/SUBMIT/APPROVE 等动作。</div>
        </Tabs.TabPane>
      </Tabs>
      <template #footer><Space><Button @click="drawerOpen = false">取消</Button><Button :loading="saving" type="primary" @click="submit">保存草稿</Button></Space></template>
    </Drawer>
  </Page>
</template>

<style scoped>
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 16px; }
.span-2 { grid-column: span 2; }
.relation-card { padding: 12px; margin-bottom: 12px; border: 1px solid #e5e7eb; border-radius: 6px; }
.relation-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; align-items: center; }
.authorization-tip { padding: 12px; margin-top: 16px; color: #92400e; background: #fffbeb; border: 1px solid #fde68a; border-radius: 6px; }
</style>

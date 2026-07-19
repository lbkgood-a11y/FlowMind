<script setup lang="ts">
import type { OpenApiOperationsApi } from '#/api';
import type { TableProps } from 'ant-design-vue';

import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';

import {
  Card,
  Button,
  FormItem,
  Input,
  message,
  Modal,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Tooltip,
} from 'ant-design-vue';

import { getLifecycleAssets, getOpenApiLifecycleData, invokeOpenApiLifecycleAction } from '#/api';
import {
  BusinessPageScaffold,
  CompactQueryBar,
  CompactTableFrame,
  CompactToolbar,
  SplitLayout,
} from '#/shared';

import AssetReferenceSelect from './AssetReferenceSelect.vue';
import DynamicPayloadForm from './DynamicPayloadForm.vue';
import LifecycleAssetDetail from './LifecycleAssetDetail.vue';
import {
  buildPayload,
  createPayloadFormState,
  parsePayloadJson,
  PayloadFormError,
  payloadToFormState,
  payloadToJson,
} from './payload-form';
import { lifecycleResources } from './resource-config';

const route = useRoute();
const { hasAccessByCodes } = useAccess();
const TabPane = Tabs.TabPane;
const Textarea = Input.TextArea;

const resourceKey = computed(() => String(route.path.split('/').filter(Boolean).at(-1)));
const config = computed(() => lifecycleResources[resourceKey.value] ?? lifecycleResources.structures!);
const canWrite = computed(() => hasAccessByCodes([config.value.writePermission]));
const rows = ref<OpenApiOperationsApi.LifecycleAsset[]>([]);
const loading = ref(false);
const detail = ref<OpenApiOperationsApi.LifecycleAsset>();
const detailOpen = ref(false);
const createOpen = ref(false);
const saving = ref(false);
const createTab = ref('visual');
const createState = ref(createPayloadFormState(config.value.createForm));
const createJson = ref('{}');
const query = reactive({ keyword: '', state: '' });
const actionOpen = ref(false);
const actionTarget = ref('');
const actionPath = ref('');
const actionTab = ref('visual');
const actionState = ref<Record<string, unknown>>({});
const actionJson = ref('{}');
const editOpen = ref(false);
const editTarget = ref<OpenApiOperationsApi.LifecycleAsset>();
const editTab = ref('visual');
const editState = ref<Record<string, unknown>>({});
const editJson = ref('{}');
const pagination = reactive({
  current: 1,
  pageSize: 20,
  showSizeChanger: true,
  total: 0,
});

const selectedAction = computed(() =>
  config.value.actions.find((item) => item.path === actionPath.value),
);
const actionOptions = computed(() =>
  config.value.actions.map((item) => ({ label: item.label, value: item.path })),
);
const columns: TableProps['columns'] = [
  { dataIndex: 'assetKey', key: 'key', title: '标识' },
  { dataIndex: 'displayName', key: 'name', title: '名称' },
  { dataIndex: 'lifecycleState', key: 'state', title: '状态', width: 140 },
  { dataIndex: 'updatedAt', key: 'updated', title: '更新时间', width: 180 },
  { fixed: 'right', key: 'actions', title: '操作', width: 160 },
];

async function load() {
  loading.value = true;
  try {
    const result = await getLifecycleAssets(config.value.assetType, {
      keyword: query.keyword || undefined,
      page: pagination.current,
      size: pagination.pageSize,
      state: query.state || undefined,
    });
    rows.value = result.records;
    pagination.total = result.total;
  } finally {
    loading.value = false;
  }
}

function asAsset(record: Record<string, any>) {
  return record as OpenApiOperationsApi.LifecycleAsset;
}

function canEditRecord(record: OpenApiOperationsApi.LifecycleAsset) {
  const edit = config.value.edit;
  if (!edit) return false;
  return !edit.states?.length || edit.states.includes(record.lifecycleState ?? '');
}

function changeActionTab(key: number | string) {
  const nextTab = String(key);
  const form = selectedAction.value?.form;
  if (!form) return;
  if (nextTab === 'json' && !syncActionJson()) {
    return;
  }
  if (nextTab === 'visual' && !syncActionVisual()) {
    return;
  }
  actionTab.value = nextTab;
}

function changeCreateTab(key: number | string) {
  const nextTab = String(key);
  if (nextTab === 'json' && !syncCreateJson()) {
    return;
  }
  if (nextTab === 'visual' && !syncCreateVisual()) {
    return;
  }
  createTab.value = nextTab;
}

function changeEditTab(key: number | string) {
  const editConfig = config.value.edit;
  if (!editConfig) return;
  const nextTab = String(key);
  if (nextTab === 'json' && !syncEditJson()) {
    return;
  }
  if (nextTab === 'visual' && !syncEditVisual()) {
    return;
  }
  editTab.value = nextTab;
}

async function create() {
  let data: Record<string, unknown>;
  try {
    data =
      createTab.value === 'json'
        ? parsePayloadJson(createJson.value)
        : buildPayload(config.value.createForm, createState.value);
  } catch (error) {
    reportPayloadError(error);
    return;
  }
  saving.value = true;
  try {
    await invokeOpenApiLifecycleAction('POST', config.value.createEndpoint, data);
    message.success('创建成功');
    createOpen.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function edit() {
  const editConfig = config.value.edit;
  const target = editTarget.value;
  if (!editConfig || !target) {
    return;
  }
  let data: Record<string, unknown>;
  try {
    data =
      editTab.value === 'json'
        ? parsePayloadJson(editJson.value)
        : buildPayload(editConfig.form, editState.value);
  } catch (error) {
    reportPayloadError(error);
    return;
  }
  saving.value = true;
  try {
    await invokeOpenApiLifecycleAction(
      editConfig.method ?? 'PUT',
      interpolatePath(editConfig.path, target),
      data,
    );
    message.success('保存成功');
    editOpen.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function executeAction() {
  const action = selectedAction.value;
  if (!actionTarget.value || !action) {
    message.warning('请选择动作和目标资产');
    return;
  }
  let data: Record<string, unknown> | undefined;
  try {
    data = action.form
      ? actionTab.value === 'json'
        ? parsePayloadJson(actionJson.value)
        : buildPayload(action.form, actionState.value)
      : undefined;
  } catch (error) {
    reportPayloadError(error);
    return;
  }
  saving.value = true;
  try {
    await invokeOpenApiLifecycleAction(
      action.method ?? 'POST',
      action.path.replace('{id}', encodeURIComponent(actionTarget.value)),
      data,
    );
    message.success('生命周期动作已执行');
    actionOpen.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

function inspect(record: OpenApiOperationsApi.LifecycleAsset) {
  detail.value = record;
  detailOpen.value = true;
}

async function openEditDialog(record: OpenApiOperationsApi.LifecycleAsset) {
  const editConfig = config.value.edit;
  if (!editConfig || !canEditRecord(record)) {
    return;
  }
  editTarget.value = record;
  saving.value = true;
  try {
    const source = editConfig.loadPath
      ? await getOpenApiLifecycleData<Record<string, unknown>>(interpolatePath(editConfig.loadPath, record))
      : record.detail;
    editState.value = payloadToFormState(editConfig.form, source);
    editJson.value = payloadToJson(buildPayload(editConfig.form, editState.value));
    editTab.value = 'visual';
    editOpen.value = true;
  } catch {
    message.error('读取草稿配置失败');
  } finally {
    saving.value = false;
  }
}

function openActionDialog() {
  actionPath.value = config.value.actions[0]?.path ?? '';
  actionTarget.value = '';
  resetActionPayload();
  actionOpen.value = true;
}

function openCreateDialog() {
  createState.value = createPayloadFormState(config.value.createForm);
  createJson.value = payloadToJson(buildPayload(config.value.createForm, createState.value));
  createTab.value = 'visual';
  createOpen.value = true;
}

function reportPayloadError(error: unknown) {
  if (error instanceof PayloadFormError) {
    message.error(error.message);
    return;
  }
  message.error('配置内容不合法');
}

function interpolatePath(path: string, asset: OpenApiOperationsApi.LifecycleAsset) {
  return path.replaceAll('{id}', encodeURIComponent(asset.id));
}

function resetActionPayload() {
  const form = selectedAction.value?.form;
  actionTab.value = 'visual';
  actionState.value = form ? createPayloadFormState(form) : {};
  actionJson.value = form ? payloadToJson(buildPayload(form, actionState.value)) : '{}';
}

function syncActionJson() {
  const form = selectedAction.value?.form;
  if (!form) return true;
  try {
    actionJson.value = payloadToJson(buildPayload(form, actionState.value));
    return true;
  } catch (error) {
    reportPayloadError(error);
    return false;
  }
}

function syncActionVisual() {
  const form = selectedAction.value?.form;
  if (!form) return true;
  try {
    actionState.value = payloadToFormState(form, parsePayloadJson(actionJson.value));
    return true;
  } catch (error) {
    reportPayloadError(error);
    return false;
  }
}

function syncCreateJson() {
  try {
    createJson.value = payloadToJson(buildPayload(config.value.createForm, createState.value));
    return true;
  } catch (error) {
    reportPayloadError(error);
    return false;
  }
}

function syncCreateVisual() {
  try {
    createState.value = payloadToFormState(
      config.value.createForm,
      parsePayloadJson(createJson.value),
    );
    return true;
  } catch (error) {
    reportPayloadError(error);
    return false;
  }
}

function syncEditJson() {
  const editConfig = config.value.edit;
  if (!editConfig) return true;
  try {
    editJson.value = payloadToJson(buildPayload(editConfig.form, editState.value));
    return true;
  } catch (error) {
    reportPayloadError(error);
    return false;
  }
}

function syncEditVisual() {
  const editConfig = config.value.edit;
  if (!editConfig) return true;
  try {
    editState.value = payloadToFormState(editConfig.form, parsePayloadJson(editJson.value));
    return true;
  } catch (error) {
    reportPayloadError(error);
    return false;
  }
}

function tableChange(next: TableProps['pagination']) {
  if (next && typeof next === 'object') {
    pagination.current = next.current ?? 1;
    pagination.pageSize = next.pageSize ?? 20;
    load();
  }
}

watch(actionPath, resetActionPayload);
watch(resourceKey, () => {
  pagination.current = 1;
  createOpen.value = false;
  editOpen.value = false;
  actionOpen.value = false;
  load();
});
onMounted(load);
</script>

<template>
  <Page :description="config.description" :title="config.title">
    <BusinessPageScaffold pattern="document">
      <template #query>
        <CompactQueryBar :columns="3">
          <Input v-model:value="query.keyword" allow-clear placeholder="名称或标识" />
          <Input v-model:value="query.state" allow-clear placeholder="生命周期状态" />
          <template #actions>
            <Button type="primary" @click="load">查询</Button>
            <Button v-if="canWrite" @click="openCreateDialog">新建</Button>
            <Button v-if="canWrite && config.actions.length" @click="openActionDialog">
              生命周期动作
            </Button>
          </template>
        </CompactQueryBar>
      </template>
      <template #toolbar>
        <CompactToolbar :title="config.title" :subtitle="config.description" />
      </template>
      <SplitLayout left-width="260px">
        <template #left>
          <Card size="small" title="资源上下文">
            <Space direction="vertical" size="small">
              <Tag color="blue">{{ config.assetType }}</Tag>
              <span>动作 {{ config.actions.length }}</span>
              <span>记录 {{ pagination.total }}</span>
            </Space>
          </Card>
        </template>
        <CompactTableFrame>
          <Table
            row-key="id"
            size="small"
            :columns="columns"
            :data-source="rows"
            :loading="loading"
            :pagination="pagination"
            @change="tableChange"
          >
            <template #bodyCell="{ column, record }">
              <Tag v-if="column.key === 'state'" color="blue">
                {{ record.lifecycleState || '-' }}
              </Tag>
              <Space v-if="column.key === 'actions'">
                <Button type="link" @click="inspect(asAsset(record))">详情</Button>
                <Button
                  v-if="canWrite && config.edit && canEditRecord(asAsset(record))"
                  type="link"
                  @click="openEditDialog(asAsset(record))"
                >
                  编辑
                </Button>
                <Tooltip
                  v-else-if="canWrite && config.edit"
                  title="只有 DRAFT 草稿可编辑；已发布版本请新建草稿后修改"
                >
                  <Button disabled type="link">编辑</Button>
                </Tooltip>
              </Space>
            </template>
          </Table>
        </CompactTableFrame>
      </SplitLayout>
    </BusinessPageScaffold>
    <Modal v-model:open="detailOpen" title="资产详情" :footer="null" width="920px">
      <LifecycleAssetDetail :asset="detail" :config="config" />
    </Modal>
    <Modal
      v-model:open="createOpen"
      :confirm-loading="saving"
      :title="`新建${config.title}`"
      width="860px"
      @ok="create"
    >
      <Tabs :active-key="createTab" size="small" @change="changeCreateTab">
        <TabPane key="visual" tab="可视化配置">
          <DynamicPayloadForm v-model:model-value="createState" :form="config.createForm" />
        </TabPane>
        <TabPane key="json" tab="高级 JSON">
          <Textarea v-model:value="createJson" :rows="20" />
        </TabPane>
      </Tabs>
    </Modal>
    <Modal
      v-model:open="editOpen"
      :confirm-loading="saving"
      :title="`编辑${config.title}草稿`"
      width="860px"
      @ok="edit"
    >
      <Tabs v-if="config.edit" :active-key="editTab" size="small" @change="changeEditTab">
        <TabPane key="visual" tab="可视化配置">
          <DynamicPayloadForm v-model:model-value="editState" :form="config.edit.form" />
        </TabPane>
        <TabPane key="json" tab="高级 JSON">
          <Textarea v-model:value="editJson" :rows="20" />
        </TabPane>
      </Tabs>
    </Modal>
    <Modal
      v-model:open="actionOpen"
      :confirm-loading="saving"
      title="执行生命周期动作"
      width="860px"
      @ok="executeAction"
    >
      <Space direction="vertical" style="width: 100%">
        <FormItem label="动作" required>
          <Select v-model:value="actionPath" placeholder="选择动作" :options="actionOptions" />
        </FormItem>
        <FormItem :label="selectedAction?.targetLabel ?? '目标资产'" required>
          <AssetReferenceSelect
            v-if="selectedAction?.targetReference"
            v-model:model-value="actionTarget"
            :placeholder="selectedAction?.targetPlaceholder ?? '搜索选择目标资产'"
            :reference="selectedAction.targetReference"
          />
          <Input
            v-else
            v-model:value="actionTarget"
            :placeholder="selectedAction?.targetPlaceholder ?? '搜索选择目标资产'"
          />
        </FormItem>
        <Tabs
          v-if="selectedAction?.form"
          :active-key="actionTab"
          size="small"
          @change="changeActionTab"
        >
          <TabPane key="visual" tab="动作配置">
            <DynamicPayloadForm v-model:model-value="actionState" :form="selectedAction.form" />
          </TabPane>
          <TabPane key="json" tab="高级 JSON">
            <Textarea v-model:value="actionJson" :rows="12" />
          </TabPane>
        </Tabs>
        <div v-else class="bodyless-action">该动作无需请求参数</div>
      </Space>
    </Modal>
  </Page>
</template>

<style scoped>
.toolbar {
  margin-bottom: 12px;
}

.toolbar :deep(.ant-input) {
  width: 220px;
}

.bodyless-action {
  padding: 8px 0;
  color: #64748b;
}

pre {
  max-height: 520px;
  padding: 12px;
  overflow: auto;
  color: #e2e8f0;
  background: #0f172a;
  border-radius: 6px;
}
</style>

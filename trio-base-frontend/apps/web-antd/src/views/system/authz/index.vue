<script setup lang="ts">
import type { SystemAuthorizationApi } from '#/api';
import type { TableProps } from 'ant-design-vue';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import {
  Button,
  Drawer,
  Form,
  FormItem,
  Input,
  message,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tabs,
} from 'ant-design-vue';

import {
  deleteAuthorizationFieldPolicy,
  deleteAuthorizationGrant,
  getAuthorizationAdminOptions,
  getAuthorizationResourceTree,
  getRoleAuthorizationProfile,
  previewAuthorizationDecision,
  saveAuthorizationFieldPolicy,
  saveAuthorizationGrant,
  saveAuthorizationGuardTemplate,
  updateAuthorizationGuardTemplateStatus,
} from '#/api';
import {
  BusinessPageScaffold,
  CompactTableFrame,
  CompactToolbar,
} from '#/shared';

const TabPane = Tabs.TabPane;

const AUTHZ_PERMISSIONS = {
  delete: '/api/v1/authz/**:DELETE',
  post: '/api/v1/authz/**:POST',
  put: '/api/v1/authz/**:PUT',
  query: '/api/v1/authz/**:GET',
} as const;

const { hasAccessByCodes } = useAccess();
const canQuery = computed(() => hasAccessByCodes([AUTHZ_PERMISSIONS.query]));
const canCreate = computed(() => hasAccessByCodes([AUTHZ_PERMISSIONS.post]));
const canUpdate = computed(() => hasAccessByCodes([AUTHZ_PERMISSIONS.put]));
const canDelete = computed(() => hasAccessByCodes([AUTHZ_PERMISSIONS.delete]));

// ─── Shared state ───
const loading = ref(false);
const saving = ref(false);
const resourceTree = ref<SystemAuthorizationApi.ResourceTree>();
const adminOptions = ref<SystemAuthorizationApi.AdminOptions>();

const resourceGroups = computed(() => resourceTree.value?.groups ?? []);
const resourceList = computed(() =>
  resourceGroups.value.flatMap((group) => group.resources ?? []),
);

function findResource(resourceCode: string) {
  return resourceList.value.find((r) => r.resourceCode === resourceCode);
}

function actionOptionsForResource(resourceCode: string) {
  const r = findResource(resourceCode);
  return (r?.actions ?? [])
    .filter((a) => a.status !== 0)
    .map((a) => ({
      label: `${a.actionCode}${a.description ? ` · ${a.description}` : ''}`,
      value: a.actionCode,
    }));
}

function fieldOptionsForResource(resourceCode: string) {
  const r = findResource(resourceCode);
  return (r?.fields ?? [])
    .filter((f) => f.status !== 0)
    .map((f) => ({
      label: f.fieldLabel || f.fieldKey,
      value: f.fieldKey,
    }));
}

const resourceOptions = computed(() =>
  resourceList.value.map((r) => ({
    label: `${r.displayName || r.resourceCode} · ${r.resourceType}`,
    value: r.resourceCode,
  })),
);

const fieldResourceOptions = computed(() =>
  resourceList.value
    .filter((r) => (r.fields ?? []).length > 0)
    .map((r) => ({
      label: `${r.displayName || r.resourceCode} · ${r.resourceType}`,
      value: r.resourceCode,
    })),
);

async function loadResources() {
  if (!canQuery.value) return;
  loading.value = true;
  try {
    const [tree, options] = await Promise.all([
      getAuthorizationResourceTree(),
      getAuthorizationAdminOptions(),
    ]);
    resourceTree.value = tree;
    adminOptions.value = options;
  } catch {
    message.warning('授权资源加载失败');
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadResources();
});

// ─── Tab 1: Function permissions (grants) ───
const grantDrawerOpen = ref(false);
const grantForm = reactive({
  resourceCode: '',
  actionCode: '',
  subjectType: 'ROLE' as string,
  subjectId: '',
  effect: 'ALLOW' as string,
  description: '',
});
const grantRows = ref<SystemAuthorizationApi.AuthorizationGrant[]>([]);

const grantColumns: TableProps['columns'] = [
  { title: '资源', dataIndex: 'resourceCode', key: 'resourceCode', width: 200 },
  { title: '动作', dataIndex: 'actionCode', key: 'actionCode', width: 120 },
  { title: '主体类型', dataIndex: 'subjectType', key: 'subjectType', width: 110 },
  { title: '主体ID', dataIndex: 'subjectId', key: 'subjectId', width: 160 },
  { title: '效果', dataIndex: 'effect', key: 'effect', width: 100 },
  { title: '操作', key: 'action', width: 120 },
];

const subjectTypeOptions = [
  { label: '角色 (ROLE)', value: 'ROLE' },
  { label: '用户 (USER)', value: 'USER' },
];

function openGrantDrawer() {
  grantForm.resourceCode = resourceOptions.value[0]?.value ?? '';
  grantForm.actionCode = '';
  grantForm.subjectType = 'ROLE';
  grantForm.subjectId = '';
  grantForm.effect = 'ALLOW';
  grantForm.description = '';
  grantDrawerOpen.value = true;
}

function asStringValue(value: unknown) {
  return typeof value === 'string' ? value : String(value ?? '');
}

function changeGrantResource(value: unknown) {
  const resourceCode = asStringValue(value);
  grantForm.resourceCode = resourceCode;
  grantForm.actionCode = '';
}

async function handleSaveGrant() {
  if (!grantForm.resourceCode || !grantForm.actionCode || !grantForm.subjectId) {
    message.warning('请填写完整信息');
    return;
  }
  saving.value = true;
  try {
    await saveAuthorizationGrant({
      resourceCode: grantForm.resourceCode,
      actionCode: grantForm.actionCode,
      subjectType: grantForm.subjectType as 'ROLE' | 'USER',
      subjectId: grantForm.subjectId,
      effect: grantForm.effect as 'ALLOW' | 'DENY',
    });
    message.success('授权已保存');
    grantDrawerOpen.value = false;
    await loadGrants();
  } catch {
    message.error('保存授权失败');
  } finally {
    saving.value = false;
  }
}

async function handleDeleteGrant(record: SystemAuthorizationApi.AuthorizationGrant) {
  if (!record.id) return;
  try {
    await deleteAuthorizationGrant(record.id);
    message.success('授权已删除');
    await loadGrants();
  } catch {
    message.error('删除授权失败');
  }
}

async function loadGrants() {
  if (!canQuery.value) return;
  const firstResource = resourceList.value[0];
  if (!firstResource) return;
  try {
    const profile = await getRoleAuthorizationProfile('R001');
    grantRows.value = profile.functionGrants ?? [];
  } catch {
    grantRows.value = [];
  }
}

// ─── Tab 2: Field policies ───
const fieldDrawerOpen = ref(false);
const fieldForm = reactive({
  resourceCode: '',
  fieldKey: '',
  subjectType: 'ROLE' as string,
  subjectId: '',
  readMode: '',
  writeMode: '',
  maskStrategy: '',
  description: '',
});
const fieldRows = ref<SystemAuthorizationApi.FieldPolicy[]>([]);

const readModeOptions = computed(() =>
  (adminOptions.value?.fieldReadModes ?? []).map((o) => ({
    label: o.label || o.code,
    value: o.code,
  })),
);
const writeModeOptions = computed(() =>
  (adminOptions.value?.fieldWriteModes ?? []).map((o) => ({
    label: o.label || o.code,
    value: o.code,
  })),
);
const maskStrategyOptions = computed(() =>
  (adminOptions.value?.maskStrategies ?? []).map((o) => ({
    label: o.label || o.code,
    value: o.code,
  })),
);

const fieldColumns: TableProps['columns'] = [
  { title: '资源', dataIndex: 'resourceCode', key: 'resourceCode', width: 200 },
  { title: '字段', dataIndex: 'fieldKey', key: 'fieldKey', width: 140 },
  { title: '主体', dataIndex: 'subjectId', key: 'subjectId', width: 140 },
  { title: '读取模式', dataIndex: 'readMode', key: 'readMode', width: 110 },
  { title: '写入模式', dataIndex: 'writeMode', key: 'writeMode', width: 110 },
  { title: '脱敏策略', dataIndex: 'maskStrategy', key: 'maskStrategy', width: 120 },
  { title: '操作', key: 'action', width: 100 },
];

function changeFieldResource(value: unknown) {
  const resourceCode = asStringValue(value);
  fieldForm.resourceCode = resourceCode;
  fieldForm.fieldKey = '';
}

function openFieldDrawer() {
  const first = fieldResourceOptions.value[0];
  fieldForm.resourceCode = first?.value ?? '';
  fieldForm.fieldKey = '';
  fieldForm.subjectType = 'ROLE';
  fieldForm.subjectId = '';
  fieldForm.readMode = adminOptions.value?.fieldReadModes?.[0]?.code ?? 'VISIBLE';
  fieldForm.writeMode = adminOptions.value?.fieldWriteModes?.[0]?.code ?? 'EDITABLE';
  fieldForm.maskStrategy = '';
  fieldForm.description = '';
  fieldDrawerOpen.value = true;
}

async function handleSaveField() {
  if (!fieldForm.resourceCode || !fieldForm.fieldKey || !fieldForm.subjectId) {
    message.warning('请填写完整信息');
    return;
  }
  saving.value = true;
  try {
    await saveAuthorizationFieldPolicy({
      effect: 'ALLOW',
      resourceCode: fieldForm.resourceCode,
      fieldKey: fieldForm.fieldKey,
      subjectType: fieldForm.subjectType as 'ROLE' | 'USER',
      subjectId: fieldForm.subjectId,
      readMode: fieldForm.readMode,
      writeMode: fieldForm.writeMode,
      maskStrategy: fieldForm.maskStrategy || undefined,
    });
    message.success('字段策略已保存');
    fieldDrawerOpen.value = false;
    await loadFieldPolicies();
  } catch {
    message.error('保存字段策略失败');
  } finally {
    saving.value = false;
  }
}

async function handleDeleteField(record: SystemAuthorizationApi.FieldPolicy) {
  if (!record.id) return;
  try {
    await deleteAuthorizationFieldPolicy(record.id);
    message.success('字段策略已删除');
    await loadFieldPolicies();
  } catch {
    message.error('删除字段策略失败');
  }
}

async function loadFieldPolicies() {
  if (!canQuery.value) return;
  try {
    const profile = await getRoleAuthorizationProfile('R001');
    fieldRows.value = profile.fieldPolicies ?? [];
  } catch {
    fieldRows.value = [];
  }
}

// ─── Tab 3: Guard templates ───
const guardDrawerOpen = ref(false);
const guardForm = reactive({
  guardCode: '',
  ownerService: '',
  supportedResourceTypes: '',
  description: '',
  configSchemaJson: '',
  status: 1 as number,
});
const guardRows = ref<SystemAuthorizationApi.GuardTemplate[]>([]);

const guardColumns: TableProps['columns'] = [
  { title: '守卫代码', dataIndex: 'guardCode', key: 'guardCode', width: 180 },
  { title: '所属服务', dataIndex: 'ownerService', key: 'ownerService', width: 180 },
  {
    title: '支持资源类型',
    dataIndex: 'supportedResourceTypes',
    key: 'supportedResourceTypes',
    width: 200,
  },
  { title: '描述', dataIndex: 'description', key: 'description', width: 240, ellipsis: true },
  { title: '状态', dataIndex: 'status', key: 'status', width: 90 },
  { title: '操作', key: 'action', width: 180 },
];

function openGuardDrawer() {
  guardForm.guardCode = '';
  guardForm.ownerService = '';
  guardForm.supportedResourceTypes = '';
  guardForm.description = '';
  guardForm.configSchemaJson = '';
  guardForm.status = 1;
  guardDrawerOpen.value = true;
}

async function handleSaveGuard() {
  if (!guardForm.guardCode || !guardForm.ownerService) {
    message.warning('请填写守卫代码和所属服务');
    return;
  }
  saving.value = true;
  try {
    await saveAuthorizationGuardTemplate({
      guardCode: guardForm.guardCode,
      ownerService: guardForm.ownerService,
      supportedResourceTypes: guardForm.supportedResourceTypes || undefined,
      description: guardForm.description || undefined,
      configSchemaJson: guardForm.configSchemaJson || undefined,
    });
    message.success('守卫模板已保存');
    guardDrawerOpen.value = false;
    await loadGuards();
  } catch {
    message.error('保存守卫模板失败');
  } finally {
    saving.value = false;
  }
}

async function handleToggleGuard(
  record: SystemAuthorizationApi.GuardTemplate,
  checked: boolean | number | string,
) {
  if (!record.id) return;
  const enabled = checked === true || checked === 1 || checked === '1';
  saving.value = true;
  try {
    await updateAuthorizationGuardTemplateStatus(record.id, enabled ? 1 : 0);
    message.success(enabled ? '已启用' : '已禁用');
    await loadGuards();
  } catch {
    message.error('更新状态失败');
  } finally {
    saving.value = false;
  }
}

async function handleResetGuard(record: SystemAuthorizationApi.GuardTemplate) {
  if (!record.id) return;
  try {
    await updateAuthorizationGuardTemplateStatus(record.id, 1);
    message.success('守卫模板已重置为启用');
    await loadGuards();
  } catch {
    message.error('重置失败');
  }
}

async function loadGuards() {
  if (!canQuery.value) return;
  guardRows.value = adminOptions.value?.guardTemplates ?? [];
}

// ─── Tab 4: Decision Preview ───
const previewForm = reactive({
  resourceCode: '',
  actionCode: '',
  userId: '',
  businessObjectId: '',
  tenantId: 'default',
});
const previewResult = ref<SystemAuthorizationApi.DecisionPreview | null>(null);
const previewLoading = ref(false);

function changePreviewResource(value: unknown) {
  const resourceCode = asStringValue(value);
  previewForm.resourceCode = resourceCode;
  previewForm.actionCode = '';
  previewResult.value = null;
}

async function handlePreview() {
  if (!previewForm.resourceCode || !previewForm.actionCode || !previewForm.userId) {
    message.warning('请填写完整的决策预览参数');
    return;
  }
  previewLoading.value = true;
  previewResult.value = null;
  try {
    const result = await previewAuthorizationDecision({
      resourceCode: previewForm.resourceCode,
      actionCode: previewForm.actionCode,
      userId: previewForm.userId,
      businessObjectId: previewForm.businessObjectId || undefined,
      tenantId: previewForm.tenantId,
    });
    previewResult.value = result;
  } catch {
    message.error('决策预览请求失败');
  } finally {
    previewLoading.value = false;
  }
}

// Helpers
function statusTagColor(status?: number) {
  return status === 1 ? 'success' : 'default';
}

function effectTagColor(effect?: string) {
  return effect === 'DENY' ? 'error' : 'success';
}

function readModeColor(mode?: string) {
  if (mode === 'VISIBLE') return 'success';
  if (mode === 'MASKED') return 'warning';
  return 'error';
}

// ─── Tab switching ───
function onTabChange(key: number | string) {
  const nextKey = String(key);
  if (nextKey === 'function') loadGrants();
  if (nextKey === 'field') loadFieldPolicies();
  if (nextKey === 'guard') loadGuards();
}
</script>

<template>
  <Page>
    <BusinessPageScaffold class="authz-page" pattern="master-detail">
      <template #toolbar>
        <CompactToolbar title="授权管理" subtitle="统一管理功能授权、字段策略、守卫模板和决策预览" />
      </template>
      <Tabs default-active-key="function" @change="onTabChange">
        <!-- Tab 1: 功能权限 -->
        <TabPane key="function" tab="功能权限">
          <div class="flex items-center justify-between mb-3">
            <span class="text-muted-foreground text-sm">按资源 + 动作授权给角色或用户</span>
            <Space v-if="canCreate">
              <Button @click="loadGrants" class="!w-8 !h-8">
                <IconifyIcon icon="mdi:refresh" />
              </Button>
              <Button type="primary" @click="openGrantDrawer">
                <IconifyIcon icon="mdi:plus" class="mr-1" />新增授权
              </Button>
            </Space>
          </div>
          <CompactTableFrame>
            <Table
            :columns="grantColumns"
            :data-source="grantRows"
            :loading="loading"
            :pagination="{ pageSize: 20, showSizeChanger: true }"
            row-key="id"
            size="small"
            bordered
          >
            <template #bodyCell="{ column, record }: any">
              <template v-if="column.key === 'effect'">
                <Tag :color="effectTagColor(record.effect)">{{ record.effect }}</Tag>
              </template>
              <template v-else-if="column.key === 'action' && canDelete">
                <Popconfirm title="确认删除此授权?" @confirm="handleDeleteGrant(record)">
                  <Button type="link" danger size="small" @click.stop>删除</Button>
                </Popconfirm>
              </template>
            </template>
          </Table>
          </CompactTableFrame>
        </TabPane>

        <!-- Tab 2: 字段规则 -->
        <TabPane key="field" tab="字段规则">
          <div class="flex items-center justify-between mb-3">
            <span class="text-muted-foreground text-sm">按字段配置读写权限和脱敏策略</span>
            <Space v-if="canCreate">
              <Button @click="loadFieldPolicies" class="!w-8 !h-8">
                <IconifyIcon icon="mdi:refresh" />
              </Button>
              <Button type="primary" @click="openFieldDrawer">
                <IconifyIcon icon="mdi:plus" class="mr-1" />新增字段策略
              </Button>
            </Space>
          </div>
          <CompactTableFrame>
            <Table
            :columns="fieldColumns"
            :data-source="fieldRows"
            :loading="loading"
            :pagination="{ pageSize: 20, showSizeChanger: true }"
            row-key="id"
            size="small"
            bordered
          >
            <template #bodyCell="{ column, record }: any">
              <template v-if="column.key === 'readMode'">
                <Tag :color="readModeColor(record.readMode)">{{ record.readMode }}</Tag>
              </template>
              <template v-else-if="column.key === 'action' && canDelete">
                <Popconfirm title="确认删除?" @confirm="handleDeleteField(record)">
                  <Button type="link" danger size="small" @click.stop>删除</Button>
                </Popconfirm>
              </template>
            </template>
          </Table>
          </CompactTableFrame>
        </TabPane>

        <!-- Tab 3: 守卫模板 -->
        <TabPane key="guard" tab="守卫模板">
          <div class="flex items-center justify-between mb-3">
            <span class="text-muted-foreground text-sm">管理运行时守卫模板的启用/禁用</span>
            <Space v-if="canCreate">
              <Button @click="loadGuards" class="!w-8 !h-8">
                <IconifyIcon icon="mdi:refresh" />
              </Button>
              <Button type="primary" @click="openGuardDrawer">
                <IconifyIcon icon="mdi:plus" class="mr-1" />新增守卫
              </Button>
            </Space>
          </div>
          <CompactTableFrame>
            <Table
            :columns="guardColumns"
            :data-source="guardRows"
            :loading="loading"
            :pagination="{ pageSize: 20, showSizeChanger: true }"
            row-key="id"
            size="small"
            bordered
          >
            <template #bodyCell="{ column, record }: any">
              <template v-if="column.key === 'status'">
                <Tag :color="statusTagColor(record.status)">{{
                  record.status === 1 ? '启用' : '禁用'
                }}</Tag>
              </template>
              <template v-else-if="column.key === 'action'">
                <Space>
                  <Switch
                    v-if="canUpdate"
                    :checked="record.status === 1"
                    checked-children="启用"
                    un-checked-children="禁用"
                    :loading="saving"
                    @change="(checked) => handleToggleGuard(record, checked)"
                  />
                  <Popconfirm
                    v-if="canDelete"
                    title="确认重置此守卫?"
                    @confirm="handleResetGuard(record)"
                  >
                    <Button type="link" size="small" @click.stop>重置</Button>
                  </Popconfirm>
                </Space>
              </template>
            </template>
          </Table>
          </CompactTableFrame>
        </TabPane>

        <!-- Tab 4: 决策预览 -->
        <TabPane key="preview" tab="决策预览">
          <div class="mb-4">
            <span class="text-muted-foreground text-sm"
              >模拟授权引擎决策，查看指定用户的资源访问权限</span
            >
          </div>
          <div class="max-w-3xl">
            <Form layout="inline" class="flex flex-wrap gap-3">
              <FormItem label="资源">
                <Select
                  v-model:value="previewForm.resourceCode"
                  :options="resourceOptions"
                  style="width: 260px"
                  placeholder="选择资源"
                  @change="changePreviewResource"
                />
              </FormItem>
              <FormItem label="动作">
                <Select
                  v-model:value="previewForm.actionCode"
                  :options="actionOptionsForResource(previewForm.resourceCode)"
                  style="width: 160px"
                  placeholder="选择动作"
                />
              </FormItem>
              <FormItem label="用户ID">
                <Input
                  v-model:value="previewForm.userId"
                  placeholder="输入用户ID"
                  style="width: 180px"
                />
              </FormItem>
              <FormItem label="业务对象ID">
                <Input
                  v-model:value="previewForm.businessObjectId"
                  placeholder="选填"
                  style="width: 160px"
                />
              </FormItem>
              <FormItem>
                <Button type="primary" :loading="previewLoading" @click="handlePreview">
                  执行预览
                </Button>
              </FormItem>
            </Form>

            <div
              v-if="previewResult"
              class="mt-6 border rounded-lg p-4 bg-gray-50 dark:bg-gray-900"
            >
              <h4 class="font-semibold mb-3 text-base">决策结果</h4>
              <div class="grid grid-cols-2 gap-y-2 text-sm">
                <div>
                  <span class="text-muted-foreground">结果:</span>
                  <Tag
                    :color="previewResult.allowed ? 'success' : 'error'"
                    class="ml-2"
                  >
                    {{ previewResult.allowed ? 'ALLOW' : 'DENY' }}
                  </Tag>
                </div>
                <div>
                  <span class="text-muted-foreground">效果:</span>
                  {{ previewResult.effect || '-' }}
                </div>
                <div>
                  <span class="text-muted-foreground">匹配授权ID:</span>
                  {{ previewResult.matchedGrantId || '-' }}
                </div>
                <div>
                  <span class="text-muted-foreground">数据范围:</span>
                  {{ previewResult.dataScope?.scopeTypes?.join(', ') || '-' }}
                </div>
              </div>

              <div v-if="previewResult.reasons?.length" class="mt-3">
                <h5 class="font-medium mb-1">决策原因:</h5>
                <ul class="list-disc pl-5 text-sm space-y-1">
                  <li v-for="(r, i) in previewResult.reasons" :key="i">
                    <Tag v-if="r.code" class="mr-1">{{ r.code }}</Tag>
                    {{ r.message || r.source || '-' }}
                  </li>
                </ul>
              </div>

              <div v-if="previewResult.fieldRules?.length" class="mt-3">
                <h5 class="font-medium mb-1">字段规则:</h5>
                <Table
                  :columns="[
                    { title: '字段', dataIndex: 'fieldKey', key: 'fieldKey' },
                    { title: '读取', dataIndex: 'readMode', key: 'readMode' },
                    { title: '写入', dataIndex: 'writeMode', key: 'writeMode' },
                    { title: '脱敏', dataIndex: 'maskStrategy', key: 'maskStrategy' },
                  ]"
                  :data-source="previewResult.fieldRules"
                  :pagination="false"
                  size="small"
                  row-key="fieldKey"
                >
                  <template #bodyCell="{ column, record }: any">
                    <template v-if="column.key === 'readMode'">
                      <Tag :color="readModeColor(record.readMode)">{{ record.readMode }}</Tag>
                    </template>
                  </template>
                </Table>
              </div>

              <div v-if="previewResult.guardRequirements?.length" class="mt-3">
                <h5 class="font-medium mb-1">守卫需求:</h5>
                <ul class="list-disc pl-5 text-sm space-y-1">
                  <li v-for="(g, i) in previewResult.guardRequirements" :key="i">
                    {{ g.guardCode }} — {{ g.description || '' }}
                    <Tag>{{ g.ownerService }}</Tag>
                  </li>
                </ul>
              </div>
            </div>
          </div>
        </TabPane>
      </Tabs>
    </BusinessPageScaffold>

    <!-- Grant drawer -->
    <Drawer v-model:open="grantDrawerOpen" title="新增授权" :width="500">
      <Form layout="vertical">
        <FormItem label="资源">
          <Select
            v-model:value="grantForm.resourceCode"
            :options="resourceOptions"
            @change="changeGrantResource"
          />
        </FormItem>
        <FormItem label="动作">
          <Select
            v-model:value="grantForm.actionCode"
            :options="actionOptionsForResource(grantForm.resourceCode)"
          />
        </FormItem>
        <FormItem label="主体类型">
          <Select v-model:value="grantForm.subjectType" :options="subjectTypeOptions" />
        </FormItem>
        <FormItem label="主体ID">
          <Input v-model:value="grantForm.subjectId" placeholder="输入角色编码或用户ID" />
        </FormItem>
        <FormItem label="效果">
          <Select
            v-model:value="grantForm.effect"
            :options="[
              { label: 'ALLOW', value: 'ALLOW' },
              { label: 'DENY', value: 'DENY' },
            ]"
          />
        </FormItem>
        <FormItem label="描述">
          <Input.TextArea v-model:value="grantForm.description" :rows="2" />
        </FormItem>
      </Form>
      <template #footer>
        <Button @click="grantDrawerOpen = false">取消</Button>
        <Button type="primary" :loading="saving" @click="handleSaveGrant">保存</Button>
      </template>
    </Drawer>

    <!-- Field policy drawer -->
    <Drawer v-model:open="fieldDrawerOpen" title="新增字段策略" :width="500">
      <Form layout="vertical">
        <FormItem label="资源">
          <Select
            v-model:value="fieldForm.resourceCode"
            :options="fieldResourceOptions"
            @change="changeFieldResource"
          />
        </FormItem>
        <FormItem label="字段">
          <Select
            v-model:value="fieldForm.fieldKey"
            :options="fieldOptionsForResource(fieldForm.resourceCode)"
          />
        </FormItem>
        <FormItem label="主体类型">
          <Select v-model:value="fieldForm.subjectType" :options="subjectTypeOptions" />
        </FormItem>
        <FormItem label="主体ID">
          <Input v-model:value="fieldForm.subjectId" placeholder="输入角色编码或用户ID" />
        </FormItem>
        <FormItem label="读取模式">
          <Select v-model:value="fieldForm.readMode" :options="readModeOptions" />
        </FormItem>
        <FormItem label="写入模式">
          <Select v-model:value="fieldForm.writeMode" :options="writeModeOptions" />
        </FormItem>
        <FormItem label="脱敏策略">
          <Select
            v-model:value="fieldForm.maskStrategy"
            :options="maskStrategyOptions"
            allow-clear
          />
        </FormItem>
        <FormItem label="描述">
          <Input.TextArea v-model:value="fieldForm.description" :rows="2" />
        </FormItem>
      </Form>
      <template #footer>
        <Button @click="fieldDrawerOpen = false">取消</Button>
        <Button type="primary" :loading="saving" @click="handleSaveField">保存</Button>
      </template>
    </Drawer>

    <!-- Guard template drawer -->
    <Drawer v-model:open="guardDrawerOpen" title="新增守卫模板" :width="500">
      <Form layout="vertical">
        <FormItem label="守卫代码" required>
          <Input v-model:value="guardForm.guardCode" placeholder="如 NO_SELF_APPROVAL" />
        </FormItem>
        <FormItem label="所属服务" required>
          <Input v-model:value="guardForm.ownerService" placeholder="如 service-workflow-engine" />
        </FormItem>
        <FormItem label="支持资源类型">
          <Input
            v-model:value="guardForm.supportedResourceTypes"
            placeholder="如 LOWCODE_FORM,WORKFLOW_TASK"
          />
        </FormItem>
        <FormItem label="描述">
          <Input.TextArea v-model:value="guardForm.description" :rows="2" />
        </FormItem>
        <FormItem label="配置 JSON Schema">
          <Input.TextArea v-model:value="guardForm.configSchemaJson" :rows="3" placeholder="选填" />
        </FormItem>
      </Form>
      <template #footer>
        <Button @click="guardDrawerOpen = false">取消</Button>
        <Button type="primary" :loading="saving" @click="handleSaveGuard">保存</Button>
      </template>
    </Drawer>
  </Page>
</template>

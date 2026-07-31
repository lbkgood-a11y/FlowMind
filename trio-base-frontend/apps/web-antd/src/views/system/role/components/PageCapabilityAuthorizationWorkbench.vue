<script setup lang="ts">
import type { SystemAuthorizationApi, SystemOrgApi, SystemUserApi } from '#/api';

import { computed, onMounted, reactive, ref } from 'vue';

import {
  Alert,
  Button,
  Checkbox,
  Collapse,
  CollapsePanel,
  Empty,
  Input,
  message,
  Modal,
  Radio,
  RadioGroup,
  Select,
  Space,
  Spin,
  Tag,
  Tooltip,
} from 'ant-design-vue';

import {
  getOrCreateRoleAuthorizationDraft,
  getOrgDimensions,
  getOrgTree,
  getPageCapabilities,
  getRoleAuthorizationReleases,
  getRoleAuthorizationDrifts,
  getUserList,
  publishRoleAuthorizationDraft,
  replaceRoleCapabilityIntent,
  rollbackRoleAuthorizationRelease,
  simulatePageCapability,
  validateRoleAuthorizationDraft,
} from '#/api';

const props = defineProps<{
  canManage: boolean;
  roleId: string;
}>();

const emit = defineEmits<{ published: [] }>();

type Selection = SystemAuthorizationApi.RoleCapabilitySelection;
type FieldRule = {
  fieldKey: string;
  maskStrategy?: string;
  readMode: 'HIDDEN' | 'MASKED' | 'VISIBLE';
  writeMode: 'DENIED' | 'READ_ONLY' | 'WRITABLE';
};

const loading = ref(true);
const saving = ref(false);
const validating = ref(false);
const publishing = ref(false);
const dirty = ref(false);
const capabilities = ref<SystemAuthorizationApi.PageCapability[]>([]);
const draft = ref<SystemAuthorizationApi.RoleAuthorizationDraft>();
const validation = ref<SystemAuthorizationApi.RoleAuthorizationValidation>();
const releases = ref<SystemAuthorizationApi.RoleAuthorizationRelease[]>([]);
const drifts = ref<SystemAuthorizationApi.RoleAuthorizationDrift[]>([]);
const activePageKeys = ref<string[]>([]);
const simulationLoading = ref(false);
const simulationResult = ref<SystemAuthorizationApi.PageCapabilitySimulation>();
const simulationUsers = ref<SystemUserApi.SystemUser[]>([]);
const organizationOptions = ref<Array<{ label: string; value: string }>>([]);
const simulationForm = reactive({
  businessObjectId: '',
  capabilityId: '',
  mode: 'ROLE' as 'ROLE' | 'USER',
  userId: '',
});
const selectionById = reactive<Record<string, Selection>>({});
const fieldRulesByCapability = reactive<Record<string, Record<string, FieldRule>>>({});
const loadedCapabilityIds = ref(new Set<string>());

const scopeOptions = [
  { label: '不能查看任何数据', value: 'NONE' },
  { label: '仅本人数据', value: 'SELF' },
  { label: '本部门数据', value: 'OWN_ORG' },
  { label: '本部门及下级部门', value: 'OWN_ORG_AND_CHILDREN' },
  { label: '指定组织', value: 'ASSIGNED_ORGS' },
  { label: '全部数据', value: 'ALL' },
];
const fieldReadOptions = [
  { label: '正常显示', value: 'VISIBLE' },
  { label: '脱敏显示', value: 'MASKED' },
  { label: '完全隐藏', value: 'HIDDEN' },
];
const fieldWriteOptions = [
  { label: '允许填写和修改', value: 'WRITABLE' },
  { label: '只读', value: 'READ_ONLY' },
  { label: '禁止提交', value: 'DENIED' },
];
const maskOptions = [
  { label: '通用脱敏', value: 'DEFAULT' },
  { label: '手机号脱敏', value: 'PHONE' },
  { label: '邮箱脱敏', value: 'EMAIL' },
  { label: '姓名脱敏', value: 'NAME' },
];

const pages = computed(() => {
  const grouped = new Map<string, {
    access: SystemAuthorizationApi.PageCapability[];
    name: string;
    operations: SystemAuthorizationApi.PageCapability[];
    pageCode: string;
    reads: SystemAuthorizationApi.PageCapability[];
  }>();
  for (const capability of capabilities.value) {
    const page = grouped.get(capability.pageCode) ?? {
      access: [],
      name: capability.pageName,
      operations: [],
      pageCode: capability.pageCode,
      reads: [],
    };
    if (capability.category === 'ACCESS') page.access.push(capability);
    if (capability.category === 'READ') page.reads.push(capability);
    if (capability.category === 'OPERATION') page.operations.push(capability);
    grouped.set(capability.pageCode, page);
  }
  return [...grouped.values()];
});

const selectedCount = computed(() => Object.keys(selectionById).length);
const selectedPages = computed(() =>
  pages.value.filter((page) =>
    [...page.access, ...page.reads, ...page.operations].some((item) => isSelected(item.id)),
  ),
);
const selectedCapabilityOptions = computed(() =>
  capabilities.value
    .filter((item) => isSelected(item.id))
    .map((item) => ({ label: `${item.pageName} · ${item.capabilityName}`, value: item.id })),
);
const simulationUserOptions = computed(() =>
  simulationUsers.value.map((user) => ({
    label: user.username,
    value: user.id,
  })),
);
const statusLabel = computed(() => {
  const labels: Record<string, string> = {
    DRAFT: '草稿待校验',
    FAILED: '发布失败，可修正后重试',
    PUBLISHED: '已发布',
    PUBLISHING: '正在发布',
    VALIDATED: '校验已通过，等待发布',
  };
  return labels[draft.value?.status ?? 'DRAFT'] ?? '草稿';
});

function isSelected(capabilityId: string) {
  return Boolean(selectionById[capabilityId]);
}

function capabilityName(capabilityId: string) {
  return capabilities.value.find((item) => item.id === capabilityId)?.capabilityName ?? '必需功能';
}

function selectedDependents(capabilityId: string) {
  return capabilities.value.filter(
    (item) => isSelected(item.id) && item.requiredCapabilityIds?.includes(capabilityId),
  );
}

function addWithDependencies(capability: SystemAuthorizationApi.PageCapability, explicit = true) {
  const existing = selectionById[capability.id];
  selectionById[capability.id] = {
    ...existing,
    capabilityId: capability.id,
    selectionSource: explicit ? 'EXPLICIT' : existing?.selectionSource ?? 'DEPENDENCY',
  };
  for (const requiredId of capability.requiredCapabilityIds ?? []) {
    const required = capabilities.value.find((item) => item.id === requiredId);
    if (required) addWithDependencies(required, false);
  }
}

function toggleCapability(capability: SystemAuthorizationApi.PageCapability, checked: boolean) {
  if (!props.canManage || capability.readiness !== 'READY') return;
  if (checked) {
    addWithDependencies(capability);
  } else {
    const dependents = selectedDependents(capability.id);
    if (dependents.length) {
      message.warning(`请先取消“${dependents.map((item) => item.capabilityName).join('、')}”，它们需要“${capability.capabilityName}”`);
      return;
    }
    delete selectionById[capability.id];
  }
  markChanged();
}

function markChanged() {
  dirty.value = true;
  validation.value = undefined;
  if (draft.value?.status === 'VALIDATED') draft.value.status = 'DRAFT';
}

function fieldRule(capabilityId: string, fieldKey: string) {
  return fieldRulesByCapability[capabilityId]?.[fieldKey];
}

function syncFieldIntent(capabilityId: string) {
  const selection = selectionById[capabilityId];
  if (!selection) return;
  const rules = Object.values(fieldRulesByCapability[capabilityId] ?? {});
  selection.fieldIntentJson = rules.length ? JSON.stringify(rules) : undefined;
  markChanged();
}

function toggleFieldRule(capabilityId: string, fieldKey: string, checked: boolean) {
  const rules = fieldRulesByCapability[capabilityId] ?? {};
  fieldRulesByCapability[capabilityId] = rules;
  if (checked) {
    rules[fieldKey] = { fieldKey, readMode: 'HIDDEN', writeMode: 'DENIED' };
  } else {
    delete rules[fieldKey];
  }
  syncFieldIntent(capabilityId);
}

function updateFieldRule(capabilityId: string, fieldKey: string, changes: Partial<FieldRule>) {
  const rule = fieldRule(capabilityId, fieldKey);
  if (!rule) return;
  Object.assign(rule, changes);
  if (rule.readMode !== 'MASKED') rule.maskStrategy = undefined;
  if (rule.readMode === 'MASKED' && !rule.maskStrategy) rule.maskStrategy = 'DEFAULT';
  syncFieldIntent(capabilityId);
}

function updateDefaultScope(capabilityId: string, value: string) {
  const selection = selectionById[capabilityId];
  if (!selection) return;
  selection.defaultScopeType = value;
  if (value !== 'ASSIGNED_ORGS') selection.defaultScopeIds = [];
  markChanged();
}

function updateDefaultOrganizations(capabilityId: string, values: string[]) {
  const selection = selectionById[capabilityId];
  if (!selection) return;
  selection.defaultScopeIds = values;
  markChanged();
}

function updateOperationScope(capabilityId: string, value?: string) {
  const selection = selectionById[capabilityId];
  if (!selection) return;
  selection.operationScopeType = value || undefined;
  if (value !== 'ASSIGNED_ORGS') selection.operationScopeIds = [];
  markChanged();
}

function updateOperationOrganizations(capabilityId: string, values: string[]) {
  const selection = selectionById[capabilityId];
  if (!selection) return;
  selection.operationScopeIds = values;
  markChanged();
}

function flattenOrganizations(nodes: SystemOrgApi.OrgTreeNode[]) {
  const result: SystemOrgApi.OrgTreeNode[] = [];
  const walk = (items: SystemOrgApi.OrgTreeNode[]) => {
    for (const item of items) {
      result.push(item);
      if (item.children?.length) walk(item.children);
    }
  };
  walk(nodes);
  return result;
}

async function loadOrganizations() {
  try {
    const dimensions = await getOrgDimensions();
    const dimension = dimensions.find((item) => item.isDefault === 1) ?? dimensions[0];
    if (!dimension) return;
    const tree = await getOrgTree(dimension.dimensionCode);
    organizationOptions.value = flattenOrganizations(tree).map((item) => ({
      label: `${item.unitName}（${item.unitCode}）`,
      value: item.id,
    }));
  } catch {
    organizationOptions.value = [];
  }
}

function applyDraft(next: SystemAuthorizationApi.RoleAuthorizationDraft) {
  draft.value = next;
  for (const key of Object.keys(selectionById)) delete selectionById[key];
  for (const key of Object.keys(fieldRulesByCapability)) delete fieldRulesByCapability[key];
  for (const selection of next.selections ?? []) {
    selectionById[selection.capabilityId] = { ...selection };
    if (selection.fieldIntentJson) {
      try {
        const rules = JSON.parse(selection.fieldIntentJson) as FieldRule[];
        fieldRulesByCapability[selection.capabilityId] = Object.fromEntries(
          rules.map((rule) => [rule.fieldKey, rule]),
        );
      } catch {
        fieldRulesByCapability[selection.capabilityId] = {};
      }
    }
  }
  loadedCapabilityIds.value = new Set(next.selections.map((item) => item.capabilityId));
  dirty.value = false;
}

async function load() {
  loading.value = true;
  try {
    const [catalog, currentDraft, history, driftItems, users] = await Promise.all([
      getPageCapabilities(),
      getOrCreateRoleAuthorizationDraft(props.roleId),
      getRoleAuthorizationReleases(props.roleId),
      getRoleAuthorizationDrifts(props.roleId),
      getUserList({ page: 1, size: 100, status: 1 }),
    ]);
    capabilities.value = catalog;
    activePageKeys.value = [...new Set(catalog.map((item) => item.pageCode))];
    releases.value = history;
    drifts.value = driftItems;
    simulationUsers.value = users.items;
    applyDraft(currentDraft);
    await loadOrganizations();
  } finally {
    loading.value = false;
  }
}

async function runSimulation() {
  if (!simulationForm.capabilityId) {
    message.warning('请选择要验证的页面功能');
    return;
  }
  if (simulationForm.mode === 'USER' && !simulationForm.userId) {
    message.warning('请从用户列表中选择实际用户');
    return;
  }
  simulationLoading.value = true;
  simulationResult.value = undefined;
  try {
    simulationResult.value = await simulatePageCapability(simulationForm.capabilityId, {
      businessObjectId: simulationForm.businessObjectId || undefined,
      mode: simulationForm.mode,
      roleId: simulationForm.mode === 'ROLE' ? props.roleId : undefined,
      userId: simulationForm.mode === 'USER' ? simulationForm.userId : undefined,
    });
  } finally {
    simulationLoading.value = false;
  }
}

async function saveDraft() {
  if (!draft.value) throw new Error('授权草稿尚未加载');
  if (!dirty.value) return draft.value;
  saving.value = true;
  try {
    const currentIds = new Set(Object.keys(selectionById));
    const removedCapabilityIds = [...loadedCapabilityIds.value].filter((id) => !currentIds.has(id));
    const next = await replaceRoleCapabilityIntent(draft.value.draftId, {
      expectedVersion: draft.value.version,
      removedCapabilityIds,
      selections: Object.values(selectionById),
    });
    applyDraft(next);
    message.success('授权草稿已保存，尚未影响线上用户');
    return next;
  } catch (error) {
    message.error('草稿保存失败，可能已被其他实施人员修改，请重新打开后再试');
    throw error;
  } finally {
    saving.value = false;
  }
}

async function validateDraft() {
  validating.value = true;
  try {
    const current = await saveDraft();
    validation.value = await validateRoleAuthorizationDraft(current.draftId, {
      expectedVersion: current.version,
    });
    if (draft.value) draft.value.status = 'VALIDATED';
    message.success('校验通过，请确认变更摘要后发布');
  } finally {
    validating.value = false;
  }
}

function confirmPublish() {
  if (!draft.value || !validation.value) {
    message.warning('请先完成校验');
    return;
  }
  Modal.confirm({
    content: validation.value.businessSummary,
    okText: '确认发布',
    title: '发布后将立即影响持有该角色的用户',
    async onOk() {
      if (!draft.value || !validation.value) return;
      publishing.value = true;
      try {
        await publishRoleAuthorizationDraft(draft.value.draftId, {
          expectedVersion: draft.value.version,
          validationToken: validation.value.validationToken,
        });
        message.success('角色权限已发布');
        emit('published');
        await load();
      } finally {
        publishing.value = false;
      }
    },
  });
}

function confirmRollback(release: SystemAuthorizationApi.RoleAuthorizationRelease) {
  Modal.confirm({
    content: `将恢复为第 ${release.releaseNumber} 版：${release.businessSummary}`,
    okText: '确认回滚',
    title: '确认恢复历史权限版本',
    async onOk() {
      await rollbackRoleAuthorizationRelease(props.roleId, release.releaseId);
      message.success('历史权限版本已恢复');
      emit('published');
      await load();
    },
  });
}

onMounted(load);
</script>

<template>
  <Spin :spinning="loading">
    <div class="capability-workbench">
      <div class="workbench-header">
        <div>
          <h3>按页面配置角色权限</h3>
          <p>只选择用户能进入的页面、能查看的内容和能执行的操作，后台权限由系统自动完成。</p>
        </div>
        <Space wrap>
          <Tag color="blue">{{ statusLabel }}</Tag>
          <Tag>已选 {{ selectedCount }} 项功能</Tag>
        </Space>
      </div>

      <Alert show-icon type="info">
        <template #message>“可查看”和“可操作”已分开</template>
        <template #description>
          进入页面只决定能否看到入口；查看内容决定能读哪些数据；新增、编辑、删除、导出等操作需要单独勾选。
        </template>
      </Alert>

      <Alert v-if="drifts.length" show-icon type="warning" message="页面功能映射已变化，线上权限尚未自动改变">
        <template #description>
          <div v-for="drift in drifts" :key="drift.driftId">
            {{ drift.impactSummary }}
          </div>
        </template>
      </Alert>

      <Collapse v-model:active-key="activePageKeys" class="page-capability-list">
        <CollapsePanel v-for="page in pages" :key="page.pageCode">
          <template #header>
            <div class="page-heading">
              <strong>{{ page.name }}</strong>
              <Tag v-if="[...page.access, ...page.reads, ...page.operations].some((item) => isSelected(item.id))" color="processing">
                已配置
              </Tag>
            </div>
          </template>

          <section class="capability-section">
            <div class="section-title"><strong>允许进入页面</strong><span>控制菜单入口和页面访问</span></div>
            <div v-for="item in page.access" :key="item.id" class="capability-row">
              <Tooltip :title="item.readiness === 'READY' ? item.helpText : item.readinessMessage">
                <Checkbox
                  :checked="isSelected(item.id)"
                  :disabled="!canManage || item.readiness !== 'READY' || selectedDependents(item.id).length > 0"
                  @change="(event) => toggleCapability(item, event.target.checked)"
                >
                  {{ item.capabilityName }}
                </Checkbox>
              </Tooltip>
              <Tag v-if="selectionById[item.id]?.selectionSource === 'DEPENDENCY'">系统自动添加</Tag>
            </div>
          </section>

          <section class="capability-section">
            <div class="section-title"><strong>允许查看</strong><span>控制页面内容和默认数据范围</span></div>
            <div v-for="item in page.reads" :key="item.id" class="capability-row capability-row-with-config">
              <div>
                <Checkbox
                  :checked="isSelected(item.id)"
                  :disabled="!canManage || item.readiness !== 'READY'"
                  @change="(event) => toggleCapability(item, event.target.checked)"
                >
                  {{ item.capabilityName }}
                </Checkbox>
                <Tag v-if="selectionById[item.id]?.selectionSource === 'DEPENDENCY'">操作需要，系统自动添加</Tag>
              </div>
              <Select
                v-if="isSelected(item.id) && item.scopeConfigurable"
                :value="selectionById[item.id]?.defaultScopeType"
                class="scope-select"
                :disabled="!canManage"
                :options="scopeOptions"
                placeholder="选择该页面默认数据范围"
                @change="(value) => updateDefaultScope(item.id, String(value))"
              />
              <Select
                v-if="isSelected(item.id) && selectionById[item.id]?.defaultScopeType === 'ASSIGNED_ORGS'"
                :value="selectionById[item.id]?.defaultScopeIds"
                class="scope-select"
                :disabled="!canManage"
                mode="multiple"
                :options="organizationOptions"
                placeholder="选择允许查看的组织"
                show-search
                @change="(values) => updateDefaultOrganizations(item.id, values as string[])"
              />
            </div>
            <Empty v-if="page.reads.length === 0" description="该页面没有独立的查看权限" :image="Empty.PRESENTED_IMAGE_SIMPLE" />
          </section>

          <section class="capability-section">
            <div class="section-title"><strong>允许操作</strong><span>每项业务操作单独授权，可按需缩小数据范围</span></div>
            <div v-for="item in page.operations" :key="item.id" class="capability-row capability-row-with-config">
              <div>
                <Checkbox
                  :checked="isSelected(item.id)"
                  :disabled="!canManage || item.readiness !== 'READY'"
                  @change="(event) => toggleCapability(item, event.target.checked)"
                >
                  {{ item.capabilityName }}
                </Checkbox>
                <Tag v-if="item.requiredCapabilityIds?.length" color="default">
                  同时需要 {{ item.requiredCapabilityIds.map(capabilityName).join('、') }}
                </Tag>
              </div>
              <Select
                v-if="isSelected(item.id) && item.scopeConfigurable"
                allow-clear
                :value="selectionById[item.id]?.operationScopeType"
                class="scope-select"
                :disabled="!canManage"
                :options="scopeOptions"
                placeholder="跟随页面默认范围"
                @change="(value) => updateOperationScope(item.id, value ? String(value) : undefined)"
              />
              <Select
                v-if="isSelected(item.id) && selectionById[item.id]?.operationScopeType === 'ASSIGNED_ORGS'"
                :value="selectionById[item.id]?.operationScopeIds"
                class="scope-select"
                :disabled="!canManage"
                mode="multiple"
                :options="organizationOptions"
                placeholder="选择该操作允许使用的组织"
                show-search
                @change="(values) => updateOperationOrganizations(item.id, values as string[])"
              />
            </div>
          </section>

          <section v-if="[...page.reads, ...page.operations].some((item) => item.fieldRestrictionConfigurable)" class="capability-section">
            <div class="section-title"><strong>字段保护</strong><span>仅显示业务服务确认能够执行的字段限制</span></div>
            <template v-for="item in [...page.reads, ...page.operations]" :key="`fields:${item.id}`">
              <div v-if="isSelected(item.id) && item.fieldRestrictionConfigurable" class="field-capability-group">
                <strong>{{ item.capabilityName }}</strong>
                <div v-for="field in item.availableFields" :key="field.fieldKey" class="field-rule-row">
                  <Checkbox
                    :checked="Boolean(fieldRule(item.id, field.fieldKey))"
                    :disabled="!canManage"
                    @change="(event) => toggleFieldRule(item.id, field.fieldKey, event.target.checked)"
                  >
                    限制“{{ field.fieldLabel || field.fieldKey }}”
                  </Checkbox>
                  <template v-if="fieldRule(item.id, field.fieldKey)">
                    <Select
                      :value="fieldRule(item.id, field.fieldKey)?.readMode"
                      class="field-mode-select"
                      :options="fieldReadOptions"
                      @change="(value) => updateFieldRule(item.id, field.fieldKey, { readMode: String(value) as FieldRule['readMode'] })"
                    />
                    <Select
                      v-if="fieldRule(item.id, field.fieldKey)?.readMode === 'MASKED'"
                      :value="fieldRule(item.id, field.fieldKey)?.maskStrategy"
                      class="field-mode-select"
                      :options="maskOptions"
                      @change="(value) => updateFieldRule(item.id, field.fieldKey, { maskStrategy: String(value) })"
                    />
                    <Select
                      :value="fieldRule(item.id, field.fieldKey)?.writeMode"
                      class="field-mode-select"
                      :options="fieldWriteOptions"
                      @change="(value) => updateFieldRule(item.id, field.fieldKey, { writeMode: String(value) as FieldRule['writeMode'] })"
                    />
                  </template>
                </div>
                <Empty v-if="!item.availableFields?.length" description="业务服务尚未登记可限制字段" :image="Empty.PRESENTED_IMAGE_SIMPLE" />
              </div>
            </template>
          </section>

          <section v-if="[...page.reads, ...page.operations].some((item) => isSelected(item.id) && item.constraintConfigurable)" class="capability-section">
            <div class="section-title"><strong>业务限制</strong><span>由业务服务声明并在接口侧强制执行</span></div>
            <Alert message="所选操作包含审批资格、禁止本人审批等业务限制，发布时系统会自动校验并启用。" type="info" show-icon />
          </section>
        </CollapsePanel>
      </Collapse>

      <section class="review-panel">
        <div class="section-title"><strong>发布前确认</strong><span>草稿不会影响线上用户，只有“发布”才会生效</span></div>
        <div v-if="selectedPages.length" class="business-summary">
          <div v-for="page in selectedPages" :key="page.pageCode">
            <strong>{{ page.name }}</strong>
            <span>{{ [...page.access, ...page.reads, ...page.operations].filter((item) => isSelected(item.id)).map((item) => item.capabilityName).join('、') }}</span>
          </div>
        </div>
        <Empty v-else description="该角色尚未选择任何页面功能" :image="Empty.PRESENTED_IMAGE_SIMPLE" />
        <Alert v-if="validation" class="validation-result" show-icon type="success" :message="validation.businessSummary">
          <template #description>
            本次发布将影响 {{ validation.affectedUserCount }} 名用户，生成 {{ validation.compilation.grants.length }} 项运行时授权；校验结果将在 {{ validation.expiresAt }} 前有效。
          </template>
        </Alert>
        <div class="workbench-actions">
          <Space>
            <Button :disabled="!canManage" :loading="saving" @click="saveDraft">保存草稿</Button>
            <Button :disabled="!canManage" :loading="validating" @click="validateDraft">校验权限</Button>
            <Button :disabled="!canManage || !validation" :loading="publishing" type="primary" @click="confirmPublish">确认并发布</Button>
          </Space>
        </div>
      </section>

      <section class="release-panel">
        <div class="section-title"><strong>验证实际效果</strong><span>无需填写用户 ID，从用户列表选择即可</span></div>
        <div class="simulation-form">
          <RadioGroup v-model:value="simulationForm.mode" button-style="solid">
            <Radio value="ROLE">当前线上角色</Radio>
            <Radio value="USER">实际用户</Radio>
          </RadioGroup>
          <Select
            v-if="simulationForm.mode === 'USER'"
            v-model:value="simulationForm.userId"
            class="simulation-user-select"
            :options="simulationUserOptions"
            placeholder="搜索并选择实际用户"
            show-search
          />
          <Select
            v-model:value="simulationForm.capabilityId"
            class="simulation-capability-select"
            :options="selectedCapabilityOptions"
            placeholder="选择要验证的页面功能"
          />
          <Input v-model:value="simulationForm.businessObjectId" class="simulation-object-input" allow-clear placeholder="业务对象编号（可选）" />
          <Button :loading="simulationLoading" type="primary" @click="runSimulation">开始验证</Button>
        </div>
        <Alert
          v-if="simulationResult"
          class="simulation-result"
          show-icon
          :type="simulationResult.allowed ? 'success' : 'error'"
          :message="`${simulationResult.pageName} · ${simulationResult.capabilityName}：${simulationResult.outcome}`"
        >
          <template #description>
            <div>数据范围：{{ simulationResult.dataScopeSummary }}</div>
            <div v-for="reason in simulationResult.reasons" :key="reason">{{ reason }}</div>
            <div v-for="field in simulationResult.fieldSummaries" :key="field">字段：{{ field }}</div>
            <div v-for="guard in simulationResult.guardSummaries" :key="guard">业务限制：{{ guard }}</div>
          </template>
        </Alert>
      </section>

      <section class="release-panel">
        <div class="section-title"><strong>发布历史</strong><span>可恢复任一不可变历史版本</span></div>
        <div v-if="releases.length" class="release-list">
          <div v-for="release in releases" :key="release.releaseId" class="release-row">
            <div><strong>第 {{ release.releaseNumber }} 版</strong><span>{{ release.businessSummary }}</span></div>
            <Space><Tag>{{ release.publishedBy }} · {{ release.publishedAt }}</Tag><Button :disabled="!canManage" size="small" @click="confirmRollback(release)">恢复此版本</Button></Space>
          </div>
        </div>
        <Empty v-else description="尚无已发布版本" :image="Empty.PRESENTED_IMAGE_SIMPLE" />
      </section>
    </div>
  </Spin>
</template>

<style scoped>
.capability-workbench { display: flex; flex-direction: column; gap: 16px; }
.workbench-header, .section-title, .page-heading, .capability-row, .release-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.workbench-header h3 { margin: 0 0 4px; font-size: 18px; }
.workbench-header p, .section-title span { margin: 0; color: var(--ant-color-text-secondary, #666); }
.page-capability-list { background: transparent; }
.page-heading { width: 100%; padding-right: 12px; }
.capability-section, .review-panel, .release-panel { padding: 16px; border: 1px solid var(--ant-color-border-secondary, #f0f0f0); border-radius: 8px; }
.capability-section + .capability-section { margin-top: 12px; }
.section-title { margin-bottom: 12px; }
.capability-row { min-height: 42px; padding: 8px 0; border-top: 1px dashed var(--ant-color-border-secondary, #f0f0f0); }
.capability-row-with-config { align-items: flex-start; }
.scope-select { width: 230px; }
.business-summary, .release-list { display: flex; flex-direction: column; gap: 8px; }
.business-summary > div, .release-row { padding: 10px 12px; background: var(--ant-color-fill-quaternary, #fafafa); border-radius: 6px; }
.business-summary strong, .release-row strong { display: inline-block; min-width: 110px; }
.release-row > div { display: flex; flex-direction: column; gap: 4px; }
.validation-result { margin-top: 12px; }
.simulation-form { display: flex; flex-wrap: wrap; gap: 8px; }
.simulation-user-select { width: 220px; }
.simulation-capability-select { min-width: 260px; flex: 1; }
.simulation-object-input { width: 210px; }
.simulation-result { margin-top: 12px; }
.field-capability-group { display: flex; flex-direction: column; gap: 8px; }
.field-rule-row { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; min-height: 40px; }
.field-rule-row > :first-child { min-width: 220px; }
.field-mode-select { width: 160px; }
.workbench-actions { display: flex; justify-content: flex-end; margin-top: 16px; }
@media (max-width: 900px) { .capability-row, .release-row { align-items: stretch; flex-direction: column; } .scope-select { width: 100%; } }
</style>

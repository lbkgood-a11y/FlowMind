<script setup lang="ts">
import type { TableProps } from 'ant-design-vue';

import type { SystemOrgApi } from '#/api';
import type { ActionApi } from '#/api/action-client';
import type { ProcessApi } from '#/api/process';

import { computed, h, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import {
  Button,
  Divider,
  Drawer,
  Empty,
  Form,
  FormItem,
  Input,
  message,
  Pagination,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Timeline,
  TimelineItem,
  Tooltip,
} from 'ant-design-vue';

import {
  ACTION_TARGET_TYPES,
  ACTION_TYPES,
  createActionIdempotencyKey,
  getActionErrorCode,
  getOrgDimensions,
  getOrgTree,
  requireActionData,
} from '#/api';
import {
  getProcessClosureDetail,
  getProcessHistory,
  getProcessInstanceList,
  getProcessPackageList,
} from '#/api/process';
import {
  isActionDispatchError,
  useActionDispatch,
} from '#/composables/useActionDispatch';
import { useAgentRefreshScopes } from '#/composables/useAgentRefreshScopes';
import {
  BusinessActionButton,
  BusinessPageScaffold,
  refreshByScopes,
  useActionAvailability,
} from '#/shared';

import DynamicProcessForm from '../components/DynamicProcessForm.vue';
import { getProcessFormFields, parseFormSchema } from '../components/process-form';

const PERMISSIONS = {
  history: '/api/v1/process-instances/*/history:GET',
  query: '/api/v1/process-instances:GET',
  start: '/api/v1/process-instances/start:POST',
} as const;

const ORG_PERMISSIONS = {
  query: '/api/v1/org/units:GET',
} as const;

const { hasAccessByCodes } = useAccess();
const { dispatchAction } = useActionDispatch();
const {
  getAvailability,
  loadAvailability: loadActionAvailability,
  loading: actionAvailabilityLoading,
} = useActionAvailability();
const canQuery = computed(() => hasAccessByCodes([PERMISSIONS.query]));
const canStart = computed(() => hasAccessByCodes([PERMISSIONS.start]));
const canHistory = computed(() => hasAccessByCodes([PERMISSIONS.history]));
const canQueryOrg = computed(() => hasAccessByCodes([ORG_PERMISSIONS.query]));

const loading = ref(false);
const saving = ref(false);
const formOpen = ref(false);
const detailOpen = ref(false);
const detailRecord = ref<ProcessApi.ProcessInstance>();
const detailHistory = ref<ProcessApi.ProcessHistory>();
const detailClosure = ref<ProcessApi.ProcessClosureDetail>();
const closureLoading = ref(false);
const historyLoading = ref(false);
const instances = ref<ProcessApi.ProcessInstance[]>([]);
const packageOptions = ref<Array<{ label: string; value: string }>>([]);
const packages = ref<ProcessApi.ProcessPackage[]>([]);
const dynamicFormRef = ref<InstanceType<typeof DynamicProcessForm>>();
const orgDimensions = ref<SystemOrgApi.OrgDimension[]>([]);
const orgTreeRows = ref<SystemOrgApi.OrgTreeNode[]>([]);

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
  orgDimensionCode: 'ADMIN',
  orgUnitId: undefined as string | undefined,
  processPackageId: '',
  title: '',
  formData: {} as Record<string, unknown>,
});

const selectedPackage = computed(() =>
  packages.value.find((item) => item.id === formModel.processPackageId),
);

const historyTimelineItems = computed(() =>
  (detailHistory.value?.nodes ?? []).map((node) => ({
    children: `${node.enteredAt}${node.exitedAt ? ` - ${node.exitedAt}` : ''}`,
    color:
      node.status === 'COMPLETED'
        ? 'green'
        : node.status === 'FAILED'
          ? 'red'
          : 'blue',
    key: `${node.id}:${node.visitNo}`,
    label: `${node.nodeName || node.nodeId} · 第 ${node.visitNo} 次 · ${node.status}`,
  })),
);

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

const orgDimensionOptions = computed(() =>
  orgDimensions.value.map((item) => ({
    label: item.dimensionName,
    value: item.dimensionCode,
  })),
);

const orgRows = computed(() => flattenOrgRows(orgTreeRows.value));

const orgOptions = computed(() => flattenOrgOptions(orgTreeRows.value));

const selectedOrgUnit = computed(() =>
  orgRows.value.find((item) => item.id === formModel.orgUnitId),
);

function buildOrgTree(list: SystemOrgApi.OrgTreeNode[]): SystemOrgApi.OrgTreeNode[] {
  const nodeMap = new Map<string, SystemOrgApi.OrgTreeNode>();
  list.forEach((item) => {
    nodeMap.set(item.id, { ...item, children: [] });
  });
  const roots: SystemOrgApi.OrgTreeNode[] = [];
  list.forEach((item) => {
    const node = nodeMap.get(item.id);
    if (!node) {
      return;
    }
    if (item.parentUnitId && nodeMap.has(item.parentUnitId)) {
      nodeMap.get(item.parentUnitId)?.children?.push(node);
    } else {
      roots.push(node);
    }
  });
  const sortNodes = (nodes: SystemOrgApi.OrgTreeNode[]) => {
    nodes.sort((a, b) => (a.sortOrder ?? 100) - (b.sortOrder ?? 100));
    nodes.forEach((node) => {
      if (node.children?.length) {
        sortNodes(node.children);
      } else {
        delete node.children;
      }
    });
  };
  sortNodes(roots);
  return roots;
}

function flattenOrgRows(
  list: SystemOrgApi.OrgTreeNode[],
): SystemOrgApi.OrgTreeNode[] {
  return list.flatMap((item) => [
    item,
    ...flattenOrgRows(item.children ?? []),
  ]);
}

function flattenOrgOptions(
  list: SystemOrgApi.OrgTreeNode[],
  depth = 0,
): Array<{ label: string; value: string }> {
  return list.flatMap((item) => {
    const option = {
      label: `${'  '.repeat(depth)}${item.unitName} (${item.unitCode})`,
      value: item.id,
    };
    return [
      option,
      ...flattenOrgOptions(item.children ?? [], depth + 1),
    ];
  });
}

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

useAgentRefreshScopes({
  actions: () => loadInstances(1),
  document: () => loadInstances(1),
  list: () => loadInstances(1),
  timeline: () => loadInstances(1),
  workflow: () => loadInstances(1),
});

async function loadPackageOptions() {
  try {
    const result = await getProcessPackageList({ size: 100 });
    const latestByKey = new Map<string, ProcessApi.ProcessPackage>();
    result.items
      .filter((item) => item.status === 'PUBLISHED')
      .forEach((item) => {
        const current = latestByKey.get(item.processKey);
        if (!current || item.version > current.version) latestByKey.set(item.processKey, item);
      });
    packages.value = [...latestByKey.values()];
    packageOptions.value = packages.value.map((item) => ({
      label: `${item.name} (${item.processKey}) · v${item.version}`,
      value: item.id,
    }));
  } catch {
    packages.value = [];
    packageOptions.value = [];
  }
}

async function loadOrgDimensions() {
  if (!canQueryOrg.value) {
    orgDimensions.value = [];
    orgTreeRows.value = [];
    return;
  }
  if (orgDimensions.value.length === 0) {
    orgDimensions.value = await getOrgDimensions();
  }
  const nextDimension =
    orgDimensions.value.find((item) => item.isDefault === 1)?.dimensionCode ??
    orgDimensions.value[0]?.dimensionCode ??
    'ADMIN';
  if (
    !formModel.orgDimensionCode ||
    !orgDimensions.value.some(
      (item) => item.dimensionCode === formModel.orgDimensionCode,
    )
  ) {
    formModel.orgDimensionCode = nextDimension;
  }
}

async function loadOrgTree() {
  if (!canQueryOrg.value || !formModel.orgDimensionCode) {
    orgTreeRows.value = [];
    return;
  }
  orgTreeRows.value = buildOrgTree(await getOrgTree(formModel.orgDimensionCode));
}

async function ensureOrgContext() {
  await loadOrgDimensions();
  await loadOrgTree();
}

async function changeOrgDimension(value: string) {
  formModel.orgDimensionCode = value;
  formModel.orgUnitId = undefined;
  await loadOrgTree();
}

async function openStart() {
  formModel.processPackageId = '';
  formModel.title = '';
  formModel.formData = {};
  formModel.orgDimensionCode = 'ADMIN';
  formModel.orgUnitId = undefined;
  formOpen.value = true;
  await Promise.all([loadPackageOptions(), ensureOrgContext()]);
}

function changePackage(packageId?: string) {
  formModel.processPackageId = packageId || '';
  const pkg = packages.value.find((item) => item.id === packageId);
  formModel.formData = Object.fromEntries(
    getProcessFormFields(pkg?.formSchema, pkg?.formUiSchema)
      .filter((field) => field.schema.default !== undefined)
      .map((field) => [field.key, field.schema.default]),
  );
}

async function openDetail(record: ProcessApi.ProcessInstance) {
  detailRecord.value = record;
  detailHistory.value = undefined;
  detailClosure.value = undefined;
  detailOpen.value = true;
  await Promise.all([loadHistory(record.id), loadClosure(record.id)]);
}

async function loadHistory(instanceId: string) {
  if (!canHistory.value) return;
  historyLoading.value = true;
  try {
    detailHistory.value = await getProcessHistory(instanceId);
  } finally {
    historyLoading.value = false;
  }
}

async function loadClosure(instanceId: string) {
  closureLoading.value = true;
  try {
    detailClosure.value = await getProcessClosureDetail(instanceId);
    await loadClosureEffectAvailability();
  } catch {
    detailClosure.value = undefined;
    await loadActionAvailability([]);
  } finally {
    closureLoading.value = false;
  }
}

async function loadClosureEffectAvailability() {
  if (!detailRecord.value || !detailClosure.value?.effects.length) {
    await loadActionAvailability([]);
    return;
  }
  await loadActionAvailability(buildClosureEffectCandidates());
}

function buildClosureEffectCandidates() {
  return detailClosure.value!.effects.flatMap((effect) => {
    const candidates: ActionApi.ActionCandidate[] = [];
    if (effect.retryAvailable) {
      candidates.push(
        buildClosureEffectCandidate(
          ACTION_TYPES.processClosureEffectRetry,
          effect,
          { effectId: effect.id },
        ),
      );
    }
    if (effect.manualHandlingAvailable) {
      candidates.push(
        buildClosureEffectCandidate(
          ACTION_TYPES.processClosureEffectMarkHandled,
          effect,
          { effectId: effect.id, reason: '人工确认已处理' },
        ),
      );
    }
    return candidates;
  });
}

function buildClosureEffectCandidate(
  actionType: string,
  effect: ProcessApi.ProcessClosureDetail['effects'][number],
  payload: Record<string, unknown>,
): ActionApi.ActionCandidate {
  return {
    actionType,
    candidateId: closureEffectCandidateId(actionType, effect.id),
    executionMode: 'SYNC',
    payload,
    target: {
      id: effect.id,
      ownerService: 'service-workflow-engine',
      tenantId: detailRecord.value?.tenantId,
      type: ACTION_TARGET_TYPES.processClosureEffect,
    },
  };
}

function closureEffectCandidateId(actionType: string, effectId: string) {
  return `${actionType}:${effectId}`;
}

function retryEffectAvailability(effectId: string) {
  return getAvailability(
    closureEffectCandidateId(ACTION_TYPES.processClosureEffectRetry, effectId),
  );
}

function markEffectHandledAvailability(effectId: string) {
  return getAvailability(
    closureEffectCandidateId(
      ACTION_TYPES.processClosureEffectMarkHandled,
      effectId,
    ),
  );
}

async function retryEffect(effectId: string) {
  if (!detailRecord.value) return;
  await dispatchAction(
    {
      actionType: ACTION_TYPES.processClosureEffectRetry,
      executionMode: 'SYNC',
      idempotencyKey: createActionIdempotencyKey(
        ACTION_TYPES.processClosureEffectRetry,
        effectId,
      ),
      payload: { effectId },
      target: {
        id: effectId,
        ownerService: 'service-workflow-engine',
        tenantId: detailRecord.value.tenantId,
        type: ACTION_TARGET_TYPES.processClosureEffect,
      },
    },
    {
      failureMessage: '闭环重试提交失败',
      onSuccess: async (result) => {
        await refreshByScopes(result, {
          actions: () => {
            if (detailRecord.value) return loadClosure(detailRecord.value.id);
          },
          document: () => {
            if (detailRecord.value) return loadClosure(detailRecord.value.id);
          },
          timeline: () => {
            if (detailRecord.value) return loadHistory(detailRecord.value.id);
          },
          workflow: () => {
            if (detailRecord.value) return loadClosure(detailRecord.value.id);
          },
        });
      },
      successMessage: '已提交重试',
    },
  );
}

async function markEffectHandled(effectId: string) {
  if (!detailRecord.value) return;
  await dispatchAction(
    {
      actionType: ACTION_TYPES.processClosureEffectMarkHandled,
      executionMode: 'SYNC',
      idempotencyKey: createActionIdempotencyKey(
        ACTION_TYPES.processClosureEffectMarkHandled,
        effectId,
      ),
      payload: { effectId, reason: '人工确认已处理' },
      target: {
        id: effectId,
        ownerService: 'service-workflow-engine',
        tenantId: detailRecord.value.tenantId,
        type: ACTION_TARGET_TYPES.processClosureEffect,
      },
    },
    {
      failureMessage: '人工处理标记失败',
      onSuccess: async (result) => {
        await refreshByScopes(result, {
          actions: () => {
            if (detailRecord.value) return loadClosure(detailRecord.value.id);
          },
          document: () => {
            if (detailRecord.value) return loadClosure(detailRecord.value.id);
          },
          timeline: () => {
            if (detailRecord.value) return loadHistory(detailRecord.value.id);
          },
          workflow: () => {
            if (detailRecord.value) return loadClosure(detailRecord.value.id);
          },
        });
      },
      successMessage: '已标记为人工处理',
    },
  );
}

async function submitStart() {
  const pkg = selectedPackage.value;
  if (!pkg) {
    message.warning('请选择流程');
    return;
  }
  const clientErrors = dynamicFormRef.value?.validate() ?? [];
  if (clientErrors.length > 0) {
    message.warning('请修正表单字段后再发起');
    return;
  }
  saving.value = true;
  try {
    const formDataObj: Record<string, unknown> = { ...formModel.formData };
    if (formModel.orgUnitId) {
      if (!selectedOrgUnit.value) {
        message.warning('请选择有效组织');
        return;
      }
      const schema = parseFormSchema(pkg.formSchema);
      if (schema?.properties?.dept && formDataObj.dept == null) {
        formDataObj.dept = selectedOrgUnit.value.unitCode;
      }
    }
    const idempotencyKey = createActionIdempotencyKey(
      ACTION_TYPES.processInstanceStart,
      pkg.id,
      pkg.version,
    );
    const result = await dispatchAction<{ processInstance: ProcessApi.ProcessInstance }>(
      {
        actionType: ACTION_TYPES.processInstanceStart,
        executionMode: 'WORKFLOW',
        idempotencyKey,
        payload: {
          processKey: pkg.processKey,
          processPackageId: pkg.id,
          title: formModel.title || undefined,
          formData: formDataObj,
          idempotencyKey,
          version: pkg.version,
        },
        target: {
          id: pkg.id,
          ownerService: 'service-workflow-engine',
          tenantId: pkg.tenantId,
          type: ACTION_TARGET_TYPES.processInstance,
          version: String(pkg.version),
        },
      },
      { failureMessage: '流程发起失败' },
    );
    requireActionData<ProcessApi.ProcessInstance>(result, 'processInstance');
    message.success('流程已发起');
    formOpen.value = false;
    await refreshByScopes(result, {
      actions: () => loadInstances(),
      document: () => loadInstances(),
      list: () => loadInstances(),
      timeline: () => loadInstances(),
      workflow: () => loadInstances(),
    });
  } catch (error: any) {
    const actionResult = isActionDispatchError(error) ? error.result : undefined;
    const actionErrorCode = getActionErrorCode(actionResult);
    const responseData = error?.response?.data ?? error;
    if (
      actionErrorCode === 'FORM_DATA_VALIDATION_FAILED' ||
      responseData?.message === 'FORM_DATA_VALIDATION_FAILED'
    ) {
      dynamicFormRef.value?.applyServerErrors(
        (actionResult?.data as Record<string, unknown> | undefined)?.fieldErrors ??
          responseData?.data?.fieldErrors ??
          [],
      );
      return;
    }
    if (
      actionErrorCode === 'PROCESS_VERSION_CONFLICT' ||
      responseData?.message === 'PROCESS_VERSION_CONFLICT'
    ) {
      const processKey = pkg.processKey;
      await loadPackageOptions();
      const latest = packages.value.find((item) => item.processKey === processKey);
      changePackage(latest?.id);
      message.warning('流程版本已更新，请确认新表单后重新提交');
      return;
    }
    throw error;
  } finally {
    saving.value = false;
  }
}

function onPageChange(page: number, pageSize: number) {
  pagination.pageSize = pageSize;
  loadInstances(page);
}

function outcomeStatusColor(status?: string) {
  if (status === 'APPROVED') return 'success';
  if (status === 'REJECTED' || status === 'TERMINATED') return 'error';
  if (status === 'SUSPENDED' || status === 'CLOSURE_FAILED') return 'warning';
  return 'default';
}

function closureStatusColor(status?: string) {
  if (status === 'SUCCEEDED' || status === 'SKIPPED') return 'success';
  if (status === 'FAILED' || status === 'PARTIAL_FAILED') return 'error';
  if (status === 'RUNNING' || status === 'PENDING') return 'processing';
  return 'default';
}

function effectStatusColor(status?: string) {
  if (status === 'SUCCEEDED' || status === 'SKIPPED' || status === 'MANUALLY_HANDLED') {
    return 'success';
  }
  if (status === 'FAILED') return 'error';
  if (status === 'RETRYING' || status === 'RUNNING' || status === 'PENDING') {
    return 'processing';
  }
  return 'default';
}

function effectResultSummary(effect: ProcessApi.ProcessClosureDetail['effects'][number]) {
  if (!effect.resultJson) return '';
  try {
    const parsed = JSON.parse(effect.resultJson);
    return parsed.message || parsed.data?.summary || '';
  } catch {
    return '';
  }
}

onMounted(() => loadInstances(1));
</script>

<template>
  <Page auto-content-height>
    <BusinessPageScaffold pattern="single-table">
      <section class="list-panel">
        <div class="list-header">
          <h2>流程实例</h2>
          <Space :size="8">
            <BusinessActionButton
              v-if="canStart"
              label="发起流程"
              primary
              @execute="openStart"
            >
              <Plus class="size-4" />
              发起流程
            </BusinessActionButton>
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
    </BusinessPageScaffold>

    <Drawer
      v-model:open="formOpen"
      title="发起流程"
      destroy-on-close
      placement="right"
      width="560"
    >
      <Form layout="vertical">
        <FormItem label="选择流程" required>
          <Select
            :options="packageOptions"
            :value="formModel.processPackageId"
            placeholder="选择已发布的流程"
            @change="(value) => changePackage(String(value || ''))"
          />
        </FormItem>
        <FormItem v-if="canQueryOrg" label="组织维度">
          <Select
            :options="orgDimensionOptions"
            :value="formModel.orgDimensionCode"
            placeholder="请选择组织维度"
            @change="(value) => changeOrgDimension(String(value || formModel.orgDimensionCode))"
          />
        </FormItem>
        <FormItem v-if="canQueryOrg" label="选择组织">
          <Select
            v-model:value="formModel.orgUnitId"
            allow-clear
            option-filter-prop="label"
            :options="orgOptions"
            placeholder="请选择组织"
            show-search
          />
        </FormItem>
        <FormItem label="标题">
          <Input v-model:value="formModel.title" placeholder="不填则自动生成" />
        </FormItem>
        <DynamicProcessForm
          v-if="selectedPackage"
          ref="dynamicFormRef"
          v-model="formModel.formData"
          :schema-json="selectedPackage.formSchema"
          :ui-schema-json="selectedPackage.formUiSchema"
        />
        <Empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" />
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
        <p>
          <strong>状态：</strong>
          <Tag :color="detailRecord.status === 'COMPLETED' ? 'success' : detailRecord.status === 'RUNNING' ? 'processing' : 'error'">
            {{ detailRecord.status }}
          </Tag>
        </p>
        <p><strong>发起人：</strong>{{ detailRecord.initiatorName }}</p>
        <p><strong>当前节点：</strong>{{ detailRecord.currentNodeId || '-' }}</p>
        <p><strong>发起时间：</strong>{{ detailRecord.startedAt }}</p>
        <p><strong>完成时间：</strong>{{ detailRecord.completedAt || '-' }}</p>
        <p><strong>业务对象：</strong>{{ detailRecord.businessType || '-' }} / {{ detailRecord.businessId || '-' }}</p>

        <Divider>业务闭环</Divider>
        <Spin :spinning="closureLoading">
          <template v-if="detailClosure?.outcome">
            <div class="closure-summary">
              <span>审批结果</span>
              <Tag :color="outcomeStatusColor(detailClosure.outcome.outcomeStatus)">
                {{ detailClosure.outcome.outcomeStatus }}
              </Tag>
              <span>闭环状态</span>
              <Tag :color="closureStatusColor(detailClosure.closure?.closureStatus)">
                {{ detailClosure.closure?.closureStatus || 'SKIPPED' }}
              </Tag>
              <span>TraceId</span>
              <code>{{ detailClosure.closure?.traceId || detailClosure.outcome.traceId || '-' }}</code>
            </div>
            <div v-if="detailClosure.effects.length" class="effect-list">
              <div
                v-for="effect in detailClosure.effects"
                :key="effect.id"
                class="effect-row"
              >
                <div class="effect-main">
                  <Tag :color="effectStatusColor(effect.status)">{{ effect.status }}</Tag>
                  <strong>{{ effect.businessActionName || effect.businessActionCode || effect.effectKey }}</strong>
                  <span>{{ effect.effectType }} · {{ effect.mode }} · {{ effect.businessActionCode || effect.effectKey }}</span>
                </div>
                <div class="effect-meta">
                  <span>尝试 {{ effect.attemptCount ?? 0 }}</span>
                  <span v-if="effect.traceId">TraceId {{ effect.traceId }}</span>
                  <span v-if="effect.lastError">{{ effect.failureCategory || 'FAILED' }} · {{ effect.lastError }}</span>
                  <span v-if="effectResultSummary(effect)">{{ effectResultSummary(effect) }}</span>
                </div>
                <div class="effect-actions">
                  <BusinessActionButton
                    v-if="effect.retryAvailable"
                    :availability="retryEffectAvailability(effect.id)"
                    :loading="actionAvailabilityLoading"
                    label="重试"
                    @execute="retryEffect(effect.id)"
                  >
                    重试
                  </BusinessActionButton>
                  <BusinessActionButton
                    v-if="effect.manualHandlingAvailable"
                    :availability="markEffectHandledAvailability(effect.id)"
                    :loading="actionAvailabilityLoading"
                    label="标记已处理"
                    @execute="markEffectHandled(effect.id)"
                  >
                    标记已处理
                  </BusinessActionButton>
                </div>
              </div>
            </div>
            <Empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" />
          </template>
          <Empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" />
        </Spin>

        <div v-if="detailRecord.formData" class="mt-2">
          <strong>表单数据：</strong>
          <pre class="json-preview mt-1">{{ detailRecord.formData }}</pre>
        </div>
        <Divider>审批历史</Divider>
        <Spin :spinning="historyLoading">
          <Timeline v-if="historyTimelineItems.length" mode="left">
            <TimelineItem
              v-for="item in historyTimelineItems"
              :key="item.key"
              :color="item.color"
              :label="item.label"
            >
              {{ item.children }}
            </TimelineItem>
          </Timeline>
          <Empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" />
          <template v-if="detailHistory?.operations.length">
            <Divider>任务操作</Divider>
            <div
              v-for="operation in detailHistory.operations"
              :key="operation.operationId"
              class="operation-row"
            >
              <Tag>{{ operation.action }}</Tag>
              <span>{{ operation.operatorName || operation.operatorId }}</span>
              <span class="text-muted-foreground">{{ operation.createdAt }}</span>
              <span v-if="operation.comment">{{ operation.comment }}</span>
            </div>
          </template>
        </Spin>
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

.operation-row {
  display: grid;
  grid-template-columns: 88px 100px 1fr;
  gap: 8px;
  align-items: center;
  padding: 6px 0;
  border-bottom: 1px solid #f0f0f0;
  font-size: 12px;
}

.closure-summary {
  display: grid;
  grid-template-columns: 72px 1fr;
  gap: 8px 10px;
  align-items: center;
  margin-bottom: 10px;
  font-size: 12px;
}

.closure-summary span {
  color: #64748b;
}

.effect-list {
  display: grid;
  gap: 8px;
}

.effect-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 6px 8px;
  padding: 8px;
  border: 1px solid #e5e7eb;
}

.effect-main,
.effect-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.effect-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  grid-column: 2;
  grid-row: 1;
}

.effect-main span,
.effect-meta {
  color: #64748b;
  font-size: 12px;
}

.effect-meta {
  grid-column: 1 / -1;
}
</style>

<script setup lang="ts">
import type { TableProps } from 'ant-design-vue';

import type { SystemOrgApi } from '#/api';
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
  Tooltip,
  Timeline,
  TimelineItem,
} from 'ant-design-vue';

import { getOrgDimensions, getOrgTree } from '#/api';
import {
  getProcessHistory,
  getProcessInstanceList,
  getProcessPackageList,
  startProcessInstance,
} from '#/api/process';

import DynamicProcessForm from '../components/DynamicProcessForm.vue';
import { getProcessFormFields, parseFormSchema } from '../components/process-form';

const PERMISSIONS = {
  query: '/api/v1/process-instances:GET',
  start: '/api/v1/process-instances/start:POST',
  history: '/api/v1/process-instances/*/history:GET',
} as const;

const ORG_PERMISSIONS = {
  query: '/api/v1/org/units:GET',
} as const;

const { hasAccessByCodes } = useAccess();
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
  detailOpen.value = true;
  if (!canHistory.value) return;
  historyLoading.value = true;
  try {
    detailHistory.value = await getProcessHistory(record.id);
  } finally {
    historyLoading.value = false;
  }
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
    await startProcessInstance({
      processKey: pkg.processKey,
      processPackageId: pkg.id,
      title: formModel.title || undefined,
      formData: formDataObj,
      version: pkg.version,
    });
    message.success('流程已发起');
    formOpen.value = false;
    await loadInstances();
  } catch (error: any) {
    const responseData = error?.response?.data ?? error;
    if (responseData?.message === 'FORM_DATA_VALIDATION_FAILED') {
      dynamicFormRef.value?.applyServerErrors(
        responseData?.data?.fieldErrors ?? [],
      );
      return;
    }
    if (responseData?.message === 'PROCESS_VERSION_CONFLICT') {
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
</style>

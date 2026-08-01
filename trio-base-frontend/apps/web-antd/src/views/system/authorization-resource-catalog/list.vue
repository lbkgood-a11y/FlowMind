<script lang="ts" setup>
import type { TableProps } from 'ant-design-vue';
import type { SystemAuthorizationApi } from '#/api/system/authorization';
import type { TableColumnSetting } from '#/shared';

import { computed, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  Alert,
  Button,
  Card,
  Descriptions,
  DescriptionsItem,
  Drawer,
  Empty,
  FormItem,
  Input,
  Pagination,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
} from 'ant-design-vue';

import {
  getAuthorizationResources,
  getAuthorizationResourceTree,
  getPageCapabilities,
  getPageCapabilityCatalogs,
  getPageCapabilityDiagnostics,
  getStaleAuthorizationResources,
} from '#/api/system/authorization';
import {
  BusinessPageScaffold,
  CompactQueryBar,
  CompactTableFrame,
  CompactToolbar,
  restoreTableColumnSettings,
  TableColumnSettings,
} from '#/shared/page';

type QueryModel = {
  keyword?: string;
  lifecycleStatus?: string;
  ownerService?: string;
  resourceType?: string;
};

const loading = ref(false);
const route = useRoute();
const router = useRouter();
const detailLoading = ref(false);
const detailOpen = ref(false);
const errorMessage = ref('');
const resources = ref<SystemAuthorizationApi.ResourceSummary[]>([]);
const resourceTree = ref<SystemAuthorizationApi.ResourceTree>();
const selectedResource = ref<SystemAuthorizationApi.ResourceNode>();
const staleResourceCodes = ref(new Set<string>());
const pageCapabilities = ref<SystemAuthorizationApi.PageCapability[]>([]);
const pageDiagnostics = ref<SystemAuthorizationApi.PageCapabilityDiagnostic[]>([]);
const queryHidden = ref(false);
const blockFullscreen = ref(false);
const tableKey = ref(0);

const query = reactive<QueryModel>({
  keyword: '',
  lifecycleStatus: undefined,
  ownerService: undefined,
  resourceType: undefined,
});
const pagination = reactive({ current: 1, pageSize: 20, total: 0 });

const allResourceNodes = computed(
  () => resourceTree.value?.groups.flatMap((group) => group.resources) ?? [],
);
const ownerOptions = computed(() =>
  [...new Set(allResourceNodes.value.map((item) => item.ownerService).filter(Boolean))]
    .sort()
    .map((value) => ({ label: value, value })),
);
const resourceTypeOptions = computed(() =>
  (resourceTree.value?.groups ?? []).map((group) => ({
    label: `${group.label} (${group.resourceType})`,
    value: group.resourceType,
  })),
);
const staleCount = computed(() => staleResourceCodes.value.size);
const referencedCapabilities = computed(() => {
  const resourceCode = selectedResource.value?.resourceCode;
  if (!resourceCode) return [];
  const capabilityById = new Map(
    pageCapabilities.value.map((item) => [item.id, item]),
  );
  return pageDiagnostics.value
    .filter((diagnostic) =>
      diagnostic.targets.some((target) => target.resourceCode === resourceCode),
    )
    .map((diagnostic) => capabilityById.get(diagnostic.capabilityId))
    .filter((item): item is SystemAuthorizationApi.PageCapability => Boolean(item));
});

const baseColumns: NonNullable<TableProps['columns']> = [
  { dataIndex: 'displayName', fixed: 'left', key: 'displayName', title: '资源名称', width: 190 },
  { dataIndex: 'resourceCode', key: 'resourceCode', title: '资源编码', width: 280 },
  { dataIndex: 'resourceType', key: 'resourceType', title: '资源类型', width: 150 },
  { dataIndex: 'ownerService', key: 'ownerService', title: 'Owner 服务', width: 180 },
  { dataIndex: 'lifecycleStatus', key: 'lifecycleStatus', title: '生命周期', width: 120 },
  { key: 'enforcement', title: '字段治理就绪度', width: 250 },
  { key: 'freshness', title: '同步状态', width: 120 },
  { dataIndex: 'lastSyncedAt', key: 'lastSyncedAt', title: '最后同步时间', width: 190 },
  { fixed: 'right', key: 'action', title: '操作', width: 90 },
];
const defaultColumnSettings: TableColumnSetting[] = baseColumns.map((column) => ({
  fixed: column.fixed === true ? 'left' : column.fixed || undefined,
  key: String(column.key),
  required: column.key === 'action',
  title: String(column.title),
  visible: true,
  width: Number(column.width || 120),
}));
const columnSettings = reactive(
  restoreTableColumnSettings(
    'triobase:table-columns:system-authorization-resource-catalog',
    defaultColumnSettings,
  ),
);
const columns = computed<TableProps['columns']>(() =>
  columnSettings.filter((item) => item.visible).map((item) => {
    const base = baseColumns.find((column) => String(column.key) === item.key);
    return {
      ...base,
      fixed: item.fixed,
      width: Math.max(Number(item.width || 0), Number(base?.width || 120)),
    };
  }),
);

function applyColumnSettings(settings: TableColumnSetting[]) {
  columnSettings.splice(0, columnSettings.length, ...settings);
  tableKey.value += 1;
}

function normalizeQueryValue(value?: string) {
  const normalized = value?.trim();
  return normalized || undefined;
}

async function loadResources(page = pagination.current) {
  loading.value = true;
  errorMessage.value = '';
  try {
    const result = await getAuthorizationResources({
      keyword: normalizeQueryValue(query.keyword),
      lifecycleStatus: query.lifecycleStatus,
      ownerService: query.ownerService,
      page,
      resourceType: query.resourceType,
      size: pagination.pageSize,
    });
    resources.value = result.records;
    pagination.current = Number(result.page || page);
    pagination.total = Number(result.total || 0);
  } catch (error) {
    resources.value = [];
    pagination.total = 0;
    errorMessage.value = error instanceof Error ? error.message : '授权资源目录加载失败';
  } finally {
    loading.value = false;
  }
}

async function loadRegistryProjection() {
  const [treeResult, staleResult] = await Promise.all([
    getAuthorizationResourceTree(),
    getStaleAuthorizationResources({ staleMinutes: 1440 }),
  ]);
  resourceTree.value = treeResult;
  staleResourceCodes.value = new Set(staleResult.map((item) => item.resourceCode));
}

async function loadCapabilityReferences() {
  const catalogs = await getPageCapabilityCatalogs();
  const activeCatalog = catalogs.find((item) => item.lifecycleStatus === 'ACTIVE');
  if (!activeCatalog) {
    pageCapabilities.value = [];
    pageDiagnostics.value = [];
    return;
  }
  [pageCapabilities.value, pageDiagnostics.value] = await Promise.all([
    getPageCapabilities(undefined, undefined, activeCatalog.id),
    getPageCapabilityDiagnostics(undefined, activeCatalog.id),
  ]);
}

async function refreshCatalog() {
  loading.value = true;
  errorMessage.value = '';
  try {
    await Promise.all([
      loadResources(pagination.current),
      loadRegistryProjection(),
      loadCapabilityReferences(),
    ]);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '授权资源目录刷新失败';
  } finally {
    loading.value = false;
  }
}

function resetQuery() {
  query.keyword = '';
  query.lifecycleStatus = undefined;
  query.ownerService = undefined;
  query.resourceType = undefined;
  void loadResources(1);
}

async function openDetail(summary: SystemAuthorizationApi.ResourceSummary) {
  detailOpen.value = true;
  detailLoading.value = true;
  try {
    if (!resourceTree.value) await loadRegistryProjection();
    selectedResource.value = allResourceNodes.value.find(
      (item) => item.resourceCode === summary.resourceCode,
    ) ?? {
      ...summary,
      actions: [],
      fields: [],
      guards: [],
    };
  } finally {
    detailLoading.value = false;
  }
}

function onPageChange(page: number, pageSize: number) {
  pagination.pageSize = pageSize;
  void loadResources(page);
}

function formatMetadata(value?: string) {
  if (!value) return '-';
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

function lifecycleColor(status?: string) {
  return status === 'ACTIVE' ? 'success' : status === 'OFFLINE' ? 'default' : 'warning';
}

function readinessColor(readiness?: string) {
  return readiness === 'READY'
    ? 'success'
    : readiness === 'NOT_APPLICABLE'
      ? 'default'
      : readiness === 'PARTIAL'
        ? 'warning'
        : 'error';
}

function asResource(record: Record<string, any>) {
  return record as SystemAuthorizationApi.ResourceSummary;
}

function resourceRow(record: Record<string, any>) {
  return { onDblclick: () => openDetail(asResource(record)) };
}

function openCapabilityReference(capabilityCode: string) {
  detailOpen.value = false;
  void router.push({
    name: 'SystemPageCapabilityCatalog',
    query: { capabilityCode },
  });
}

watch(
  () => route.query.resourceCode,
  async (resourceCode) => {
    const targetResourceCode = String(resourceCode || '');
    query.keyword = targetResourceCode;
    pagination.current = 1;
    await refreshCatalog();
    const exact = resources.value.find(
      (item) => item.resourceCode === targetResourceCode,
    );
    if (exact) await openDetail(exact);
  },
  { immediate: true },
);
</script>

<template>
  <Page auto-content-height>
    <BusinessPageScaffold
      class="resource-catalog-page"
      pattern="single-table"
      :fullscreen="blockFullscreen"
      :class="{ 'is-block-fullscreen': blockFullscreen, 'is-query-hidden': queryHidden }"
    >
      <template #query>
        <CompactQueryBar v-show="!queryHidden" :columns="4">
          <FormItem label="关键字">
            <Input
              v-model:value="query.keyword"
              allow-clear
              placeholder="资源名称或编码"
              @press-enter="loadResources(1)"
            />
          </FormItem>
          <FormItem label="Owner 服务">
            <Select
              v-model:value="query.ownerService"
              allow-clear
              :options="ownerOptions"
              placeholder="全部 Owner"
              show-search
            />
          </FormItem>
          <FormItem label="资源类型">
            <Select
              v-model:value="query.resourceType"
              allow-clear
              :options="resourceTypeOptions"
              placeholder="全部类型"
            />
          </FormItem>
          <FormItem label="生命周期">
            <Select
              v-model:value="query.lifecycleStatus"
              allow-clear
              :options="[
                { label: '已激活', value: 'ACTIVE' },
                { label: '已下线', value: 'OFFLINE' },
                { label: '已废弃', value: 'DEPRECATED' },
              ]"
              placeholder="全部状态"
            />
          </FormItem>
          <template #actions>
            <Button @click="resetQuery">重置</Button>
            <Button type="primary" @click="loadResources(1)">查询</Button>
          </template>
        </CompactQueryBar>
      </template>

      <template #toolbar>
        <CompactToolbar>
          <template #title>
            <div class="list-title">
              <h2>资源注册中心</h2>
              <Button v-if="queryHidden" type="link" @click="queryHidden = false">展开搜索</Button>
            </div>
          </template>
          <Space :size="8">
            <Tag :color="staleCount ? 'warning' : 'success'">
              {{ staleCount ? `${staleCount} 个资源同步过期` : '同步状态正常' }}
            </Tag>
            <Tooltip title="查询并隐藏搜索栏">
              <Button shape="circle" type="primary" @click="loadResources(1); queryHidden = true">
                <i aria-hidden="true" class="vxe-button--item vxe-table-icon-search"></i>
              </Button>
            </Tooltip>
            <Tooltip title="刷新">
              <Button :loading="loading" shape="circle" @click="refreshCatalog">
                <i aria-hidden="true" class="vxe-button--item vxe-table-icon-refresh"></i>
              </Button>
            </Tooltip>
            <Tooltip :title="blockFullscreen ? '还原' : '全屏'">
              <Button shape="circle" @click="blockFullscreen = !blockFullscreen">
                <i aria-hidden="true" class="vxe-button--item vxe-button--prefix-icon" :class="blockFullscreen ? 'vxe-table-icon-minimize' : 'vxe-table-icon-fullscreen'"></i>
              </Button>
            </Tooltip>
            <TableColumnSettings
              :defaults="defaultColumnSettings"
              :model-value="columnSettings"
              storage-key="triobase:table-columns:system-authorization-resource-catalog"
              @apply="applyColumnSettings"
            />
          </Space>
        </CompactToolbar>
      </template>

      <Alert
        v-if="errorMessage"
        class="mb-2"
        :message="errorMessage"
        show-icon
        type="error"
      />

      <CompactTableFrame>
        <Table
          :key="tableKey"
          :columns="columns"
          :data-source="resources"
          :loading="loading"
          :pagination="false"
          :scroll="{ x: 1560 }"
          bordered
          row-key="id"
          table-layout="fixed"
          size="small"
          @row="resourceRow"
        >
          <template #emptyText><Empty description="暂无授权资源" /></template>
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'displayName'">
              <Button type="link" @click="openDetail(asResource(record))">
                {{ record.displayName || record.resourceCode }}
              </Button>
            </template>
            <template v-else-if="column.key === 'lifecycleStatus'">
              <Tag :color="lifecycleColor(record.lifecycleStatus)">
                {{ record.lifecycleStatus || '-' }}
              </Tag>
            </template>
            <template v-else-if="column.key === 'enforcement'">
              <Tooltip :title="record.fieldEnforcementReason || '暂无诊断'">
                <Tag :color="readinessColor(record.fieldEnforcementReadiness)">
                  {{ record.fieldEnforcementReadiness || 'NON_COMPLIANT' }}
                  · {{ record.fieldCount || 0 }} 字段
                </Tag>
              </Tooltip>
            </template>
            <template v-else-if="column.key === 'freshness'">
              <Tag :color="staleResourceCodes.has(record.resourceCode) ? 'warning' : 'success'">
                {{ staleResourceCodes.has(record.resourceCode) ? '同步过期' : '正常' }}
              </Tag>
            </template>
            <template v-else-if="column.key === 'lastSyncedAt'">
              {{ record.lastSyncedAt || '-' }}
            </template>
            <template v-else-if="column.key === 'action'">
              <Button size="small" type="link" @click="openDetail(asResource(record))">详情</Button>
            </template>
          </template>
        </Table>

        <template #footer>
          <div class="table-total">共 {{ pagination.total }} 条资源</div>
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
      v-model:open="detailOpen"
      :footer="null"
      title="注册资源详情"
      placement="right"
      width="min(860px, 94vw)"
    >
      <div v-if="selectedResource" class="detail-content">
        <Alert
          class="mb-3"
          message="资源定义由 Owner 服务维护；如需变更，请修改 Owner Manifest 并重新发布或同步。"
          show-icon
          type="info"
        />
        <Descriptions bordered :column="2" size="small">
          <DescriptionsItem label="资源名称">{{ selectedResource.displayName || '-' }}</DescriptionsItem>
          <DescriptionsItem label="资源编码">{{ selectedResource.resourceCode }}</DescriptionsItem>
          <DescriptionsItem label="资源类型">{{ selectedResource.resourceType }}</DescriptionsItem>
          <DescriptionsItem label="Owner 服务">{{ selectedResource.ownerService || '-' }}</DescriptionsItem>
          <DescriptionsItem label="业务对象">{{ selectedResource.businessObjectId || '-' }}</DescriptionsItem>
          <DescriptionsItem label="生命周期">
            <Tag :color="lifecycleColor(selectedResource.lifecycleStatus)">
              {{ selectedResource.lifecycleStatus || '-' }}
            </Tag>
          </DescriptionsItem>
          <DescriptionsItem label="最后同步">{{ selectedResource.lastSyncedAt || '-' }}</DescriptionsItem>
          <DescriptionsItem label="同步状态">
            <Tag :color="staleResourceCodes.has(selectedResource.resourceCode) ? 'warning' : 'success'">
              {{ staleResourceCodes.has(selectedResource.resourceCode) ? '同步过期' : '正常' }}
            </Tag>
          </DescriptionsItem>
          <DescriptionsItem label="字段执行能力" :span="2">
            <Space wrap>
              <Tag :color="readinessColor(selectedResource.fieldEnforcementReadiness)">
                {{ selectedResource.fieldEnforcementReadiness || 'NON_COMPLIANT' }}
              </Tag>
              <Tag :color="selectedResource.readHideEnforced ? 'blue' : 'default'">读取隐藏</Tag>
              <Tag :color="selectedResource.readMaskEnforced ? 'purple' : 'default'">读取脱敏</Tag>
              <Tag :color="selectedResource.writeDenyEnforced ? 'orange' : 'default'">写入拒绝</Tag>
            </Space>
            <div class="mt-1 text-xs text-gray-500">
              {{ selectedResource.fieldEnforcementReason || '暂无字段治理诊断' }}
            </div>
          </DescriptionsItem>
        </Descriptions>

        <Card class="mt-3" size="small" title="引用此资源的页面能力">
          <Space v-if="referencedCapabilities.length" wrap>
            <Button
              v-for="capability in referencedCapabilities"
              :key="capability.id"
              size="small"
              @click="openCapabilityReference(capability.capabilityCode)"
            >
              {{ capability.pageName }} · {{ capability.capabilityName }}
            </Button>
          </Space>
          <Empty v-else description="当前没有页面能力引用此资源" />
        </Card>

        <Card class="mt-3" size="small" title="动作">
          <Table
            :columns="[
              { dataIndex: 'actionCode', title: '动作编码', width: 150 },
              { dataIndex: 'actionCategory', title: '类别', width: 120 },
              { dataIndex: 'description', title: '说明' },
              { dataIndex: 'guardCodes', title: '守卫', width: 220 },
            ]"
            :data-source="selectedResource.actions"
            :pagination="false"
            :scroll="{ x: 700 }"
            row-key="actionCode"
            size="small"
          >
            <template #emptyText><Empty description="未注册动作" /></template>
            <template #bodyCell="{ column, record }">
              <Space v-if="column.dataIndex === 'guardCodes'" wrap :size="4">
                <Tag v-for="guard in record.guardCodes" :key="guard">{{ guard }}</Tag>
                <span v-if="!record.guardCodes?.length">-</span>
              </Space>
            </template>
          </Table>
        </Card>

        <Card class="mt-3" size="small" title="字段">
          <Table
            :columns="[
              { dataIndex: 'fieldKey', title: '字段键', width: 170 },
              { dataIndex: 'fieldLabel', title: '字段名称', width: 150 },
              { dataIndex: 'fieldType', title: '类型', width: 110 },
              { dataIndex: 'sensitivityClassification', title: '敏感级别', width: 130 },
              { dataIndex: 'defaultMaskStrategy', title: '默认脱敏', width: 130 },
            ]"
            :data-source="selectedResource.fields"
            :pagination="false"
            :scroll="{ x: 700 }"
            row-key="fieldKey"
            size="small"
          >
            <template #emptyText><Empty description="未注册字段" /></template>
          </Table>
        </Card>

        <Card class="mt-3" size="small" title="守卫">
          <Table
            :columns="[
              { dataIndex: 'guardCode', title: '守卫编码', width: 190 },
              { dataIndex: 'ownerService', title: 'Owner 服务', width: 170 },
              { dataIndex: 'description', title: '说明' },
            ]"
            :data-source="selectedResource.guards"
            :pagination="false"
            :scroll="{ x: 650 }"
            row-key="guardCode"
            size="small"
          >
            <template #emptyText><Empty description="未注册适用守卫" /></template>
          </Table>
        </Card>

        <Card class="mt-3" size="small" title="元数据">
          <pre class="metadata-block">{{ formatMetadata(selectedResource.metadataJson) }}</pre>
        </Card>
      </div>
      <Empty v-else-if="!detailLoading" description="未找到资源详情" />
    </Drawer>
  </Page>
</template>

<style scoped>
.resource-catalog-page {
  min-height: 0;
}

.detail-content {
  min-width: 0;
}

.metadata-block {
  max-height: 240px;
  margin: 0;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

@media (max-width: 768px) {
  :deep(.ant-descriptions-view table) {
    table-layout: fixed;
  }
}
</style>

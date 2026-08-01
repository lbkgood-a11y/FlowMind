<script lang="ts" setup>
import type { SystemAuthorizationApi } from '#/api/system/authorization';
import type { TableColumnSetting } from '#/shared';

import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  Alert,
  Button,
  Empty,
  FormItem,
  Pagination,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
} from 'ant-design-vue';

import {
  getPageCapabilities,
  getPageCapabilityCatalogs,
  getPageCapabilityDiagnostics,
} from '#/api/system/authorization';
import {
  BusinessPageScaffold,
  CompactQueryBar,
  CompactTableFrame,
  CompactToolbar,
  restoreTableColumnSettings,
  TableColumnSettings,
} from '#/shared/page';

const loading = ref(false);
const route = useRoute();
const router = useRouter();
const errorMessage = ref('');
const catalogs = ref<SystemAuthorizationApi.PageCapabilityCatalog[]>([]);
const selectedCatalogId = ref<string>();
const capabilities = ref<SystemAuthorizationApi.PageCapability[]>([]);
const diagnostics = ref<SystemAuthorizationApi.PageCapabilityDiagnostic[]>([]);
const queryHidden = ref(false);
const blockFullscreen = ref(false);
const tableKey = ref(0);

const selectedCatalog = computed(() =>
  catalogs.value.find((item) => item.id === selectedCatalogId.value),
);
const catalogOptions = computed(() =>
  catalogs.value.map((item) => ({
    label: `${item.catalogCode} v${item.catalogVersion} · ${item.lifecycleStatus}`,
    value: item.id,
  })),
);
const readyCount = computed(
  () => capabilities.value.filter((item) => item.readiness === 'READY').length,
);
const pageCount = computed(
  () => new Set(capabilities.value.map((item) => item.pageCode)).size,
);
const diagnosticById = computed(
  () => new Map(diagnostics.value.map((item) => [item.capabilityId, item])),
);
const pagination = reactive({
  current: 1,
  pageSize: 20,
});
const pagedCapabilities = computed(() => {
  const start = (pagination.current - 1) * pagination.pageSize;
  return capabilities.value.slice(start, start + pagination.pageSize);
});

const baseColumns = [
  { dataIndex: 'pageName', key: 'pageName', title: '页面', width: 170 },
  {
    dataIndex: 'capabilityName',
    key: 'capabilityName',
    title: '能力名称',
    width: 180,
  },
  {
    dataIndex: 'capabilityCode',
    key: 'capabilityCode',
    title: '能力编码',
    width: 250,
  },
  { dataIndex: 'category', key: 'category', title: '类型', width: 110 },
  { dataIndex: 'readiness', key: 'readiness', title: '就绪状态', width: 120 },
  { key: 'dependencies', title: '依赖', width: 220 },
  { key: 'targets', title: '运行时目标', width: 320 },
  {
    dataIndex: 'readinessMessage',
    key: 'readinessMessage',
    title: '诊断说明',
    width: 240,
  },
];
const defaultColumnSettings: TableColumnSetting[] = baseColumns.map((column) => ({
  key: String(column.key),
  title: String(column.title),
  visible: true,
  width: Number(column.width || 120),
}));
const columnSettings = reactive(
  restoreTableColumnSettings(
    'triobase:table-columns:system-capability-catalog',
    defaultColumnSettings,
  ),
);
const columns = computed(() =>
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

function readinessColor(readiness: string) {
  return readiness === 'READY'
    ? 'success'
    : readiness === 'PARTIAL'
      ? 'warning'
      : 'error';
}

async function loadCatalogContent() {
  if (!selectedCatalogId.value) {
    capabilities.value = [];
    diagnostics.value = [];
    return;
  }
  loading.value = true;
  errorMessage.value = '';
  try {
    [capabilities.value, diagnostics.value] = await Promise.all([
      getPageCapabilities(undefined, undefined, selectedCatalogId.value),
      getPageCapabilityDiagnostics(undefined, selectedCatalogId.value),
    ]);
    focusCapabilityFromRoute();
  } catch (error) {
    capabilities.value = [];
    diagnostics.value = [];
    errorMessage.value =
      error instanceof Error ? error.message : '能力目录加载失败';
  } finally {
    loading.value = false;
  }
}

function focusCapabilityFromRoute() {
  const capabilityCode = String(route.query.capabilityCode || '');
  if (!capabilityCode) return;
  const index = capabilities.value.findIndex(
    (item) => item.capabilityCode === capabilityCode,
  );
  if (index >= 0) {
    pagination.current = Math.floor(index / pagination.pageSize) + 1;
  }
}

function openResourceTarget(resourceCode: string) {
  void router.push({
    name: 'SystemAuthorizationResourceCatalog',
    query: { resourceCode },
  });
}

async function loadCatalogs() {
  loading.value = true;
  errorMessage.value = '';
  try {
    catalogs.value = await getPageCapabilityCatalogs();
    const active = catalogs.value.find(
      (item) => item.lifecycleStatus === 'ACTIVE',
    );
    selectedCatalogId.value = active?.id ?? catalogs.value[0]?.id;
    if (!selectedCatalogId.value) {
      errorMessage.value =
        '当前租户尚未生成能力目录，请检查服务启动日志中的 Manifest 就绪诊断。';
    }
  } catch (error) {
    catalogs.value = [];
    errorMessage.value =
      error instanceof Error ? error.message : '能力目录加载失败';
  } finally {
    loading.value = false;
  }
}

watch(selectedCatalogId, () => void loadCatalogContent());
watch(capabilities, () => {
  pagination.current = 1;
  focusCapabilityFromRoute();
});

function handlePageChange(page: number, pageSize: number) {
  if (pageSize !== pagination.pageSize) {
    pagination.pageSize = pageSize;
    pagination.current = 1;
    return;
  }
  pagination.current = page;
}

onMounted(() => void loadCatalogs());
</script>

<template>
  <Page auto-content-height>
    <BusinessPageScaffold
      class="capability-catalog-page"
      pattern="single-table"
      :fullscreen="blockFullscreen"
      :class="{ 'is-block-fullscreen': blockFullscreen, 'is-query-hidden': queryHidden }"
    >
      <template #query>
        <CompactQueryBar v-show="!queryHidden" :columns="4">
          <FormItem label="目录版本">
            <Select
              v-model:value="selectedCatalogId"
              :options="catalogOptions"
              placeholder="选择目录版本"
            />
          </FormItem>
          <template #actions>
            <Button type="primary" @click="loadCatalogContent">查询</Button>
          </template>
        </CompactQueryBar>
      </template>
      <template #toolbar>
        <CompactToolbar>
          <template #title>
            <div class="list-title">
              <h2>页面能力目录</h2>
              <Button v-if="queryHidden" type="link" @click="queryHidden = false">展开搜索</Button>
            </div>
          </template>
          <Space :size="8">
            <Tag v-if="selectedCatalog" :color="selectedCatalog.lifecycleStatus === 'ACTIVE' ? 'success' : 'default'">
              {{ selectedCatalog.catalogCode }} v{{ selectedCatalog.catalogVersion }}
            </Tag>
            <Tag color="blue">页面 {{ pageCount }}</Tag>
            <Tag color="success">READY {{ readyCount }}/{{ capabilities.length }}</Tag>
            <Tooltip title="查询并隐藏搜索栏">
              <Button shape="circle" type="primary" @click="loadCatalogContent(); queryHidden = true">
                <i aria-hidden="true" class="vxe-button--item vxe-table-icon-search"></i>
              </Button>
            </Tooltip>
            <Tooltip title="刷新">
              <Button :loading="loading" shape="circle" @click="loadCatalogs">
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
              storage-key="triobase:table-columns:system-capability-catalog"
              @apply="applyColumnSettings"
            />
          </Space>
        </CompactToolbar>
      </template>

      <Alert
        v-if="errorMessage"
        class="mb-3"
        :message="errorMessage"
        show-icon
        type="error"
      />

        <CompactTableFrame v-if="capabilities.length">
          <Table
            :key="tableKey"
            :columns="columns"
            :data-source="pagedCapabilities"
            :pagination="false"
            :scroll="{ x: 'max-content' }"
            row-key="id"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <Tag v-if="column.key === 'category'" color="blue">
                {{ record.category }}
              </Tag>
              <Tag
                v-else-if="column.key === 'readiness'"
                :color="readinessColor(record.readiness)"
              >
                {{ record.readiness }}
              </Tag>
              <Space v-else-if="column.key === 'dependencies'" wrap>
                <Tag
                  v-for="code in diagnosticById.get(record.id)
                    ?.requiredCapabilityCodes || []"
                  :key="code"
                >
                  {{ code }}
                </Tag>
                <span
                  v-if="
                    !(
                      diagnosticById.get(record.id)?.requiredCapabilityCodes ||
                      []
                    ).length
                  "
                  >-</span>
              </Space>
              <Space
                v-else-if="column.key === 'targets'"
                direction="vertical"
                size="small"
              >
                <Tag
                  v-for="target in diagnosticById.get(record.id)?.targets || []"
                  :key="`${target.resourceCode}:${target.actionCode}`"
                  class="cursor-pointer"
                  :color="target.active ? 'processing' : 'error'"
                  @click="openResourceTarget(target.resourceCode)"
                >
                  {{ target.resourceCode }} : {{ target.actionCode }}
                </Tag>
                <span
                  v-if="!(diagnosticById.get(record.id)?.targets || []).length"
                  >-</span>
              </Space>
            </template>
          </Table>
          <template #footer>
            <div class="table-total">共 {{ capabilities.length }} 条记录</div>
            <Pagination
              v-model:current="pagination.current"
              v-model:page-size="pagination.pageSize"
              :page-size-options="['10', '20', '50', '100']"
              :total="capabilities.length"
              show-less-items
              show-quick-jumper
              show-size-changer
              size="small"
              @change="handlePageChange"
              @show-size-change="handlePageChange"
            />
          </template>
        </CompactTableFrame>
        <Empty v-else-if="!loading" description="当前目录没有页面能力" />
    </BusinessPageScaffold>
  </Page>
</template>

<style scoped>
.capability-catalog-page{display:flex;min-height:100%;flex-direction:column;gap:8px}
</style>

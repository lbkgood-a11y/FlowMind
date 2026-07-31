<script lang="ts" setup>
import type { SystemAuthorizationApi } from '#/api/system/authorization';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';

import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  DescriptionsItem,
  Empty,
  Pagination,
  Row,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
} from 'ant-design-vue';

import {
  getPageCapabilities,
  getPageCapabilityCatalogs,
  getPageCapabilityDiagnostics,
} from '#/api/system/authorization';
import {
  BusinessPageScaffold,
  CompactTableFrame,
  CompactToolbar,
} from '#/shared/page';

const loading = ref(false);
const errorMessage = ref('');
const catalogs = ref<SystemAuthorizationApi.PageCapabilityCatalog[]>([]);
const selectedCatalogId = ref<string>();
const capabilities = ref<SystemAuthorizationApi.PageCapability[]>([]);
const diagnostics = ref<SystemAuthorizationApi.PageCapabilityDiagnostic[]>([]);

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

const columns = [
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
  } catch (error) {
    capabilities.value = [];
    diagnostics.value = [];
    errorMessage.value =
      error instanceof Error ? error.message : '能力目录加载失败';
  } finally {
    loading.value = false;
  }
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
  <Page>
    <BusinessPageScaffold>
      <template #toolbar>
        <CompactToolbar
          title="能力目录"
          subtitle="查看页面能力、目录生命周期、依赖关系与运行时映射；能力定义由 Owner Manifest 维护"
        >
          <Button :loading="loading" @click="loadCatalogs">刷新目录</Button>
        </CompactToolbar>
      </template>

      <Alert
        v-if="errorMessage"
        class="mb-3"
        :message="errorMessage"
        show-icon
        type="error"
      />

      <Spin :spinning="loading">
        <Card class="mb-3" size="small">
          <Space wrap>
            <span class="text-sm font-medium">目录版本</span>
            <Select
              v-model:value="selectedCatalogId"
              :options="catalogOptions"
              placeholder="选择目录版本"
              style="width: 320px"
            />
            <Tag
              v-if="selectedCatalog"
              :color="
                selectedCatalog.lifecycleStatus === 'ACTIVE'
                  ? 'success'
                  : 'default'
              "
            >
              {{ selectedCatalog.lifecycleStatus }}
            </Tag>
          </Space>

          <Descriptions
            v-if="selectedCatalog"
            class="mt-3"
            :column="3"
            size="small"
            bordered
          >
            <DescriptionsItem label="租户">
              {{ selectedCatalog.tenantId }}
            </DescriptionsItem>
            <DescriptionsItem label="目录编码">
              {{ selectedCatalog.catalogCode }}
            </DescriptionsItem>
            <DescriptionsItem label="版本">
              v{{ selectedCatalog.catalogVersion }}
            </DescriptionsItem>
            <DescriptionsItem label="来源">
              {{ selectedCatalog.sourceType }}
            </DescriptionsItem>
            <DescriptionsItem label="来源引用">
              {{ selectedCatalog.sourceRef || '-' }}
            </DescriptionsItem>
            <DescriptionsItem label="激活时间">
              {{ selectedCatalog.activatedAt || '-' }}
            </DescriptionsItem>
          </Descriptions>
        </Card>

        <Row class="mb-3" :gutter="12">
          <Col :span="8">
            <Card size="small">
              <Statistic title="页面数" :value="pageCount" />
            </Card>
          </Col>
          <Col :span="8">
            <Card size="small">
              <Statistic title="能力总数" :value="capabilities.length" />
            </Card>
          </Col>
          <Col :span="8">
            <Card size="small">
              <Statistic title="READY 能力" :value="readyCount" />
            </Card>
          </Col>
        </Row>

        <CompactTableFrame v-if="capabilities.length">
          <Table
            :columns="columns"
            :data-source="pagedCapabilities"
            :pagination="false"
            :scroll="{ x: 'max-content', y: '100%' }"
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
                  :color="target.active ? 'processing' : 'error'"
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
      </Spin>
    </BusinessPageScaffold>
  </Page>
</template>

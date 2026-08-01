<script setup lang="ts">
import type { TableProps } from 'ant-design-vue';

import { computed, ref, watch } from 'vue';

import { Empty, Pagination, Table } from 'ant-design-vue';

import CompactTableFrame from './CompactTableFrame.vue';

const props = withDefaults(defineProps<{
  columns?: TableProps['columns'];
  dataSource?: any[];
  loading?: boolean;
  pageSize?: number;
  rowKey?: string | ((record: any) => string);
  scrollX?: number | string;
  scrollY?: number | string;
}>(), {
  columns: () => [],
  dataSource: () => [],
  loading: false,
  pageSize: 20,
  rowKey: 'id',
  scrollX: 'max-content',
  scrollY: '100%',
});

const current = ref(1);
const size = ref(props.pageSize);
const total = computed(() => props.dataSource.length);
const pageRows = computed(() => {
  const start = (current.value - 1) * size.value;
  return props.dataSource.slice(start, start + size.value);
});

watch(() => props.dataSource, () => {
  const lastPage = Math.max(1, Math.ceil(total.value / size.value));
  if (current.value > lastPage) current.value = lastPage;
}, { deep: false });

function changePage(page: number, pageSize: number) {
  current.value = page;
  size.value = pageSize;
}
</script>

<template>
  <CompactTableFrame class="client-paginated-table">
    <Table
      :columns="columns"
      :data-source="pageRows"
      :loading="loading"
      :pagination="false"
      :row-key="rowKey"
      :scroll="{ x: scrollX, y: scrollY }"
      :sticky="{ offsetHeader: 0 }"
      bordered
      size="small"
      table-layout="fixed"
    >
      <template #emptyText><Empty description="暂无数据" /></template>
      <template #bodyCell="slotProps"><slot name="bodyCell" v-bind="slotProps" /></template>
    </Table>
    <template #footer>
      <div class="table-total">共 {{ total }} 条记录</div>
      <Pagination
        v-model:current="current"
        v-model:page-size="size"
        :page-size-options="['10', '20', '50', '100']"
        :total="total"
        show-less-items
        show-size-changer
        size="small"
        @change="changePage"
        @show-size-change="changePage"
      />
    </template>
  </CompactTableFrame>
</template>

<style scoped>
.client-paginated-table{width:100%;min-width:0;min-height:260px}
</style>

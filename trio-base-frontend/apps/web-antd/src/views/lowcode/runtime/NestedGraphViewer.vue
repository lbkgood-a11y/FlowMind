<script setup lang="ts">
import type { LowcodeApi } from '#/api/lowcode';

import { computed } from 'vue';

import { Descriptions, DescriptionsItem, Empty } from 'ant-design-vue';

import { parseInstanceData } from './runtime-metadata';

defineOptions({ name: 'NestedGraphViewer' });
const props = defineProps<{ graph?: LowcodeApi.FormInstanceGraph; title?: string }>();
const data = computed(() => parseInstanceData(props.graph?.instance.dataJson));
</script>

<template>
  <section v-if="graph" class="graph-node">
    <h4>{{ title || graph.instance.formKey }}</h4>
    <Descriptions bordered size="small" :column="2">
      <DescriptionsItem v-for="(value, key) in data" :key="key" :label="String(key)">{{ value ?? '-' }}</DescriptionsItem>
    </Descriptions>
    <template v-for="(children, relationCode) in graph.children" :key="relationCode">
      <div class="relation-title">{{ relationCode }}</div>
      <Empty v-if="children.length === 0" description="暂无关联数据" />
      <NestedGraphViewer v-for="(child, index) in children" :key="child.instance.id" :graph="child" :title="`${child.instance.formKey} ${index + 1}`" />
    </template>
  </section>
</template>

<style scoped>
.graph-node { padding: 12px; margin: 12px 0; border-left: 3px solid #1677ff; }
.graph-node h4, .relation-title { margin: 8px 0; font-weight: 600; }
</style>

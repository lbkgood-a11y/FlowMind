<script setup lang="ts">
import type { LowcodeApi } from '#/api/lowcode';

import { computed } from 'vue';

import { Button, Card, Empty } from 'ant-design-vue';

import DynamicProcessForm from '../../process/components/DynamicProcessForm.vue';

defineOptions({ name: 'NestedRuntimeForms' });

const props = defineProps<{
  currentFormId: string;
  depth?: number;
  modelValue: Record<string, LowcodeApi.NestedFormInstanceRequest[]>;
  relations: LowcodeApi.FormRelation[];
}>();

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, LowcodeApi.NestedFormInstanceRequest[]>];
}>();

const childRelations = computed(() => props.relations
  .filter((relation) => relation.parentFormDefinitionId === props.currentFormId)
  .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0)));

function rows(code: string) {
  return props.modelValue[code] ?? [];
}

function add(relation: LowcodeApi.FormRelation) {
  const current = rows(relation.relationCode);
  if (relation.cardinality === 'ONE' && current.length > 0) return;
  emit('update:modelValue', {
    ...props.modelValue,
    [relation.relationCode]: [...current, {
      children: {},
      data: {},
      formDefinitionId: relation.childFormDefinitionId,
    }],
  });
}

function remove(code: string, index: number) {
  emit('update:modelValue', {
    ...props.modelValue,
    [code]: rows(code).filter((_, rowIndex) => rowIndex !== index),
  });
}

function updateRow(code: string, index: number, row: LowcodeApi.NestedFormInstanceRequest) {
  emit('update:modelValue', {
    ...props.modelValue,
    [code]: rows(code).map((current, rowIndex) => rowIndex === index ? row : current),
  });
}
</script>

<template>
  <div v-if="childRelations.length" class="nested-relations" :data-depth="depth ?? 1">
    <section v-for="relation in childRelations" :key="relation.relationCode" class="nested-relation">
      <div class="relation-header">
        <div><strong>{{ relation.childFormName || relation.relationCode }}</strong><span> · {{ relation.cardinality === 'ONE' ? '一对一' : '一对多' }}</span></div>
        <Button type="primary" size="small" :disabled="relation.cardinality === 'ONE' && rows(relation.relationCode).length > 0" @click="add(relation)">新增明细</Button>
      </div>
      <Empty v-if="rows(relation.relationCode).length === 0" :description="`暂无${relation.childFormName || '明细'}`" />
      <Card v-for="(row, index) in rows(relation.relationCode)" :key="index" class="nested-row" size="small">
        <template #title>{{ relation.childFormName || '明细' }} {{ index + 1 }}</template>
        <template #extra><Button danger type="link" size="small" @click="remove(relation.relationCode, index)">删除</Button></template>
        <DynamicProcessForm
          :model-value="row.data"
          :schema-json="relation.childSchemaJson"
          :ui-schema-json="relation.childUiSchemaJson"
          @update:model-value="(data) => updateRow(relation.relationCode, index, { ...row, data })"
        />
        <NestedRuntimeForms
          v-if="(depth ?? 1) < 3"
          :current-form-id="relation.childFormDefinitionId"
          :depth="(depth ?? 1) + 1"
          :model-value="row.children ?? {}"
          :relations="relations"
          @update:model-value="(children) => updateRow(relation.relationCode, index, { ...row, children })"
        />
      </Card>
    </section>
  </div>
</template>

<style scoped>
.nested-relations { display: grid; gap: 16px; margin-top: 16px; }
.nested-relation { padding: 12px; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; }
.relation-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.relation-header span { color: #64748b; font-size: 12px; }
.nested-row { margin-bottom: 12px; }
</style>

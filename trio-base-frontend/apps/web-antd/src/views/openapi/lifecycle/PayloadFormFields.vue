<script setup lang="ts">
import type { PayloadFormField, PayloadFormState } from './payload-form';

import {
  Button,
  FormItem,
  Input,
  InputNumber,
  Select,
  Switch,
} from 'ant-design-vue';

import AssetReferenceSelect from './AssetReferenceSelect.vue';
import { createArrayItemState } from './payload-form';

const props = defineProps<{
  fields: PayloadFormField[];
  modelValue: PayloadFormState;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: PayloadFormState];
}>();

const Textarea = Input.TextArea;
const tokenSeparators = [',', '，'];

function arrayValue(field: PayloadFormField): PayloadFormState[] {
  const value = props.modelValue[field.key];
  return Array.isArray(value) ? (value as PayloadFormState[]) : [];
}

function fieldClass(field: PayloadFormField) {
  return ['payload-field', field.wide ? 'payload-field-wide' : undefined];
}

function inputValue(field: PayloadFormField) {
  const value = props.modelValue[field.key];
  return value === undefined || value === null ? '' : String(value);
}

function numberValue(field: PayloadFormField) {
  const value = props.modelValue[field.key];
  return typeof value === 'number' ? value : undefined;
}

function referenceValue(field: PayloadFormField) {
  const value = props.modelValue[field.key];
  if (field.multiple) {
    return Array.isArray(value) ? value.map((item) => String(item)) : [];
  }
  return typeof value === 'string' ? value : undefined;
}

function resolvedReference(field: PayloadFormField) {
  if (field.referenceDependsOn && field.referenceMap) {
    const dependencyValue = props.modelValue[field.referenceDependsOn];
    return typeof dependencyValue === 'string' ? field.referenceMap[dependencyValue] : undefined;
  }
  return field.reference;
}

function objectValue(field: PayloadFormField): PayloadFormState {
  const value = props.modelValue[field.key];
  return typeof value === 'object' && value !== null && !Array.isArray(value)
    ? (value as PayloadFormState)
    : {};
}

function selectValue(field: PayloadFormField) {
  const value = props.modelValue[field.key];
  return typeof value === 'string' ? value : undefined;
}

function tagsValue(field: PayloadFormField) {
  const value = props.modelValue[field.key];
  return Array.isArray(value) ? (value as string[]) : [];
}

function updateArrayRow(
  field: PayloadFormField,
  index: number,
  row: PayloadFormState,
) {
  const rows = [...arrayValue(field)];
  rows[index] = row;
  updateField(field.key, rows);
}

function addArrayRow(field: PayloadFormField) {
  updateField(field.key, [...arrayValue(field), createArrayItemState(field)]);
}

function removeArrayRow(field: PayloadFormField, index: number) {
  updateField(
    field.key,
    arrayValue(field).filter((_, rowIndex) => rowIndex !== index),
  );
}

function updateField(key: string, value: unknown) {
  const next = { ...props.modelValue, [key]: value };
  const field = props.fields.find((item) => item.key === key);
  for (const clearKey of field?.clearKeysOnChange ?? []) {
    next[clearKey] = '';
  }
  emit('update:modelValue', next);
}
</script>

<template>
  <div class="payload-fields">
    <FormItem
      v-for="field in fields"
      :key="field.key"
      :class="fieldClass(field)"
      :extra="field.help"
      :label="field.label"
      :required="field.required"
    >
      <Textarea
        v-if="field.kind === 'textarea' || field.kind === 'json'"
        :placeholder="field.placeholder"
        :rows="field.rows ?? (field.kind === 'json' ? 8 : 4)"
        :value="inputValue(field)"
        @update:value="updateField(field.key, $event)"
      />
      <InputNumber
        v-else-if="field.kind === 'number'"
        class="w-full"
        :max="field.max"
        :min="field.min"
        :precision="field.precision"
        :value="numberValue(field)"
        @update:value="updateField(field.key, $event)"
      />
      <Switch
        v-else-if="field.kind === 'boolean'"
        :checked="Boolean(modelValue[field.key])"
        checked-children="是"
        un-checked-children="否"
        @update:checked="updateField(field.key, $event)"
      />
      <Select
        v-else-if="field.kind === 'select'"
        allow-clear
        :options="field.options"
        :placeholder="field.placeholder"
        :value="selectValue(field)"
        @update:value="updateField(field.key, $event)"
      />
      <Select
        v-else-if="field.kind === 'tags'"
        mode="tags"
        :placeholder="field.placeholder"
        :token-separators="tokenSeparators"
        :value="tagsValue(field)"
        @update:value="updateField(field.key, $event)"
      />
      <AssetReferenceSelect
        v-else-if="field.kind === 'reference' && resolvedReference(field)"
        :model-value="referenceValue(field)"
        :multiple="field.multiple"
        :placeholder="field.placeholder"
        :reference="resolvedReference(field)!"
        @update:model-value="updateField(field.key, $event)"
      />
      <div v-else-if="field.kind === 'object'" class="payload-object">
        <PayloadFormFields
          :fields="field.fields ?? []"
          :model-value="objectValue(field)"
          @update:model-value="updateField(field.key, $event)"
        />
      </div>
      <div v-else-if="field.kind === 'array'" class="payload-array">
        <div
          v-for="(row, index) in arrayValue(field)"
          :key="index"
          class="payload-array-row"
        >
          <div class="payload-array-row-title">
            <span>{{ field.label }} {{ index + 1 }}</span>
            <Button danger size="small" type="link" @click="removeArrayRow(field, index)">
              移除
            </Button>
          </div>
          <PayloadFormFields
            :fields="field.itemFields ?? []"
            :model-value="row"
            @update:model-value="updateArrayRow(field, index, $event)"
          />
        </div>
        <Button size="small" @click="addArrayRow(field)">
          {{ field.addLabel ?? `添加${field.label}` }}
        </Button>
      </div>
      <Input
        v-else
        :placeholder="field.placeholder"
        :value="inputValue(field)"
        @update:value="updateField(field.key, $event)"
      />
    </FormItem>
  </div>
</template>

<style scoped>
.payload-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.payload-field-wide {
  grid-column: 1 / -1;
}

.payload-array,
.payload-object {
  width: 100%;
}

.payload-array-row {
  margin-bottom: 12px;
  padding: 12px 12px 0;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
}

.payload-array-row-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  color: #475569;
  font-size: 13px;
  font-weight: 500;
}

@media (max-width: 720px) {
  .payload-fields {
    grid-template-columns: 1fr;
  }
}
</style>

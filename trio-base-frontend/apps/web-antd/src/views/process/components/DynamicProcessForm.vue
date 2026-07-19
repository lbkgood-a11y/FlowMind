<script setup lang="ts">
import type { ClientFormError } from './process-form';

import { computed, ref, watch } from 'vue';

import {
  DatePicker,
  Form,
  FormItem,
  Input,
  InputNumber,
  Select,
  Switch,
} from 'ant-design-vue';
import dayjs from 'dayjs';

import {
  getProcessFormFields,
  validateProcessFormData,
} from './process-form';

const props = defineProps<{
  modelValue: Record<string, unknown>;
  schemaJson?: string;
  uiSchemaJson?: string;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, unknown>];
}>();

const Textarea = Input.TextArea;
const fields = computed(() =>
  getProcessFormFields(props.schemaJson, props.uiSchemaJson),
);
const errors = ref<Record<string, string>>({});

watch(
  () => props.schemaJson,
  () => {
    errors.value = {};
  },
);

function updateField(key: string, value: unknown) {
  const next = { ...props.modelValue, [key]: value };
  if (value === undefined || value === null || value === '') {
    delete next[key];
  }
  emit('update:modelValue', next);
  if (errors.value[key]) {
    const nextErrors = { ...errors.value };
    delete nextErrors[key];
    errors.value = nextErrors;
  }
}

function dateValue(key: string) {
  const value = props.modelValue[key];
  return typeof value === 'string' && value ? dayjs(value) : undefined;
}

function validate(): ClientFormError[] {
  const validationErrors = validateProcessFormData(
    props.schemaJson,
    props.modelValue,
  );
  errors.value = Object.fromEntries(
    validationErrors.map((error) => [error.field, error.message]),
  );
  return validationErrors;
}

function applyServerErrors(fieldErrors: ClientFormError[]) {
  errors.value = Object.fromEntries(
    fieldErrors.map((error) => [error.field, error.message]),
  );
}

defineExpose({ applyServerErrors, validate });
</script>

<template>
  <Form class="dynamic-process-form" layout="vertical">
    <FormItem
      v-for="field in fields"
      :key="field.key"
      :help="errors[field.key]"
      :label="field.label"
      :required="field.required"
      :validate-status="errors[field.key] ? 'error' : undefined"
    >
      <Textarea
        v-if="field.widget === 'textarea'"
        :disabled="field.disabled || field.readOnly"
        :placeholder="field.placeholder"
        :rows="4"
        :value="String(modelValue[field.key] ?? '')"
        @update:value="updateField(field.key, $event)"
      />
      <InputNumber
        v-else-if="field.widget === 'money'"
        class="w-full"
        :disabled="field.disabled || field.readOnly"
        :max="field.schema.maximum"
        :min="field.schema.minimum"
        :precision="2"
        :step="0.01"
        :value="modelValue[field.key] as number | undefined"
        @update:value="updateField(field.key, $event)"
      />
      <InputNumber
        v-else-if="field.widget === 'number' || field.widget === 'integer'"
        class="w-full"
        :disabled="field.disabled || field.readOnly"
        :max="field.schema.maximum"
        :min="field.schema.minimum"
        :precision="field.widget === 'integer' ? 0 : undefined"
        :value="modelValue[field.key] as number | undefined"
        @update:value="updateField(field.key, $event)"
      />
      <Switch
        v-else-if="field.widget === 'boolean'"
        :checked="Boolean(modelValue[field.key])"
        :disabled="field.disabled || field.readOnly"
        @update:checked="updateField(field.key, $event)"
      />
      <Select
        v-else-if="field.widget === 'select'"
        allow-clear
        :disabled="field.disabled || field.readOnly"
        :options="field.schema.enum?.map((value, index) => ({
          label: field.schema.enumNames?.[index] ?? String(value),
          value,
        }))"
        :placeholder="field.placeholder"
        :value="modelValue[field.key] as number | string | undefined"
        @update:value="updateField(field.key, $event)"
      />
      <DatePicker
        v-else-if="field.widget === 'date'"
        class="w-full"
        :disabled="field.disabled || field.readOnly"
        :placeholder="field.placeholder"
        :value="dateValue(field.key)"
        value-format="YYYY-MM-DD"
        @update:value="updateField(field.key, $event)"
      />
      <Input
        v-else
        :disabled="field.disabled || field.readOnly"
        :maxlength="field.schema.maxLength"
        :placeholder="field.placeholder"
        :value="String(modelValue[field.key] ?? '')"
        @update:value="updateField(field.key, $event)"
      />
    </FormItem>
  </Form>
</template>

<style scoped>
.dynamic-process-form {
  width: 100%;
}
</style>

<script setup lang="ts">
import type {
  PayloadFormDefinition,
  PayloadFormState,
} from './payload-form';

import { Form } from 'ant-design-vue';

import PayloadFormFields from './PayloadFormFields.vue';

defineProps<{
  form: PayloadFormDefinition;
  modelValue: PayloadFormState;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: PayloadFormState];
}>();
</script>

<template>
  <Form class="dynamic-payload-form" layout="vertical">
    <section v-for="group in form.groups" :key="group.title" class="payload-group">
      <div class="payload-group-title">{{ group.title }}</div>
      <div v-if="group.description" class="payload-group-description">
        {{ group.description }}
      </div>
      <PayloadFormFields
        :fields="group.fields"
        :model-value="modelValue"
        @update:model-value="emit('update:modelValue', $event)"
      />
    </section>
  </Form>
</template>

<style scoped>
.dynamic-payload-form {
  width: 100%;
}

.payload-group + .payload-group {
  padding-top: 12px;
  border-top: 1px solid #edf2f7;
}

.payload-group-title {
  margin-bottom: 8px;
  color: #1f2937;
  font-size: 15px;
  font-weight: 600;
}

.payload-group-description {
  margin-bottom: 12px;
  color: #64748b;
  font-size: 13px;
}
</style>

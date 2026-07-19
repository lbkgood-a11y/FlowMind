<script setup lang="ts">
import { computed } from 'vue';

import { Tag } from 'ant-design-vue';

const props = defineProps<{
  label?: string;
  status?: string;
  statusGroup?: string;
}>();

const color = computed(() => {
  const value = (props.statusGroup || props.status || '').toUpperCase();
  if (['TERMINAL', 'SUCCEEDED', 'COMPLETED', 'APPROVED', 'CLOSED'].some((item) => value.includes(item))) {
    return 'success';
  }
  if (['FAILED', 'REJECTED', 'CANCELLED', 'ERROR'].some((item) => value.includes(item))) {
    return 'error';
  }
  if (['PENDING', 'RUNNING', 'PROGRESS', 'SUBMITTED'].some((item) => value.includes(item))) {
    return 'processing';
  }
  return 'default';
});
</script>

<template>
  <Tag :color="color">{{ label || status || '-' }}</Tag>
</template>

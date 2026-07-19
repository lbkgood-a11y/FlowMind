<script setup lang="ts">
import type { ActionApi } from '#/api/action-client';

import { computed } from 'vue';

import { Alert } from 'ant-design-vue';

const props = defineProps<{
  result?: ActionApi.GlobalActionResult;
}>();

const type = computed(() => {
  if (!props.result?.status) return 'info';
  return ['FAILED', 'REJECTED', 'CANCELLED'].includes(props.result.status)
    ? 'error'
    : ['SUCCEEDED'].includes(props.result.status)
      ? 'success'
      : 'info';
});

const message = computed(
  () => props.result?.message || props.result?.status || props.result?.actionId,
);
</script>

<template>
  <Alert
    v-if="result"
    :message="message"
    :type="type"
    banner
    show-icon
  />
</template>

<script setup lang="ts">
import type { ActionApi } from '#/api/action-client';

import { computed } from 'vue';

import { Button, Tooltip } from 'ant-design-vue';

const props = withDefaults(
  defineProps<{
    availability?: ActionApi.ActionCandidateValidationResult;
    danger?: boolean;
    label: string;
    loading?: boolean;
    primary?: boolean;
  }>(),
  {
    availability: undefined,
    danger: false,
    loading: false,
    primary: false,
  },
);

const emit = defineEmits<{
  execute: [];
}>();

const visible = computed(() => props.availability?.visible !== false);
const enabled = computed(() => props.availability?.enabled !== false);
const disabledReason = computed(() => props.availability?.disabledReason);
const dangerous = computed(() => props.danger || props.availability?.danger === true);
</script>

<template>
  <Tooltip v-if="visible" :title="!enabled ? disabledReason : undefined">
    <Button
      :danger="dangerous"
      :disabled="!enabled"
      :loading="loading"
      :type="primary ? 'primary' : 'default'"
      size="small"
      @click="emit('execute')"
    >
      <slot>{{ label }}</slot>
    </Button>
  </Tooltip>
</template>

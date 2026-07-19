<script setup lang="ts">
import type { ActionApi } from '#/api/action-client';

import { Modal } from 'ant-design-vue';

const props = defineProps<{
  confirmation?: ActionApi.ActionConfirmation | boolean;
  danger?: boolean;
}>();

const emit = defineEmits<{
  confirmed: [];
}>();

function confirm() {
  if (!props.confirmation) {
    emit('confirmed');
    return;
  }
  const confirmation =
    props.confirmation === true ? {} : props.confirmation;
  Modal.confirm({
    cancelText: '取消',
    content: confirmation.message,
    okButtonProps: { danger: props.danger || confirmation.riskLevel === 'CRITICAL' },
    okText: confirmation.confirmLabel ?? '确认',
    onOk: () => emit('confirmed'),
    title: confirmation.title ?? '确认执行该操作？',
  });
}
</script>

<template>
  <span class="tb-action-confirmation-wrapper" @click.stop="confirm">
    <slot />
  </span>
</template>

<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    compact?: boolean;
    fullscreen?: boolean;
    pattern?: 'document' | 'master-detail' | 'multi-table' | 'single-table';
  }>(),
  {
    compact: true,
    fullscreen: false,
    pattern: 'single-table',
  },
);
</script>

<template>
  <div
    class="tb-business-page"
    :class="[
      `tb-business-page--${props.pattern}`,
      {
        'erp-compact-page': props.compact,
        'is-block-fullscreen': props.fullscreen,
      },
    ]"
  >
    <slot name="query" />
    <slot name="toolbar" />
    <main class="tb-business-page__body">
      <slot />
    </main>
  </div>
</template>

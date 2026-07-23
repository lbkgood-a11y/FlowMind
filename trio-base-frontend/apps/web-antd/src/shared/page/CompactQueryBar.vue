<script setup lang="ts">
import { PageQueryActions } from '../page-actions';

withDefaults(
  defineProps<{
    collapsed?: boolean;
    columns?: 2 | 3 | 4;
  }>(),
  {
    collapsed: false,
    columns: 3,
  },
);
</script>

<template>
  <section class="query-panel tb-query-bar">
    <div
      class="query-grid tb-query-bar__grid"
      :class="{ collapsed }"
      :style="{ '--tb-query-columns': columns }"
    >
      <slot />
      <PageQueryActions class="query-actions tb-query-bar__actions">
        <slot name="actions" />
      </PageQueryActions>
    </div>
  </section>
</template>

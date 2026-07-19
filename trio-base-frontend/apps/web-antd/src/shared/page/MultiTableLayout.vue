<script setup lang="ts">
withDefaults(
  defineProps<{
    columns?: 1 | 2 | 3;
  }>(),
  {
    columns: 2,
  },
);
</script>

<template>
  <section class="tb-multi-table" :style="{ '--tb-multi-columns': columns }">
    <slot />
  </section>
</template>

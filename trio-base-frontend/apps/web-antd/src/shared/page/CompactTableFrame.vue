<script setup lang="ts">
withDefaults(
  defineProps<{
    bordered?: boolean;
  }>(),
  {
    bordered: true,
  },
);
</script>

<template>
  <section class="list-panel tb-table-frame" :class="{ 'tb-table-frame--bordered': bordered }">
    <div class="table-frame tb-table-frame__body">
      <slot />
    </div>
    <div v-if="$slots.footer" class="table-footer tb-table-frame__footer">
      <slot name="footer" />
    </div>
  </section>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    masterWidth?: string;
  }>(),
  {
    masterWidth: '320px',
  },
);
</script>

<template>
  <section class="tb-master-detail" :style="{ '--tb-master-width': masterWidth }">
    <aside class="tb-master-detail__master">
      <slot name="master" />
    </aside>
    <section class="tb-master-detail__detail">
      <slot />
    </section>
  </section>
</template>

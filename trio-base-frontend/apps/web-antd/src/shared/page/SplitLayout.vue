<script setup lang="ts">
withDefaults(
  defineProps<{
    leftWidth?: string;
    rightWidth?: string;
  }>(),
  {
    leftWidth: '280px',
    rightWidth: '1fr',
  },
);
</script>

<template>
  <section
    class="tb-split-layout"
    :style="{ '--tb-left-width': leftWidth, '--tb-right-width': rightWidth }"
  >
    <aside class="tb-split-layout__left">
      <slot name="left" />
    </aside>
    <section class="tb-split-layout__right">
      <slot />
    </section>
  </section>
</template>

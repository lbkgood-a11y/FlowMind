<script setup lang="ts">
withDefaults(
  defineProps<{
    sticky?: boolean;
    subtitle?: string;
    title?: string;
  }>(),
  {
    sticky: false,
    subtitle: undefined,
    title: undefined,
  },
);
</script>

<template>
  <section class="toolbar tb-toolbar" :class="{ 'tb-toolbar--sticky': sticky }">
    <div class="tb-toolbar__title">
      <slot name="title">
        <h2 v-if="title">{{ title }}</h2>
        <span v-if="subtitle">{{ subtitle }}</span>
      </slot>
    </div>
    <div class="toolbar-actions tb-toolbar__actions">
      <slot />
    </div>
  </section>
</template>

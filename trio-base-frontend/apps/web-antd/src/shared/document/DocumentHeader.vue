<script setup lang="ts">
defineProps<{
  subtitle?: string;
  title: string;
}>();
</script>

<template>
  <header class="tb-document-header">
    <div class="tb-document-header__title">
      <h1>{{ title }}</h1>
      <span v-if="subtitle">{{ subtitle }}</span>
    </div>
    <div class="tb-document-header__status">
      <slot name="status" />
    </div>
    <div class="tb-document-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

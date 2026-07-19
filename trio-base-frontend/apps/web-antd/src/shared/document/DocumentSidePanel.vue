<script setup lang="ts">
defineProps<{
  title?: string;
}>();
</script>

<template>
  <section class="tb-document-side-panel">
    <h3 v-if="title">{{ title }}</h3>
    <slot />
  </section>
</template>

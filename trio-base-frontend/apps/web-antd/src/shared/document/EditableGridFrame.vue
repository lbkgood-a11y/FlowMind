<script setup lang="ts">
defineProps<{
  title?: string;
}>();
</script>

<template>
  <section class="tb-editable-grid">
    <div v-if="title || $slots.actions" class="tb-editable-grid__header">
      <h3 v-if="title">{{ title }}</h3>
      <div>
        <slot name="actions" />
      </div>
    </div>
    <slot />
  </section>
</template>

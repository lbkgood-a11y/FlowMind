<script setup lang="ts">
import { PageToolbar } from '../page-actions';

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
    <PageToolbar>
      <template #start>
        <div class="tb-toolbar__title">
          <slot name="title">
            <h2 v-if="title">{{ title }}</h2>
            <span v-if="subtitle">{{ subtitle }}</span>
          </slot>
        </div>
      </template>
      <template #end>
        <div class="toolbar-actions tb-toolbar__actions">
          <slot />
        </div>
      </template>
    </PageToolbar>
  </section>
</template>

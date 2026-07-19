<script setup lang="ts">
import type { BusinessCatalogApi } from '../business/business-catalog-client';

import { Timeline, TimelineItem } from 'ant-design-vue';

defineProps<{
  entries?: BusinessCatalogApi.BusinessTimelineEntry[];
  title?: string;
}>();
</script>

<template>
  <section class="tb-timeline-panel">
    <h3 v-if="title">{{ title }}</h3>
    <Timeline v-if="entries?.length">
      <TimelineItem v-for="entry in entries" :key="entry.eventId">
        <div class="tb-timeline-panel__item">
          <strong>{{ entry.displayName || entry.eventType }}</strong>
          <span>{{ entry.occurredAt }}</span>
        </div>
      </TimelineItem>
    </Timeline>
    <slot v-else name="empty" />
  </section>
</template>

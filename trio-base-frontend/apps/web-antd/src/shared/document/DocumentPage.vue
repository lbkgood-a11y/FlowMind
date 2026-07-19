<script setup lang="ts">
import BusinessPageScaffold from '../page/BusinessPageScaffold.vue';
</script>

<template>
  <BusinessPageScaffold pattern="document">
    <section class="tb-document-page">
      <slot name="header" />
      <div class="tb-document-page__body">
        <main class="tb-document-page__main">
          <slot />
        </main>
        <aside v-if="$slots.side" class="tb-document-page__side">
          <slot name="side" />
        </aside>
      </div>
      <slot name="footer" />
    </section>
  </BusinessPageScaffold>
</template>

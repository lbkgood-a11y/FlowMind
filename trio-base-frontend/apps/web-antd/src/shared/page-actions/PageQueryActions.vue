<template>
  <div class="tb-page-query-actions" data-testid="page-query-actions">
    <slot name="reset" />
    <slot name="submit" />
    <slot />
  </div>
</template>

<style scoped>
.tb-page-query-actions {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: flex-end;
}
</style>

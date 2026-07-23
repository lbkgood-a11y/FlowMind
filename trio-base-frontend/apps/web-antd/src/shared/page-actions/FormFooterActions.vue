<template>
  <div class="tb-form-footer-actions" data-testid="form-footer-actions">
    <slot name="cancel" />
    <slot name="primary" />
    <slot />
  </div>
</template>

<style scoped>
.tb-form-footer-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: flex-end;
  width: 100%;
}
</style>

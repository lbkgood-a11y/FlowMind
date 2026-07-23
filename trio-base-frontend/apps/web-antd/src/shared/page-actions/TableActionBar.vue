<template>
  <div class="tb-table-action-bar" data-testid="table-action-bar"><slot /></div>
</template>

<style scoped>
.tb-table-action-bar {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 0 4px;
  align-items: center;
  justify-content: flex-start;
}
</style>

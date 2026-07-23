<template>
  <div class="tb-page-toolbar" data-testid="page-toolbar">
    <div class="tb-page-toolbar__start"><slot name="start" /></div>
    <div class="tb-page-toolbar__end"><slot name="end" /><slot /></div>
  </div>
</template>

<style scoped>
.tb-page-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.tb-page-toolbar__start,
.tb-page-toolbar__end {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.tb-page-toolbar__end { margin-left: auto; }
</style>

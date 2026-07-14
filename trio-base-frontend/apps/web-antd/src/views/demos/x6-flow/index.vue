<script setup lang="ts">
/**
 * X6 流程设计器测试页
 * 验证 X6 在 Vue Vben Admin 中的渲染效果
 * 不会生产代码，只用于评估
 */
import { onMounted, onUnmounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Button, Space } from 'ant-design-vue';
import {
  Clipboard,
  Graph,
  History,
  Keyboard,
  Scroller,
  Selection,
  Shape,
  Snapline,
} from '@antv/x6';

const containerRef = ref<HTMLDivElement>();
const graph = ref<Graph>();

// ── 自定义节点定义 ──
Shape.HTML.register({
  shape: 'approval-node',
  width: 160,
  height: 50,
  html() {
    const div = document.createElement('div');
    div.style.width = '100%';
    div.style.height = '100%';
    div.style.borderRadius = '4px';
    div.style.border = '2px solid #1677ff';
    div.style.background = '#e6f4ff';
    div.style.display = 'flex';
    div.style.alignItems = 'center';
    div.style.justifyContent = 'center';
    div.style.fontSize = '13px';
    div.style.color = '#1677ff';
    div.style.fontWeight = '500';
    div.innerText = '📋 审批节点';
    return div;
  },
});

Shape.HTML.register({
  shape: 'condition-node',
  width: 50,
  height: 50,
  html() {
    const div = document.createElement('div');
    div.style.width = '100%';
    div.style.height = '100%';
    div.style.borderRadius = '50%';
    div.style.border = '2px solid #fa8c16';
    div.style.background = '#fff7e6';
    div.style.display = 'flex';
    div.style.alignItems = 'center';
    div.style.justifyContent = 'center';
    div.style.fontSize = '12px';
    div.style.color = '#fa8c16';
    div.style.fontWeight = '500';
    div.innerText = '条件';
    return div;
  },
});

Shape.HTML.register({
  shape: 'end-node',
  width: 50,
  height: 50,
  html() {
    const inner = document.createElement('div');
    inner.style.width = '30px';
    inner.style.height = '30px';
    inner.style.borderRadius = '50%';
    inner.style.border = '2px solid #ff4d4f';
    inner.style.background = '#fff2f0';
    inner.style.display = 'flex';
    inner.style.alignItems = 'center';
    inner.style.justifyContent = 'center';
    inner.style.fontSize = '10px';
    inner.style.color = '#ff4d4f';
    inner.innerText = '结束';
    return inner;
  },
  inherit: 'html',
});

// ── 创建画布 ──
onMounted(() => {
  const instance = new Graph({
    container: containerRef.value!,
    autoResize: true,
    background: { color: '#fafafa' },
    grid: { visible: true, type: 'doubleMesh' },
    connecting: {
      router: 'manhattan',
      connector: 'smooth',
      allowBlank: false,
      snap: { radius: 20 },
      allowMulti: false,
    },
  });

  instance.use(new Selection({ rubberband: true, showNodeSelectionBox: true }));
  instance.use(new Clipboard());
  instance.use(new History({ enabled: true }));
  instance.use(new Keyboard());
  instance.use(new Scroller({ pannable: true, autoResize: true }));
  instance.use(new Snapline({ sharp: true }));
  graph.value = instance;

  // ── 添加示例节点 ──
  instance.addNode({
    shape: 'approval-node',
    id: 'start',
    x: 270,
    y: 20,
  });

  instance.addNode({
    shape: 'condition-node',
    id: 'cond',
    x: 285,
    y: 120,
  });

  instance.addNode({
    shape: 'approval-node',
    id: 'finance',
    x: 100,
    y: 220,
  });

  instance.addNode({
    shape: 'approval-node',
    id: 'dept',
    x: 440,
    y: 220,
  });

  instance.addNode({
    shape: 'end-node',
    id: 'end',
    x: 285,
    y: 330,
  });

  // ── 添加连线 ──
  instance.addEdge({
    source: 'start',
    target: 'cond',
    attrs: { line: { stroke: '#1677ff', strokeWidth: 2, targetMarker: 'classic' } },
  });

  instance.addEdge({
    source: { cell: 'cond', anchor: { name: 'left' } },
    target: 'finance',
    attrs: { line: { stroke: '#fa8c16', strokeWidth: 2, targetMarker: 'classic' } },
    labels: [{ attrs: { text: { text: '金额 > 5000' } }, position: 0.5 }],
  });

  instance.addEdge({
    source: { cell: 'cond', anchor: { name: 'right' } },
    target: 'dept',
    attrs: { line: { stroke: '#52c41a', strokeWidth: 2, targetMarker: 'classic' } },
    labels: [{ attrs: { text: { text: '金额 ≤ 5000' } }, position: 0.5 }],
  });

  instance.addEdge({
    source: 'finance',
    target: 'end',
    attrs: { line: { stroke: '#1677ff', strokeWidth: 2, targetMarker: 'classic' } },
  });

  instance.addEdge({
    source: 'dept',
    target: 'end',
    attrs: { line: { stroke: '#1677ff', strokeWidth: 2, targetMarker: 'classic' } },
  });

  instance.centerContent();
});

onUnmounted(() => {
  graph.value?.dispose();
});

function exportJson() {
  if (!graph.value) return;
  const json = graph.value.toJSON();
  const blob = new Blob([JSON.stringify(json, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = 'x6-flow.json';
  a.click();
  URL.revokeObjectURL(url);
}
</script>

<template>
  <Page auto-content-height>
    <div class="flex h-full flex-col gap-3 p-3 overflow-hidden">
      <!-- 工具栏 -->
      <div class="flex items-center justify-between flex-shrink-0">
        <h2 class="text-base font-semibold m-0">X6 流程设计器</h2>
        <Space>
          <Button @click="exportJson">导出 JSON</Button>
          <Button type="primary" @click="graph?.centerContent()">居中</Button>
          <Button @click="graph?.undo()">撤销</Button>
          <Button @click="graph?.redo()">重做</Button>
        </Space>
      </div>

      <!-- 主体 -->
      <div class="flex flex-1 gap-3 min-h-0">
        <!-- 左侧节点面板 -->
        <div class="w-36 flex-shrink-0 border border-gray-200 rounded-lg bg-white p-3 flex flex-col gap-2">
          <div class="text-xs font-medium text-gray-400 uppercase tracking-wide">节点类型</div>
          <div
            v-for="n in [
              { label: '审批', bg: '#e6f4ff', color: '#1677ff' },
              { label: '条件', bg: '#fff7e6', color: '#fa8c16' },
              { label: '抄送', bg: '#f6ffed', color: '#52c41a' },
              { label: '服务任务', bg: '#f9f0ff', color: '#722ed1' },
              { label: '结束', bg: '#fff2f0', color: '#ff4d4f' },
            ]"
            :key="n.label"
            class="px-3 py-2 border rounded-md text-sm text-center cursor-grab select-none hover:shadow-sm transition-shadow"
            :style="{ background: n.bg, borderColor: n.color, color: n.color }"
          >
            {{ n.label }}
          </div>
        </div>

        <!-- 中间画布 -->
        <div ref="containerRef" class="flex-1 border border-gray-200 rounded-lg overflow-hidden bg-white" />
      </div>
    </div>
  </Page>
</template>

<style scoped>
:deep(.x6-graph-scroller) {
  background: #fafafa;
}
:deep(.x6-widget-minimap) {
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
}
</style>

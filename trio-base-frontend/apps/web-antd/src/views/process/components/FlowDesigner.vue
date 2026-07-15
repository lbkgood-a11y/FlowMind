<script setup lang="ts">
/**
 * X6 流程设计器核心组件
 * 功能：画布拖拽 + 节点配置 + JSON 双向绑定
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';

import {
  Cell,
  Clipboard,
  Dnd,
  Edge,
  Graph,
  History,
  Keyboard,
  MiniMap,
  Node,
  Scroller,
  Selection,
  Shape,
  Snapline,
} from '@antv/x6';
import { Button, message, Space, Tooltip } from 'ant-design-vue';

import {
  buildParticipantAssignment,
  type ParticipantAssignment,
  participantAssignmentValue,
  type ParticipantType,
} from './process-designer';

import '@antv/x6-plugin-dnd/es/index.css';
import '@antv/x6-plugin-minimap/es/index.css';
import '@antv/x6-plugin-scroller/es/index.css';
import '@antv/x6-plugin-selection/es/index.css';
import '@antv/x6-plugin-snapline/es/index.css';

// ── 节点类型定义 ──
interface NodeTypeDef {
  type: string;
  label: string;
  color: string;
  bg: string;
  icon: string;
  width: number;
  height: number;
}

const props = withDefaults(
  defineProps<{
    modelValue?: string;
    readonly?: boolean;
  }>(),
  { modelValue: '', readonly: false },
);

const emit = defineEmits<{
  change: [json: string];
}>();

const NODE_TYPES: NodeTypeDef[] = [
  { type: 'START', label: '开始', color: '#8c8c8c', bg: '#fafafa', icon: '▶', width: 40, height: 40 },
  { type: 'APPROVAL', label: '审批', color: '#1677ff', bg: '#e6f4ff', icon: '📋', width: 128, height: 42 },
  { type: 'COUNTERSIGN', label: '会签', color: '#722ed1', bg: '#f9f0ff', icon: '👥', width: 128, height: 42 },
  { type: 'CONDITION', label: '条件', color: '#fa8c16', bg: '#fff7e6', icon: '◇', width: 40, height: 40 },
  { type: 'NOTIFY', label: '抄送', color: '#52c41a', bg: '#f6ffed', icon: '📧', width: 112, height: 42 },
  { type: 'SERVICE_TASK', label: '服务任务', color: '#eb2f96', bg: '#fff0f6', icon: '⚙', width: 112, height: 42 },
  { type: 'END', label: '结束', color: '#ff4d4f', bg: '#fff2f0', icon: '⏹', width: 40, height: 40 },
];

const NODE_TYPE_MAP = new Map(NODE_TYPES.map((n) => [n.type, n]));

interface NodeData {
  nodeType: string;
  name: string;
  assignment?: ParticipantAssignment;
  strategy?: string;
  conditionExpr?: string;
}

const containerRef = ref<HTMLDivElement>();
const selectedNode = ref<NodeData | null>(null);
const selectedEdge = ref<null | { condition: string }>(null);
const graph = ref<Graph | null>(null);
let dnd: Dnd | null = null;
let loadingFromModel = false;
let lastLoadedModel = '';

// ── 属性面板状态 ──
const editName = ref('');
const editAssignmentType = ref<ParticipantType>('ROLE');
const editAssignmentValue = ref('');
const editDimensionCode = ref('');
const editStrategy = ref('ALL');
const editConditionExpr = ref('');
const paletteOpen = ref(true);
const zoomPercent = ref(100);

const MIN_ZOOM = 0.25;
const MAX_ZOOM = 2;
const ZOOM_STEP = 0.1;

const showPropertyPanel = computed(() => !!selectedNode.value || !!selectedEdge.value);

// ── 注册自定义节点 ──
function registerNodes() {
  NODE_TYPES.forEach((def) => {
    const isRound = def.type === 'START' || def.type === 'CONDITION' || def.type === 'END';

    Shape.HTML.register({
      shape: `flow-${def.type}`,
      width: def.width,
      height: def.height,
      html(cell: Cell) {
        const data = cell.getData() as NodeData;
        const name = data?.name || def.label;
        const outer = document.createElement('div');
        outer.style.cssText = `
          width: ${def.width}px; height: ${def.height}px;
          box-sizing: border-box;
          border-radius: ${isRound ? '50%' : '6px'};
          border: 2px solid ${def.color};
          background: ${def.bg};
          display: flex; flex-direction: column;
          align-items: center; justify-content: center;
          cursor: pointer; position: relative;
          box-shadow: 0 1px 3px rgba(0,0,0,0.08);
          transition: box-shadow 0.15s;
        `;

        if (isRound) {
          outer.style.width = `${def.width - 8}px`;
          outer.style.height = `${def.height - 8}px`;
          outer.style.margin = '4px';
          outer.style.fontSize = '11px';
          outer.style.fontWeight = '500';
          outer.style.color = def.color;
          outer.innerText = name.length > 4 ? name.slice(0, 4) + '..' : name;
        } else {
          const label = document.createElement('div');
          label.style.cssText = 'font-size:11px;color:#999;font-weight:400;line-height:1.2';
          label.innerText = def.label;

          const title = document.createElement('div');
          title.style.cssText = 'font-size:13px;color:#333;font-weight:500;line-height:1.4;text-align:center;padding:0 4px';
          title.innerText = name;

          outer.appendChild(title);
          outer.appendChild(label);

          // 选中高亮
          outer.addEventListener('click', () => {
            if (graph.value) {
              graph.value.cleanSelection();
              graph.value.select(cell.id);
            }
          });
        }

        return outer;
      },
    });
  });
}

// ── 创建画布 ──
function createGraph() {
  if (!containerRef.value) return;

  const g: Graph = new Graph({
    container: containerRef.value,
    autoResize: true,
    background: { color: '#fafafa' },
    grid: { visible: true, type: 'doubleMesh' },
    interacting: () => !props.readonly,
    highlighting: {
      nodeAvailable: { name: 'stroke', args: { padding: 4, attrs: { stroke: '#1677ff', strokeWidth: 2 } } },
    },
    connecting: {
      router: { name: 'manhattan', args: { step: 24 } },
      connector: { name: 'smooth' },
      snap: { radius: 20 },
      allowBlank: false,
      allowMulti: false,
      allowLoop: false,
      validateConnection: () => !props.readonly,
      createEdge() {
        return new Edge({
          attrs: { line: { stroke: '#8c8c8c', strokeWidth: 2, targetMarker: { name: 'classic' } } },
          labels: [{ attrs: { text: { text: '条件' } }, position: { distance: 0.5 } }],
        });
      },
    },
  });

  // ── 插件 ──
  g.use(new Selection({ rubberband: true, showNodeSelectionBox: true }));
  g.use(new Clipboard());
  g.use(new History({ enabled: true }));
  g.use(new Keyboard());
  g.use(new Snapline({ sharp: true }));
  g.use(new Scroller({ pannable: true, autoResize: true }));
  g.use(new MiniMap({ container: document.createElement('div'), width: 200, height: 150 }));

  // 快捷键
  g.bindKey('ctrl+z', () => !props.readonly && g.undo());
  g.bindKey('ctrl+shift+z', () => !props.readonly && g.redo());
  g.bindKey('ctrl+c', () => {
    if (props.readonly) return;
    const cells = g.getSelectedCells();
    if (cells.length) g.copy(cells);
  });
  g.bindKey('ctrl+v', () => !props.readonly && g.paste());
  g.bindKey('delete', () => {
    if (props.readonly) return;
    const cells = g.getSelectedCells();
    if (cells.length) g.removeCells(cells);
  });

  // ── 选中事件 → 填充属性面板 ──
  g.on('cell:click', ({ cell }: { cell: Cell }) => {
    if (cell.isNode()) {
      const data = (cell.getData() || {}) as NodeData;
      selectedNode.value = { ...data };
      editName.value = data.name || '';
      editAssignmentType.value = data.assignment?.type || 'ROLE';
      editAssignmentValue.value = participantAssignmentValue(data.assignment);
      editDimensionCode.value = data.assignment?.dimensionCode || '';
      editStrategy.value = data.strategy || 'ALL';
      selectedEdge.value = null;
    } else if (cell.isEdge()) {
      const label = cell.getLabels()[0];
      selectedEdge.value = { condition: (label?.attrs?.text as any)?.text || 'true' };
      editConditionExpr.value = selectedEdge.value.condition;
      selectedNode.value = null;
    }
  });

  g.on('blank:click', () => {
    selectedNode.value = null;
    selectedEdge.value = null;
  });

  g.on('scale', ({ sx }: { sx: number }) => {
    zoomPercent.value = Math.round(sx * 100);
  });

  graph.value = g;

  // ── DnD ──
  dnd = new Dnd({
    target: g,
    scaled: false,
    getDragNode(node) {
      return node.clone({ keepId: true });
    },
    getDropNode(node) {
      return node.clone({ keepId: true });
    },
    validateNode(droppingNode) {
      // 防止重叠
      const existing = g.getNodes().find((n) => {
        const p1 = n.getPosition();
        const p2 = droppingNode.getPosition();
        return Math.abs(p1.x - p2.x) < 40 && Math.abs(p1.y - p2.y) < 40;
      });
      if (existing) {
        message.warning('节点重叠了，换个位置');
        return false;
      }
      return true;
    },
  });

  // ── 节点变更 → 通知父组件 ──
  g.on('cell:added', () => emitChange());
  g.on('cell:removed', () => emitChange());
  g.on('cell:change:position', () => emitChange());
  g.on('edge:label:change', () => emitChange());

  // ── 加载初始数据 ──
  if (props.modelValue) {
    loadFromJson(props.modelValue);
  }
}

// ── 开始拖拽 ──
function startDrag(type: string, event: DragEvent) {
  if (props.readonly) return;
  if (!dnd || !graph.value) return;
  const def = NODE_TYPE_MAP.get(type);
  if (!def) return;

  const node = graph.value.createNode({
    shape: `flow-${type}`,
    width: def.width,
    height: def.height,
    data: { nodeType: type, name: def.label } as NodeData,
  });

  dnd.start(node, event);
}

function changeZoom(delta: number) {
  graph.value?.zoom(delta, { maxScale: MAX_ZOOM, minScale: MIN_ZOOM });
}

function resetZoom() {
  graph.value?.zoomTo(1, { maxScale: MAX_ZOOM, minScale: MIN_ZOOM });
  graph.value?.centerContent();
}

function fitCanvas() {
  graph.value?.zoomToFit({ maxScale: 1, minScale: MIN_ZOOM, padding: 36 });
  graph.value?.centerContent();
}

// ── 流程包 JSON → 画布 ──
function loadFromJson(jsonStr: string) {
  if (!graph.value) return;
  const g = graph.value;
  loadingFromModel = true;
  g.clearCells();

  try {
    const pkg = JSON.parse(jsonStr);
    const nodes = pkg?.flow?.nodes;
    if (!nodes?.length) return;

    const xMap: Record<string, number> = {};
    nodes.forEach((n: any, i: number) => {
      const def = NODE_TYPE_MAP.get(n.type) ?? NODE_TYPE_MAP.get('APPROVAL')!;
      const nx = n.x ?? 100 + (i % 3) * 200;
      const ny = n.y ?? 40 + Math.floor(i / 3) * 130;
      xMap[n.id] = nx + def.width / 2;

      g.addNode({
        shape: `flow-${n.type}`,
        id: n.id,
        x: nx,
        y: ny,
        width: def.width,
        height: def.height,
        data: {
          nodeType: n.type,
          name: n.name || def.label,
          assignment: n.assignment,
          strategy: n.strategy,
        } as NodeData,
      });
    });

    nodes.forEach((n: any) => {
      if (!n.next?.length) return;
      n.next.forEach((nc: any) => {
        if (!nc.target || !xMap[nc.target]) return;
        g.addEdge({
          source: n.id,
          target: nc.target,
          attrs: { line: { stroke: '#8c8c8c', strokeWidth: 2, targetMarker: { name: 'classic' } } },
          labels: [
            {
              attrs: { text: { text: nc.condition === 'true' ? '通过' : nc.condition || '条件' } },
              position: { distance: 0.5 },
            },
          ],
        });
      });
    });

    g.zoomToFit({ maxScale: 1, padding: 36 });
    g.centerContent();
    lastLoadedModel = jsonStr;
  } catch {
    message.warning('JSON 解析失败');
  } finally {
    loadingFromModel = false;
  }
}

// ── 画布 → 流程包 JSON ──
function toFlowJson(): string {
  if (!graph.value) return '{}';

  const g = graph.value;
  const cells = g.getCells();
  const nodes = cells.filter((c) => c.isNode()) as Node[];
  const edges = cells.filter((c) => c.isEdge()) as Edge[];

  const edgeMap = new Map<string, Array<{ condition: string; target: string }>>();
  edges.forEach((edge) => {
    const source = edge.getSourceCellId();
    const target = edge.getTargetCellId();
    if (!source || !target) return;
    if (!edgeMap.has(source)) edgeMap.set(source, []);
    const labels = edge.getLabels();
    const condition = String(labels[0]?.attrs?.text?.text || 'true');
    edgeMap.get(source)!.push({ condition, target });
  });

  const nodeList = nodes.map((node) => {
    const pos = node.getPosition();
    const data = (node.getData() || {}) as NodeData;
    const def = NODE_TYPE_MAP.get(data.nodeType);
    const result: any = {
      id: node.id,
      type: data.nodeType,
      name: data.name || def?.label || '',
      x: pos.x,
      y: pos.y,
    };

    if (data.assignment) {
      result.assignment = data.assignment;
    }
    if (data.strategy) {
      result.strategy = data.strategy;
    }

    const next = edgeMap.get(node.id);
    if (next?.length) {
      result.next = next;
    }

    return result;
  });

  return JSON.stringify(
    {
      version: '1.0.0',
      flow: { nodes: nodeList },
    },
    null,
    2,
  );
}

function emitChange() {
  if (loadingFromModel || props.readonly) return;
  const json = toFlowJson();
  lastLoadedModel = json;
  emit('change', json);
}

// ── 属性面板操作 ──
function applyNodeProps() {
  if (props.readonly) return;
  if (!selectedNode.value || !graph.value) return;
  const cells = graph.value.getSelectedCells();
  const node = cells.find((c) => c.isNode()) as Node | undefined;
  if (!node) return;

  const data: NodeData = {
    nodeType: selectedNode.value.nodeType,
    name: editName.value || selectedNode.value.nodeType,
    assignment: undefined,
  };

  // 有人参与的节点需要分配
  if (selectedNode.value.nodeType === 'APPROVAL' || selectedNode.value.nodeType === 'COUNTERSIGN') {
    data.assignment = buildParticipantAssignment(
      editAssignmentType.value,
      editAssignmentValue.value,
      editDimensionCode.value,
    );
    if (data.nodeType === 'COUNTERSIGN') {
      data.strategy = editStrategy.value;
    }
  }

  node.setData(data);
  selectedNode.value = data;
  emitChange();
}

function applyEdgeProps() {
  if (props.readonly) return;
  if (!selectedEdge.value || !graph.value) return;
  const cells = graph.value.getSelectedCells();
  const edge = cells.find((c) => c.isEdge()) as Edge | undefined;
  if (!edge) return;

  selectedEdge.value.condition = editConditionExpr.value || 'true';
  edge.setLabels([{ attrs: { text: { text: selectedEdge.value.condition } }, position: { distance: 0.5 } }]);
  emitChange();
}

// ── 向外暴露方法 ──
function doImport(jsonStr: string) {
  loadFromJson(jsonStr);
}

function doExport(): string {
  return toFlowJson();
}

defineExpose({ doImport, doExport });

// ── 生命周期 ──
onMounted(() => {
  registerNodes();
  createGraph();
});

watch(
  () => props.modelValue,
  (json) => {
    if (json && json !== lastLoadedModel && graph.value) loadFromJson(json);
  },
);

onUnmounted(() => {
  graph.value?.dispose();
});
</script>

<template>
  <div class="flow-designer">
    <!-- 工具栏 -->
    <div class="designer-toolbar">
      <div class="toolbar-left">
        <h3 class="m-0 text-sm font-semibold">流程设计器</h3>
      </div>
      <div class="toolbar-right">
        <Space :size="6">
          <Button v-if="!readonly" size="small" @click="paletteOpen = !paletteOpen">
            {{ paletteOpen ? '隐藏节点库' : '显示节点库' }}
          </Button>
          <Tooltip title="缩小画布">
            <Button size="small" :disabled="zoomPercent <= MIN_ZOOM * 100" @click="changeZoom(-ZOOM_STEP)">−</Button>
          </Tooltip>
          <Tooltip title="恢复 100%">
            <Button class="zoom-value" size="small" @click="resetZoom">{{ zoomPercent }}%</Button>
          </Tooltip>
          <Tooltip title="放大画布">
            <Button size="small" :disabled="zoomPercent >= MAX_ZOOM * 100" @click="changeZoom(ZOOM_STEP)">＋</Button>
          </Tooltip>
          <Tooltip title="适应画布">
            <Button size="small" @click="fitCanvas">适应</Button>
          </Tooltip>
          <Tooltip v-if="!readonly" title="撤销 (Ctrl+Z)">
            <Button size="small" :disabled="!graph?.canUndo()" @click="graph?.undo()">↩ 撤销</Button>
          </Tooltip>
          <Tooltip v-if="!readonly" title="重做 (Ctrl+Shift+Z)">
            <Button size="small" :disabled="!graph?.canRedo()" @click="graph?.redo()">↪ 重做</Button>
          </Tooltip>
          <Tooltip title="居中显示">
            <Button size="small" @click="graph?.centerContent()">居中</Button>
          </Tooltip>
        </Space>
      </div>
    </div>

    <!-- 主体区域 -->
    <div class="designer-body">
      <!-- 左侧节点面板 -->
      <div v-if="!readonly && paletteOpen" class="node-palette">
        <div class="palette-title">节点类型</div>
        <div class="palette-list">
          <div
            v-for="nt in NODE_TYPES"
            :key="nt.type"
            class="palette-item"
            :style="{ borderColor: nt.color, background: nt.bg, color: nt.color }"
            draggable="true"
            @dragstart="startDrag(nt.type, $event)"
          >
            <span class="palette-icon">{{ nt.icon }}</span>
            <span>{{ nt.label }}</span>
          </div>
        </div>
      </div>

      <!-- 中间画布 -->
      <div class="canvas-wrapper">
        <div ref="containerRef" class="canvas-container"></div>
        <!-- 小地图 -->
        <div class="minimap-box"></div>
      </div>

      <!-- 右侧属性面板 -->
      <div v-if="showPropertyPanel && !readonly" class="property-panel">
        <div class="panel-title">属性配置</div>

        <!-- 节点属性 -->
        <template v-if="selectedNode">
          <div class="prop-group">
            <label class="prop-label">节点名称</label>
            <input v-model="editName" class="prop-input" @blur="applyNodeProps" @keyup.enter="applyNodeProps" />
          </div>

          <template v-if="selectedNode.nodeType === 'APPROVAL' || selectedNode.nodeType === 'COUNTERSIGN'">
            <div class="prop-group">
              <label class="prop-label">参与者类型</label>
              <select v-model="editAssignmentType" class="prop-select" @change="applyNodeProps">
                <option value="ROLE">角色</option>
                <option value="DEPT">部门</option>
                <option value="USER">指定用户</option>
              </select>
            </div>
            <div class="prop-group">
              <label class="prop-label">{{ editAssignmentType === 'ROLE' ? '角色编码' : editAssignmentType === 'DEPT' ? '部门编码' : '用户ID' }}</label>
              <input v-model="editAssignmentValue" class="prop-input" :placeholder="editAssignmentType === 'ROLE' ? '如 DEPT_HEAD' : editAssignmentType === 'DEPT' ? '如 FINANCE' : '用户 ID'" @blur="applyNodeProps" />
            </div>
            <div v-if="editAssignmentType === 'DEPT'" class="prop-group">
              <label class="prop-label">组织维度编码</label>
              <input v-model="editDimensionCode" class="prop-input" placeholder="如 ADMIN" @blur="applyNodeProps" />
            </div>
          </template>

          <template v-if="selectedNode.nodeType === 'COUNTERSIGN'">
            <div class="prop-group">
              <label class="prop-label">会签策略</label>
              <select v-model="editStrategy" class="prop-select" @change="applyNodeProps">
                <option value="ALL">全部通过（会签）</option>
                <option value="ANY">任一通过（或签）</option>
              </select>
            </div>
          </template>
        </template>

        <!-- 连线属性 -->
        <template v-if="selectedEdge">
          <div class="prop-group">
            <label class="prop-label">条件表达式</label>
            <input v-model="editConditionExpr" class="prop-input" placeholder="如 amount > 5000，true 为默认" @blur="applyEdgeProps" @keyup.enter="applyEdgeProps" />
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.flow-designer {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.designer-toolbar {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: #fafafa;
  border-bottom: 1px solid #e5e7eb;
}

.designer-body {
  display: flex;
  flex: 1;
  min-height: 0;
}

.zoom-value {
  min-width: 58px;
  font-variant-numeric: tabular-nums;
}

/* 左侧节点面板 */
.node-palette {
  display: flex;
  flex-shrink: 0;
  flex-direction: column;
  gap: 6px;
  width: 130px;
  padding: 10px;
  overflow-y: auto;
  background: #fafafa;
  border-right: 1px solid #e5e7eb;
}

.palette-title {
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.palette-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.palette-item {
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 6px 8px;
  font-size: 12px;
  cursor: grab;
  user-select: none;
  border: 1px solid;
  border-radius: 4px;
  transition: box-shadow 0.1s;
}

.palette-item:hover {
  box-shadow: 0 1px 4px rgb(0 0 0 / 10%);
}

.palette-icon {
  width: 18px;
  font-size: 14px;
  text-align: center;
}

/* 画布 */
.canvas-wrapper {
  position: relative;
  flex: 1;
  min-width: 0;
}

.canvas-container {
  width: 100%;
  height: 100%;
}

.minimap-box {
  position: absolute;
  right: 12px;
  bottom: 12px;
  width: 200px;
  height: 150px;
  pointer-events: none;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
}

/* 右侧属性面板 */
.property-panel {
  display: flex;
  flex-shrink: 0;
  flex-direction: column;
  gap: 12px;
  width: 240px;
  padding: 12px;
  overflow-y: auto;
  background: #fafafa;
  border-left: 1px solid #e5e7eb;
}

.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: #333;
}

.prop-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.prop-label {
  font-size: 12px;
  font-weight: 500;
  color: #666;
}

.prop-input {
  padding: 6px 8px;
  font-size: 12px;
  outline: none;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  transition: border-color 0.15s;
}

.prop-input:focus {
  border-color: #1677ff;
  box-shadow: 0 0 0 2px rgb(22 119 255 / 8%);
}

.prop-select {
  padding: 6px 8px;
  font-size: 12px;
  outline: none;
  background: #fff;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
}
</style>

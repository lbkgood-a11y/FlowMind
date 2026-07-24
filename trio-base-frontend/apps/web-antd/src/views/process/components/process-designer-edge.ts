import { Shape } from '@antv/x6';

export function createProcessDesignerEdge() {
  return new Shape.Edge({
    attrs: {
      line: {
        stroke: '#8c8c8c',
        strokeWidth: 2,
        targetMarker: { name: 'classic' },
      },
    },
    data: { condition: 'true' },
    labels: [],
    zIndex: 0,
  });
}

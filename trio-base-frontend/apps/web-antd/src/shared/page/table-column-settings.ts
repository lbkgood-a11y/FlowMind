export type TableColumnSetting = {
  fixed?: 'left' | 'right';
  key: string;
  required?: boolean;
  title: string;
  visible: boolean;
  width?: number;
};

export function restoreTableColumnSettings(
  storageKey: string,
  defaults: TableColumnSetting[],
) {
  const fallback = defaults.map((item) => ({ ...item }));
  try {
    if (typeof localStorage === 'undefined') return fallback;
    const saved = JSON.parse(localStorage.getItem(storageKey) || '[]') as TableColumnSetting[];
    const savedMap = new Map(saved.map((item) => [item.key, item]));
    const merged = defaults.map((item) => {
      const restored = { ...item, ...savedMap.get(item.key), key: item.key };
      if (item.required) {
        restored.required = true;
        restored.visible = true;
      }
      return restored;
    });
    const order = new Map(saved.map((item, index) => [item.key, index]));
    return merged.sort(
      (left, right) =>
        (order.get(left.key) ?? Number.MAX_SAFE_INTEGER) -
        (order.get(right.key) ?? Number.MAX_SAFE_INTEGER),
    );
  } catch {
    return fallback;
  }
}

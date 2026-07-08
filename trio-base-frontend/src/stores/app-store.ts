import { create } from "zustand";

/**
 * 全局应用状态 — 铁律 11：LUI 到 GUI 必须走 Zustand Store → 驱动视图重渲染。
 * AI 侧禁止直接操作 DOM 修改 GUI 值。
 */
interface AppState {
  /** 当前交互模式：gui | lui */
  mode: "gui" | "lui";
  setMode: (mode: "gui" | "lui") => void;

  /** LUI 最近触发的动作（如 Tool Call 生成的表单数据） */
  pendingAction: unknown | null;
  setPendingAction: (action: unknown | null) => void;
}

export const useAppStore = create<AppState>((set) => ({
  mode: "gui",
  setMode: (mode) => set({ mode }),
  pendingAction: null,
  setPendingAction: (action) => set({ pendingAction: action }),
}));

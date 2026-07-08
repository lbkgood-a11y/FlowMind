import { createElement, type ComponentType, type ReactElement } from "react";
import { z } from "zod";

/**
 * Generative UI 组件注册表 — 铁律 10：动态生成的 B 端业务组件必须在此注册，
 * 参数传递必须通过 Zod Schema 校验，防止 Prompt Injection 引发 XSS。
 */
type RegisteredComponent = {
  component: ComponentType<unknown>;
  schema: z.ZodType<unknown>;
};

const registry = new Map<string, RegisteredComponent>();

export function registerComponent(
  name: string,
  component: ComponentType<unknown>,
  schema: z.ZodType<unknown>
) {
  registry.set(name, { component, schema });
}

export function resolveComponent(
  name: string,
  props: unknown
): ReactElement | null {
  const entry = registry.get(name);
  if (!entry) {
    console.warn(`Component "${name}" not found in registry. Blocked per 铁律 10.`);
    return null;
  }
  const parsed = entry.schema.safeParse(props);
  if (!parsed.success) {
    console.warn(`Component "${name}" props failed Zod validation. Blocked per 铁律 10.`);
    return null;
  }
  const Comp = entry.component;
  return createElement(Comp, parsed.data as Record<string, unknown>);
}

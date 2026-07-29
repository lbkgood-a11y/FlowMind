/**
 * Global authority directive
 * Used for fine-grained control of component permissions
 * @Example v-access:role="[ROLE_NAME]" or v-access:role="ROLE_NAME"
 * @Example v-access:code="[ROLE_CODE]" or v-access:code="ROLE_CODE"
 */
import type { App, Directive, DirectiveBinding } from 'vue';

import { useAccess } from './use-access';

function checkAccess(
  binding: DirectiveBinding<string | string[]>,
): boolean {
  const { accessMode, hasAccessByCodes, hasAccessByRoles } = useAccess();

  const value = binding.value;
  if (!value) return true;

  const authMethod =
    accessMode.value === 'frontend' && binding.arg === 'role'
      ? hasAccessByRoles
      : hasAccessByCodes;

  const values = Array.isArray(value) ? value : [value];
  return authMethod(values);
}

function removeElement(el: Element) {
  if (el.parentNode) {
    el.parentNode.removeChild(el);
  }
}

const mounted = (el: Element, binding: DirectiveBinding<string | string[]>) => {
  if (!checkAccess(binding)) {
    removeElement(el);
  }
};

const updated = (el: Element, binding: DirectiveBinding<string | string[]>) => {
  if (!checkAccess(binding)) {
    removeElement(el);
  }
};

const authDirective: Directive = {
  mounted,
  updated,
};

export function registerAccessDirective(app: App) {
  app.directive('access', authDirective);
}

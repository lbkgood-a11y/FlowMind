"use client";

import { mergeProps } from "@base-ui/react/merge-props";
import { useRender } from "@base-ui/react/use-render";
import { Check } from "lucide-react";

import { cn } from "@/lib/utils";

type CheckedState = boolean | "indeterminate";

interface CheckboxProps {
  checked?: CheckedState;
  onCheckedChange?: (checked: CheckedState) => void;
  disabled?: boolean;
  required?: boolean;
  name?: string;
  value?: string;
  "aria-label"?: string;
  className?: string;
}

function Checkbox({
  checked = false,
  onCheckedChange,
  className,
  ...props
}: CheckboxProps) {
  return (
    <button
      type="button"
      role="checkbox"
      aria-checked={checked === "indeterminate" ? "mixed" : checked}
      data-state={
        checked === "indeterminate"
          ? "indeterminate"
          : checked
            ? "checked"
            : "unchecked"
      }
      className={cn(
        "peer size-4 shrink-0 rounded-sm border border-input shadow-sm outline-none",
        "focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50",
        "disabled:cursor-not-allowed disabled:opacity-50",
        "data-[state=checked]:bg-primary data-[state=checked]:text-primary-foreground data-[state=checked]:border-primary",
        "data-[state=indeterminate]:bg-primary data-[state=indeterminate]:text-primary-foreground data-[state=indeterminate]:border-primary",
        "transition-colors",
        className,
      )}
      onClick={() => onCheckedChange?.(checked === true ? false : true)}
      disabled={props.disabled}
      {...props}
    >
      {checked === true && (
        <Check className="size-3.5 mx-auto block" strokeWidth={3} />
      )}
      {checked === "indeterminate" && (
        <span className="flex items-center justify-center">
          <span className="size-2 rounded-[1px] bg-current" />
        </span>
      )}
    </button>
  );
}

export { Checkbox, type CheckedState };

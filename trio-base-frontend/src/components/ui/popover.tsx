"use client";

import * as React from "react";
import { cn } from "@/lib/utils";

const PopoverContext = React.createContext<{
  open: boolean;
  setOpen: React.Dispatch<React.SetStateAction<boolean>>;
} | null>(null);

function usePopover() {
  const ctx = React.useContext(PopoverContext);
  if (!ctx) throw new Error("Popover components must be used within <Popover>");
  return ctx;
}

function Popover({ children, open: controlledOpen, onOpenChange }: {
  children: React.ReactNode;
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
}) {
  const [uncontrolledOpen, setUncontrolledOpen] = React.useState(false);
  const isControlled = controlledOpen !== undefined;
  const open = isControlled ? controlledOpen : uncontrolledOpen;

  const setOpen = React.useCallback(
    (next: boolean | ((prev: boolean) => boolean)) => {
      const value = typeof next === "function" ? next(open) : next;
      if (!isControlled) setUncontrolledOpen(value);
      onOpenChange?.(value);
    },
    [isControlled, open, onOpenChange],
  );

  return (
    <PopoverContext value={{ open, setOpen }}>
      {children}
    </PopoverContext>
  );
}

const PopoverTrigger = React.forwardRef<
  HTMLButtonElement,
  React.ComponentProps<"button"> & { asChild?: boolean; disabled?: boolean }
>(({ className, asChild, disabled, children, ...props }, ref) => {
  const { open, setOpen } = usePopover();
  return (
    <button
      ref={ref}
      type="button"
      aria-haspopup="dialog"
      aria-expanded={open}
      data-state={open ? "open" : "closed"}
      disabled={disabled}
      className={cn(
        disabled && "pointer-events-none opacity-50",
        className,
      )}
      onClick={() => setOpen(!open)}
      {...props}
    >
      {children}
    </button>
  );
});
PopoverTrigger.displayName = "PopoverTrigger";

function PopoverContent({
  className,
  align = "center",
  sideOffset = 4,
  children,
  ...props
}: React.ComponentProps<"div"> & {
  align?: "start" | "center" | "end";
  sideOffset?: number;
}) {
  const { open } = usePopover();
  if (!open) return null;

  return (
    <div
      data-side="bottom"
      data-align={align}
      className={cn(
        "z-50 w-72 rounded-md border bg-popover p-4 text-popover-foreground shadow-md outline-none",
        "data-[state=open]:animate-in data-[state=closed]:animate-out",
        "data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0",
        "data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95",
        className,
      )}
      {...props}
    >
      {children}
    </div>
  );
}

export { Popover, PopoverTrigger, PopoverContent };

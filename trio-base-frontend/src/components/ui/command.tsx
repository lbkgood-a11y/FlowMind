"use client";

import * as React from "react";
import { cn } from "@/lib/utils";

const CommandContext = React.createContext<{
  value: string;
  onValueChange: (value: string) => void;
} | null>(null);

function Command({ className, ...props }: React.ComponentProps<"div">) {
  const [value, setValue] = React.useState("");

  return (
    <CommandContext value={{ value, onValueChange: setValue }}>
      <div
        className={cn(
          "flex h-full w-full flex-col overflow-hidden rounded-md bg-popover text-popover-foreground",
          className,
        )}
        {...props}
      />
    </CommandContext>
  );
}

function CommandInput({
  className,
  ...props
}: React.ComponentProps<"input">) {
  return (
    <div
      className="flex items-center border-b px-3"
      cmdk-input-wrapper=""
    >
      <input
        className={cn(
          "flex h-10 w-full rounded-md bg-transparent py-3 text-sm outline-none placeholder:text-muted-foreground disabled:cursor-not-allowed disabled:opacity-50",
          className,
        )}
        {...props}
      />
    </div>
  );
}

function CommandList({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      className={cn("max-h-[300px] overflow-y-auto overflow-x-hidden", className)}
      {...props}
    />
  );
}

function CommandEmpty({
  className,
  ...props
}: React.ComponentProps<"div">) {
  return (
    <div
      className={cn("py-6 text-center text-sm text-muted-foreground", className)}
      {...props}
    />
  );
}

function CommandGroup({
  className,
  ...props
}: React.ComponentProps<"div">) {
  return (
    <div
      className={cn(
        "overflow-hidden p-1 text-foreground [&_[cmdk-group-heading]]:px-2 [&_[cmdk-group-heading]]:py-1.5 [&_[cmdk-group-heading]]:text-xs [&_[cmdk-group-heading]]:font-medium [&_[cmdk-group-heading]]:text-muted-foreground",
        className,
      )}
      {...props}
    />
  );
}

function CommandItem({
  className,
  onSelect,
  value,
  children,
  ...props
}: React.ComponentProps<"div"> & {
  onSelect?: (value: string) => void;
  value?: string;
}) {
  return (
    <div
      role="option"
      aria-selected={false}
      data-selected="false"
      className={cn(
        "relative flex cursor-default items-center gap-2 rounded-sm px-2 py-1.5 text-sm outline-none select-none",
        "data-[disabled=true]:pointer-events-none data-[disabled=true]:opacity-50",
        "hover:bg-accent hover:text-accent-foreground",
        "aria-selected:bg-accent aria-selected:text-accent-foreground",
        className,
      )}
      onClick={() => value && onSelect?.(value)}
      {...props}
    >
      {children}
    </div>
  );
}

export { Command, CommandInput, CommandList, CommandEmpty, CommandGroup, CommandItem };

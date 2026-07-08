"use client";

import { cn } from "@/lib/utils";

interface PageHeaderProps {
  breadcrumb?: string;
  title: string;
  subtitle?: string;
  actions?: React.ReactNode;
  className?: string;
}

export function PageHeader({
  breadcrumb,
  title,
  subtitle,
  actions,
  className,
}: PageHeaderProps) {
  return (
    <div className={cn("mb-6 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between", className)}>
      <div className="min-w-0">
        {breadcrumb && (
          <p className="text-xs font-medium uppercase tracking-[0.2em] text-muted-foreground">
            {breadcrumb}
          </p>
        )}
        <h1 className={cn("text-2xl font-bold tracking-tight", breadcrumb && "mt-2")}>
          {title}
        </h1>
        {subtitle && (
          <p className="mt-1 max-w-3xl text-sm text-muted-foreground">{subtitle}</p>
        )}
      </div>
      {actions && (
        <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div>
      )}
    </div>
  );
}

"use client";

interface PageHeaderProps {
  breadcrumb?: string;
  title: string;
  subtitle?: string;
  actions?: React.ReactNode;
}

export function PageHeader({
  breadcrumb,
  title,
  subtitle,
  actions,
}: PageHeaderProps) {
  return (
    <div className="flex items-start justify-between gap-4">
      <div className="min-w-0">
        {breadcrumb && (
          <p className="text-xs uppercase tracking-[0.24em] text-fg-tertiary">
            {breadcrumb}
          </p>
        )}
        <h1
          className={`text-base font-medium text-fg-primary ${breadcrumb ? "mt-1" : ""}`}
        >
          {title}
        </h1>
        {subtitle && (
          <p className="mt-1 text-sm text-fg-tertiary">{subtitle}</p>
        )}
      </div>
      {actions && (
        <div className="flex shrink-0 items-center gap-3">{actions}</div>
      )}
    </div>
  );
}

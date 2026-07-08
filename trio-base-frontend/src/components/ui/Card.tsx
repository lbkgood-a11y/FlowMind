"use client";

interface CardProps {
  children: React.ReactNode;
  className?: string;
  title?: string;
  subtitle?: string;
  actions?: React.ReactNode;
  noPadding?: boolean;
}

export function Card({
  children,
  className = "",
  title,
  subtitle,
  actions,
  noPadding,
}: CardProps) {
  return (
    <section className={`rounded border border-border bg-white ${className}`}>
      {(title || actions) && (
        <div className="flex items-center justify-between border-b border-border px-4 py-3">
          <div className="min-w-0">
            {title && <h3 className="text-base font-medium text-fg-primary">{title}</h3>}
            {subtitle && <p className="mt-0.5 text-sm text-fg-tertiary">{subtitle}</p>}
          </div>
          {actions && (
            <div className="flex shrink-0 items-center gap-2">{actions}</div>
          )}
        </div>
      )}
      <div className={noPadding ? "" : "p-4"}>{children}</div>
    </section>
  );
}

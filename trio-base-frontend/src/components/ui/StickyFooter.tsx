"use client";

interface StickyFooterProps {
  children: React.ReactNode;
  className?: string;
}

export function StickyFooter({
  children,
  className = "",
}: StickyFooterProps) {
  return (
    <div
      className={`sticky bottom-0 z-10 border-t border-border bg-white px-4 py-3 ${className}`}
    >
      <div className="flex items-center justify-end gap-3">{children}</div>
    </div>
  );
}

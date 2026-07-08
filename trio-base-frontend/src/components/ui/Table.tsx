"use client";

/* ── Table wrapper ────────────────────────────── */

interface TableProps {
  children: React.ReactNode;
  className?: string;
}

export function Table({ children, className = "" }: TableProps) {
  return (
    <div className="overflow-x-auto">
      <table className={`w-full text-left text-sm ${className}`}>
        {children}
      </table>
    </div>
  );
}

/* ── Head parts ───────────────────────────────── */

export function THead({ children }: { children: React.ReactNode }) {
  return (
    <thead className="bg-muted/50 text-xs text-muted-foreground">
      {children}
    </thead>
  );
}

export function Th({
  children,
  className = "",
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <th className={`whitespace-nowrap px-4 py-3 font-medium ${className}`}>
      {children}
    </th>
  );
}

/* ── Body parts ───────────────────────────────── */

export function Tr({
  children,
  className = "",
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <tr
      className={`border-t border-border transition-colors even:bg-muted/20 hover:bg-accent/40 ${className}`}
    >
      {children}
    </tr>
  );
}

export function Td({
  children,
  className = "",
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <td className={`px-4 py-3 align-top ${className}`}>{children}</td>
  );
}

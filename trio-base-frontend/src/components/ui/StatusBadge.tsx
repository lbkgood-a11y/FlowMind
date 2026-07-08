"use client";

type StatusType = "success" | "processing" | "warning" | "danger";

const STATUS_MAP: Record<StatusType, { bg: string; text: string }> = {
  success:    { bg: "bg-success-bg", text: "text-success-fg" },
  processing: { bg: "bg-processing-bg", text: "text-processing-fg" },
  warning:    { bg: "bg-warning-bg", text: "text-warning-fg" },
  danger:     { bg: "bg-danger-bg", text: "text-danger-fg" },
};

interface StatusBadgeProps {
  status: StatusType;
  label?: string;
}

export function StatusBadge({ status, label }: StatusBadgeProps) {
  const s = STATUS_MAP[status] ?? STATUS_MAP.processing;
  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${s.bg} ${s.text}`}
    >
      {label ?? status}
    </span>
  );
}

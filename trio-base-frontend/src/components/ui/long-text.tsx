import { cn } from "@/lib/utils";

type LongTextProps = React.ComponentProps<"div"> & {
  children: React.ReactNode;
};

export function LongText({ className, children, ...props }: LongTextProps) {
  return (
    <div
      className={cn(
        "max-w-[200px] truncate",
        className,
      )}
      title={typeof children === "string" ? children : undefined}
      {...props}
    >
      {children}
    </div>
  );
}

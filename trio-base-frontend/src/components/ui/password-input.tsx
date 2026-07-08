"use client";

import { forwardRef, useState } from "react";
import { Eye, EyeOff } from "lucide-react";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

type PasswordInputProps = React.ComponentProps<typeof Input>;

const PasswordInput = forwardRef<HTMLInputElement, PasswordInputProps>(
  ({ className, ...props }, ref) => {
    const [show, setShow] = useState(false);
    return (
      <div className="relative">
        <Input
          ref={ref}
          type={show ? "text" : "password"}
          className={cn("pe-9", className)}
          {...props}
        />
        <button
          type="button"
          className="absolute right-0 top-0 flex h-full w-9 items-center justify-center text-muted-foreground hover:text-foreground"
          onClick={() => setShow(!show)}
          tabIndex={-1}
        >
          {show ? (
            <EyeOff className="size-4" />
          ) : (
            <Eye className="size-4" />
          )}
          <span className="sr-only">
            {show ? "Hide password" : "Show password"}
          </span>
        </button>
      </div>
    );
  },
);
PasswordInput.displayName = "PasswordInput";

export { PasswordInput };

"use client";

import type { ReactNode } from "react";
import { AlertTriangle } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

type ConfirmDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: ReactNode;
  desc: ReactNode;
  confirmText?: string;
  cancelText?: string;
  destructive?: boolean;
  disabled?: boolean;
  form?: string;
  onConfirm?: () => void;
};

export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  desc,
  confirmText = "Confirm",
  cancelText = "Cancel",
  destructive = false,
  disabled = false,
  form,
  onConfirm,
}: ConfirmDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          {typeof desc === "string" ? (
            <DialogDescription>{desc}</DialogDescription>
          ) : (
            <div className="text-sm text-muted-foreground">{desc}</div>
          )}
        </DialogHeader>
        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => onOpenChange(false)}
          >
            {cancelText}
          </Button>
          <Button
            variant={destructive ? "destructive" : "default"}
            disabled={disabled}
            type={form ? "submit" : "button"}
            form={form}
            onClick={form ? undefined : onConfirm}
          >
            {confirmText}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

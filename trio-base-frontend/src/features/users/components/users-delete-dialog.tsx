"use client";

import { useState } from "react";
import { AlertTriangle } from "lucide-react";
import { useI18n } from "@/lib/i18n";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { type User } from "../data/schema";

type UsersDeleteDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  currentRow: User;
  onSuccess?: () => void;
};

export function UsersDeleteDialog({
  open,
  onOpenChange,
  currentRow,
  onSuccess,
}: UsersDeleteDialogProps) {
  const [value, setValue] = useState("");
  const { messages } = useI18n();
  const m = messages.pages.users;

  const handleDelete = () => {
    if (value.trim() !== currentRow.username) return;
    onOpenChange(false);
    setValue("");
    onSuccess?.();
  };

  return (
    <ConfirmDialog
      open={open}
      onOpenChange={(state) => {
        if (!state) setValue("");
        onOpenChange(state);
      }}
      form="users-delete-form"
      disabled={value.trim() !== currentRow.username}
      title={
        <span className="text-destructive">
          <AlertTriangle
            className="me-1 inline-block stroke-destructive"
            size={18}
          />{" "}
          {m.deleteUserTitle}
        </span>
      }
      desc={
        <form
          id="users-delete-form"
          onSubmit={(e) => {
            e.preventDefault();
            handleDelete();
          }}
          className="space-y-4"
        >
          <p
            className="mb-2"
            dangerouslySetInnerHTML={{
              __html: m.deleteUserConfirm.replace("{username}", currentRow.username),
            }}
          />

          <Label className="my-2">
            Username:
            <Input
              value={value}
              onChange={(e) => setValue(e.target.value)}
              placeholder={m.deleteUserPlaceholder}
              autoFocus
            />
          </Label>

          <Alert variant="destructive">
            <AlertTitle>{m.deleteWarning}</AlertTitle>
            <AlertDescription>
              {m.deleteCannotUndo}
            </AlertDescription>
          </Alert>
        </form>
      }
      confirmText={m.actionsDelete}
      destructive
    />
  );
}

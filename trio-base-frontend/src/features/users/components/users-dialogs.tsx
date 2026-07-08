"use client";

import { toast } from "sonner";
import { useI18n } from "@/lib/i18n";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { adminApi } from "@/lib/admin";
import { UsersActionDialog } from "./users-action-dialog";
import { UsersDeleteDialog } from "./users-delete-dialog";
import { UsersInviteDialog } from "./users-invite-dialog";
import { UsersViewDialog } from "./users-view-dialog";
import { useUsers } from "./users-provider";

type UsersDialogsProps = {
  onSuccess?: () => void;
};

export function UsersDialogs({ onSuccess }: UsersDialogsProps) {
  const { open, setOpen, currentRow, setCurrentRow } = useUsers();
  const { messages } = useI18n();
  const m = messages.pages.users;

  async function handleStatusChange(userId: string, newStatus: number) {
    try {
      await adminApi.updateUserStatus(userId, newStatus);
      toast.success(newStatus === 1 ? m.userEnabled : m.userDisabled);
      onSuccess?.();
    } catch {
      toast.error("Failed to update user status");
    }
  }

  return (
    <>
      <UsersActionDialog
        key="user-add"
        open={open === "add"}
        onOpenChange={(state) => setOpen(state ? "add" : null)}
        onSuccess={onSuccess}
      />

      <UsersInviteDialog
        key="user-invite"
        open={open === "invite"}
        onOpenChange={(state) => setOpen(state ? "invite" : null)}
      />

      {currentRow && (
        <>
          <UsersViewDialog
            key={`user-view-${currentRow.id}`}
            open={open === "view"}
            onOpenChange={() => {
              setOpen("view");
              setTimeout(() => setCurrentRow(null), 500);
            }}
            currentRow={currentRow}
          />

          <UsersActionDialog
            key={`user-edit-${currentRow.id}`}
            open={open === "edit"}
            onOpenChange={() => {
              setOpen("edit");
              setTimeout(() => setCurrentRow(null), 500);
            }}
            currentRow={currentRow}
            onSuccess={onSuccess}
          />

          <ConfirmDialog
            key={`user-enable-${currentRow.id}`}
            open={open === "enable"}
            onOpenChange={() => {
              setOpen("enable");
              setTimeout(() => setCurrentRow(null), 500);
            }}
            title={m.actionsEnable}
            desc={<span dangerouslySetInnerHTML={{ __html: m.confirmEnable.replace("{username}", currentRow.username) }} />}
            confirmText={m.actionsEnable}
            onConfirm={() => handleStatusChange(currentRow.id, 1)}
          />

          <ConfirmDialog
            key={`user-disable-${currentRow.id}`}
            open={open === "disable"}
            onOpenChange={() => {
              setOpen("disable");
              setTimeout(() => setCurrentRow(null), 500);
            }}
            title={m.actionsDisable}
            desc={<span dangerouslySetInnerHTML={{ __html: m.confirmDisable.replace("{username}", currentRow.username) }} />}
            confirmText={m.actionsDisable}
            destructive
            onConfirm={() => handleStatusChange(currentRow.id, 0)}
          />

          <UsersDeleteDialog
            key={`user-delete-${currentRow.id}`}
            open={open === "delete"}
            onOpenChange={() => {
              setOpen("delete");
              setTimeout(() => setCurrentRow(null), 500);
            }}
            currentRow={currentRow}
            onSuccess={onSuccess}
          />
        </>
      )}
    </>
  );
}

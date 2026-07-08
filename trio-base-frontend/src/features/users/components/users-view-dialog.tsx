"use client";

import { useI18n } from "@/lib/i18n";
import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { callTypes, roleIcons, roleLabels } from "../data/data";
import { type User } from "../data/schema";

type UsersViewDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  currentRow: User;
};

export function UsersViewDialog({
  open,
  onOpenChange,
  currentRow,
}: UsersViewDialogProps) {
  const { messages } = useI18n();
  const m = messages.pages.users;
  const statusKey = currentRow.status === 1 ? "active" : "inactive";
  const badgeColor = callTypes.get(statusKey);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{currentRow.username}</DialogTitle>
          <DialogDescription>{m.userDetails}</DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <div className="flex items-center gap-4">
            <div className="flex size-14 items-center justify-center rounded-full bg-muted text-lg font-medium">
              {currentRow.username.slice(0, 2).toUpperCase()}
            </div>
            <div>
              <p className="text-lg font-medium">{currentRow.username}</p>
              <p className="text-sm text-muted-foreground">{currentRow.id}</p>
            </div>
          </div>
          <dl className="grid grid-cols-3 gap-2 text-sm">
            <dt className="text-muted-foreground">{messages.common.email}</dt>
            <dd className="col-span-2">{currentRow.email || "-"}</dd>
            <dt className="text-muted-foreground">{messages.common.phone}</dt>
            <dd className="col-span-2">{currentRow.phone || "-"}</dd>
            <dt className="text-muted-foreground">{messages.common.status}</dt>
            <dd className="col-span-2">
              <Badge variant="outline" className={badgeColor}>
                {statusKey}
              </Badge>
            </dd>
            <dt className="text-muted-foreground">{messages.common.roles}</dt>
            <dd className="col-span-2 flex flex-wrap gap-1">
              {currentRow.roles?.length ? (
                currentRow.roles.map((role) => {
                  const Icon = roleIcons[role];
                  return (
                    <Badge key={role} variant="secondary" className="gap-1">
                      {Icon && <Icon size={12} />}
                      {roleLabels[role] || role}
                    </Badge>
                  );
                })
              ) : (
                "-"
              )}
            </dd>
            <dt className="text-muted-foreground">{messages.pages.users.columns.createdAt}</dt>
            <dd className="col-span-2">
              {currentRow.createdAt
                ? new Date(currentRow.createdAt).toLocaleString("zh-CN")
                : "-"}
            </dd>
          </dl>
        </div>
      </DialogContent>
    </Dialog>
  );
}

"use client";

import { MailPlus, UserPlus } from "lucide-react";
import { useI18n } from "@/lib/i18n";
import { Button } from "@/components/ui/button";
import { useUsers } from "./users-provider";

export function UsersPrimaryButtons() {
  const { setOpen } = useUsers();
  const { messages } = useI18n();
  const m = messages.pages.users;

  return (
    <div className="flex gap-2">
      <Button
        variant="outline"
        className="space-x-1"
        onClick={() => setOpen("invite")}
      >
        <span>{m.inviteUser}</span> <MailPlus size={18} />
      </Button>
      <Button className="space-x-1" onClick={() => setOpen("add")}>
        <span>{m.addUser}</span> <UserPlus size={18} />
      </Button>
    </div>
  );
}

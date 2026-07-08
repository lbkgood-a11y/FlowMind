"use client";

import { type Row } from "@tanstack/react-table";
import { Ellipsis, Trash2, UserPen } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuShortcut,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useI18n } from "@/lib/i18n";
import { type User } from "../data/schema";
import { useUsers } from "./users-provider";

type DataTableRowActionsProps = {
  row: Row<User>;
};

export function DataTableRowActions({ row }: DataTableRowActionsProps) {
  const { setOpen, setCurrentRow } = useUsers();
  const { messages } = useI18n();
  const m = messages.pages.users;

  return (
    <>
      <DropdownMenu modal={false}>
        <DropdownMenuTrigger
          render={
            <Button variant="ghost" className="flex h-8 w-8 p-0 data-[state=open]:bg-muted">
              <Ellipsis className="h-4 w-4" />
              <span className="sr-only">Open menu</span>
            </Button>
          }
        />
        <DropdownMenuContent align="end" className="w-40">
          <DropdownMenuItem
            onClick={() => {
              setCurrentRow(row.original);
              setOpen("edit");
            }}
          >
            <UserPen className="me-2 size-4" />
            {m.actionsEdit}
          </DropdownMenuItem>
          {row.original.status === 1 ? (
            <DropdownMenuItem
              onClick={() => {
                setCurrentRow(row.original);
                setOpen("disable");
              }}
            >
              <span className="text-destructive flex items-center">
                <span className="me-2 inline-block size-2 rounded-full bg-destructive" />
                {m.actionsDisable}
              </span>
            </DropdownMenuItem>
          ) : (
            <DropdownMenuItem
              onClick={() => {
                setCurrentRow(row.original);
                setOpen("enable");
              }}
            >
              <span className="flex items-center text-emerald-600">
                <span className="me-2 inline-block size-2 rounded-full bg-emerald-500" />
{m.actionsEnable}
              </span>
            </DropdownMenuItem>
          )}
          <DropdownMenuSeparator />
          <DropdownMenuItem
            onClick={() => {
              setCurrentRow(row.original);
              setOpen("delete");
            }}
            className="text-red-500!"
          >
            <Trash2 className="me-2 size-4" />
{m.actionsDelete}
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </>
  );
}

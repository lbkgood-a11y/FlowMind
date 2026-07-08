"use client";

import { type ColumnDef } from "@tanstack/react-table";
import { cn } from "@/lib/utils";
import { Badge } from "@/components/ui/badge";
import { Checkbox } from "@/components/ui/checkbox";
import { DataTableColumnHeader } from "@/components/data-table/data-table-column-header";
import { LongText } from "@/components/ui/long-text";
import { callTypes, roleIcons, roleLabels } from "../data/data";
import { type User } from "../data/schema";
import { DataTableRowActions } from "./data-table-row-actions";

export function createUsersColumns(usersMessages: Record<string, any>): ColumnDef<User>[] {
  const commonMessages = (usersMessages as any).__common || {};
  const t = (key: string) => usersMessages[key] || key;
  const common = (key: string, fallback: string) => commonMessages[key] || fallback;

  return [
    {
      id: "select",
      header: ({ table }) => (
        <Checkbox
          checked={
            table.getIsAllPageRowsSelected() ||
            (table.getIsSomePageRowsSelected() && "indeterminate")
          }
          onCheckedChange={(value) => table.toggleAllPageRowsSelected(!!value)}
          aria-label="Select all"
          className="translate-y-0.5"
        />
      ),
      meta: { className: cn("w-12") } as Record<string, unknown>,
      cell: ({ row }) => (
        <Checkbox
          checked={row.getIsSelected()}
          onCheckedChange={(value) => row.toggleSelected(!!value)}
          aria-label="Select row"
          className="translate-y-0.5"
        />
      ),
      enableSorting: false,
      enableHiding: false,
    },
    {
      accessorKey: "username",
      header: ({ column }) => (
        <DataTableColumnHeader column={column} title={common("username", "Username")} />
      ),
      cell: ({ row }) => (
        <div className="flex items-center gap-3">
          <div className="flex size-8 items-center justify-center rounded-full bg-muted text-xs font-medium text-muted-foreground">
            {row.getValue<string>("username").slice(0, 2).toUpperCase()}
          </div>
          <LongText className="max-w-36 font-medium">
            {row.getValue("username")}
          </LongText>
        </div>
      ),
      enableHiding: false,
    },
    {
      accessorKey: "email",
      header: ({ column }) => (
        <DataTableColumnHeader column={column} title={common("email", "Email")} />
      ),
      cell: ({ row }) => (
        <div className="text-muted-foreground">
          {row.getValue("email") || "-"}
        </div>
      ),
    },
    {
      accessorKey: "phone",
      header: ({ column }) => (
        <DataTableColumnHeader column={column} title={common("phone", "Phone")} />
      ),
      cell: ({ row }) => (
        <div className="text-muted-foreground">
          {row.getValue("phone") || "-"}
        </div>
      ),
      enableSorting: false,
    },
    {
      accessorKey: "roles",
      header: ({ column }) => (
        <DataTableColumnHeader column={column} title={common("roles", "Roles")} />
      ),
      cell: ({ row }) => {
        const roles = row.getValue<string[]>("roles");
        if (!roles || roles.length === 0) return <span className="text-muted-foreground">-</span>;
        return (
          <div className="flex flex-wrap gap-1">
            {roles.slice(0, 2).map((role) => {
              const Icon = roleIcons[role];
              return (
                <Badge key={role} variant="secondary" className="gap-1 text-xs">
                  {Icon && <Icon size={12} className="text-muted-foreground" />}
                  {roleLabels[role] || role}
                </Badge>
              );
            })}
            {roles.length > 2 && (
              <Badge variant="outline" className="text-xs">
                +{roles.length - 2}
              </Badge>
            )}
          </div>
        );
      },
      filterFn: (row, id, value: string[]) => {
        const roles = row.getValue<string[]>("roles");
        if (!roles || roles.length === 0) return false;
        return value.some((v) => roles.includes(v));
      },
      enableSorting: false,
    },
    {
      accessorKey: "status",
      header: ({ column }) => (
        <DataTableColumnHeader column={column} title={common("status", "Status")} />
      ),
      cell: ({ row }) => {
        const status = row.getValue<number>("status");
        const statusKey = status === 1 ? "active" : "inactive";
        const badgeColor = callTypes.get(statusKey);
        return (
          <Badge variant="outline" className={cn("capitalize", badgeColor)}>
            {statusKey}
          </Badge>
        );
      },
      filterFn: (row, id, value: string[]) => {
        const status = row.getValue<number>("status");
        const statusKey = status === 1 ? "active" : "inactive";
        return value.includes(statusKey);
      },
      enableSorting: false,
      enableHiding: false,
    },
    {
      accessorKey: "createdAt",
      header: ({ column }) => (
        <DataTableColumnHeader column={column} title={common("createdAt", "Created")} />
      ),
      cell: ({ row }) => {
        const date = row.getValue<string | null>("createdAt");
        return (
          <div className="text-xs text-muted-foreground">
            {date ? new Date(date).toLocaleDateString("zh-CN") : "-"}
          </div>
        );
      },
    },
    {
      id: "actions",
      cell: DataTableRowActions,
    },
  ];
}

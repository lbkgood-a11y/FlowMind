"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import {
  Building2,
  FileText,
  KeySquare,
  LayoutDashboard,
  ListTree,
  Search,
  Shield,
  Users,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { useI18n } from "@/lib/i18n";

export function GlobalSearch() {
  const { messages } = useI18n();
  const [query, setQuery] = useState("");
  const searchItems = [
    {
      title: messages.common.dashboard,
      description: messages.dashboard.subtitle,
      href: "/",
      icon: LayoutDashboard,
    },
    {
      title: messages.common.forms,
      description: messages.pages.forms.subtitle,
      href: "/forms",
      icon: FileText,
    },
    {
      title: messages.common.newForm,
      description: messages.pages.formBuilder.title,
      href: "/forms/new",
      icon: FileText,
    },
    {
      title: messages.common.users,
      description: messages.pages.users.subtitle,
      href: "/admin/users",
      icon: Users,
    },
    {
      title: messages.common.roles,
      description: messages.pages.roles.title,
      href: "/admin/roles",
      icon: Shield,
    },
    {
      title: messages.common.orgs,
      description: messages.pages.orgs.subtitle,
      href: "/admin/orgs",
      icon: Building2,
    },
    {
      title: messages.common.menus,
      description: messages.pages.menus.subtitle,
      href: "/admin/menus",
      icon: ListTree,
    },
    {
      title: messages.common.permissions,
      description: messages.pages.permissions.subtitle,
      href: "/admin/permissions",
      icon: KeySquare,
    },
  ];

  const results = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    if (!normalized) {
      return searchItems;
    }

    return searchItems.filter((item) => {
      const haystack = `${item.title} ${item.description} ${item.href}`.toLowerCase();
      return haystack.includes(normalized);
    });
  }, [query]);

  return (
    <Dialog>
      <DialogTrigger
        render={
          <Button
            variant="outline"
            className="h-8 w-44 justify-start gap-2 px-2 text-muted-foreground md:w-64"
          >
            <Search className="size-4" />
            <span className="hidden sm:inline">{messages.topbar.searchButton}</span>
            <kbd className="ml-auto hidden rounded border bg-muted px-1.5 py-0.5 text-[10px] font-medium text-muted-foreground sm:inline-flex">
              ⌘K
            </kbd>
          </Button>
        }
      />
      <DialogContent className="gap-3 p-0 sm:max-w-xl">
        <DialogHeader className="px-4 pt-4">
          <DialogTitle>{messages.topbar.searchTitle}</DialogTitle>
          <DialogDescription>{messages.topbar.searchDescription}</DialogDescription>
        </DialogHeader>
        <div className="border-y px-4 py-3">
          <Input
            autoFocus
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder={messages.topbar.searchPlaceholder}
            className="h-9"
          />
        </div>
        <div className="max-h-80 overflow-y-auto px-2 pb-2">
          {results.length > 0 ? (
            results.map((item) => {
              const Icon = item.icon;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className="flex items-center gap-3 rounded-lg px-2 py-2.5 text-sm outline-none hover:bg-accent hover:text-accent-foreground focus:bg-accent"
                >
                  <span className="flex size-8 items-center justify-center rounded-md bg-muted text-muted-foreground">
                    <Icon className="size-4" />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate font-medium">{item.title}</span>
                    <span className="block truncate text-xs text-muted-foreground">
                      {item.description}
                    </span>
                  </span>
                  <span className="hidden text-xs text-muted-foreground sm:inline">
                    {item.href}
                  </span>
                </Link>
              );
            })
          ) : (
            <div className="px-4 py-10 text-center text-sm text-muted-foreground">
              {messages.common.noMatches}
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}

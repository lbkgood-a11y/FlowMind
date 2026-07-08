"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { adminApi, type UserInfoPayload } from "@/lib/admin";
import { Header } from "@/components/layout/header";
import { Main } from "@/components/layout/main";
import { GlobalSearch } from "@/components/layout/global-search";
import { LocaleSwitcher } from "@/components/layout/locale-switcher";
import { ThemeSettings } from "@/components/layout/theme-settings";
import { ProfileDropdown } from "@/components/layout/profile-dropdown";
import { UsersProvider } from "./components/users-provider";
import { UsersPrimaryButtons } from "./components/users-primary-buttons";
import { UsersTable } from "./components/users-table";
import { UsersDialogs } from "./components/users-dialogs";
import { useI18n } from "@/lib/i18n";
import type { User } from "./data/schema";

export function UsersFeature() {
  const router = useRouter();
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const { messages } = useI18n();

  useEffect(() => {
    const token = localStorage.getItem("accessToken");
    if (!token) {
      router.replace("/login");
      return;
    }
    void loadUsers();
  }, [router]);

  async function loadUsers() {
    setLoading(true);
    try {
      const userPage = await adminApi.listUsers(1, 200);
      setUsers(userPage.records);
    } catch {
      // handled by page fallback
    } finally {
      setLoading(false);
    }
  }

  return (
    <UsersProvider>
      <Header fixed>
        <div className="ml-auto flex items-center gap-2">
          <GlobalSearch />
          <LocaleSwitcher />
          <ThemeSettings />
          <ProfileDropdown />
        </div>
      </Header>

      <Main className="flex flex-1 flex-col gap-4 sm:gap-6">
        <div className="flex flex-wrap items-end justify-between gap-2">
          <div>
            <h2 className="text-2xl font-bold tracking-tight">
              {messages.pages.users.title}
            </h2>
            <p className="text-muted-foreground">
              {messages.pages.users.subtitle}
            </p>
          </div>
          <UsersPrimaryButtons />
        </div>
        <UsersTable data={users} loading={loading} />
      </Main>

      <UsersDialogs onSuccess={loadUsers} />
    </UsersProvider>
  );
}

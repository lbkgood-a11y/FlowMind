"use client";

import type { ReactNode } from "react";
import { Header } from "@/components/layout/header";
import { Main } from "@/components/layout/main";
import { GlobalSearch } from "@/components/layout/global-search";
import { LocaleSwitcher } from "@/components/layout/locale-switcher";
import { ProfileDropdown } from "@/components/layout/profile-dropdown";
import { ThemeSettings } from "@/components/layout/theme-settings";

type AppPageProps = {
  children: ReactNode;
  topbarActions?: ReactNode;
  fluid?: boolean;
  mainClassName?: string;
};

export function AppPage({
  children,
  topbarActions,
  fluid,
  mainClassName,
}: AppPageProps) {
  return (
    <>
      <Header fixed>
        <div className="ml-auto flex items-center gap-2">
          {topbarActions}
          <GlobalSearch />
          <LocaleSwitcher />
          <ThemeSettings />
          <ProfileDropdown />
        </div>
      </Header>
      <Main fluid={fluid} className={mainClassName}>
        {children}
      </Main>
    </>
  );
}

"use client";

import { usePathname } from "next/navigation";
import { AppShell } from "@/components/layout/AppShell";

/** 不需要 Sidebar 的路由前缀 */
const AUTH_ROUTES = ["/login", "/register"];

export function ClientShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const isAuth = AUTH_ROUTES.some((r) => pathname.startsWith(r));

  if (isAuth) {
    return <>{children}</>;
  }

  return <AppShell>{children}</AppShell>;
}

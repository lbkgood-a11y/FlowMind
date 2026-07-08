"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { ChevronRight, LayoutDashboard } from "lucide-react";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
  SidebarRail,
  useSidebar,
} from "@/components/ui/sidebar";
import { Badge } from "@/components/ui/badge";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { TeamSwitcher } from "./team-switcher";
import { NavUser } from "./nav-user";
import type { SidebarData, NavItem, NavLink, NavCollapsible } from "./types";
import { useI18n } from "@/lib/i18n";
import { APP_MENU_REGISTRY } from "@/lib/menu-registry";

export function AppSidebar() {
  const { messages } = useI18n();

  const sidebarData: SidebarData = {
    user: {
      name: "admin",
      email: "admin@triobase.local",
      avatar: "",
    },
    teams: [
      {
        name: messages.common.brand,
        logo: LayoutDashboard,
        plan: messages.sidebar.workspacePlan,
      },
    ],
    navGroups: [
      {
        title: messages.sidebar.group,
        items: APP_MENU_REGISTRY.filter((item) =>
          ["dashboard", "forms", "users", "roles", "orgs", "menus", "permissions"].includes(item.key),
        ).map((item) => ({
          title: {
            dashboard: messages.common.dashboard,
            forms: messages.common.forms,
            users: messages.common.users,
            roles: messages.common.roles,
            orgs: messages.common.orgs,
            menus: messages.common.menus,
            permissions: messages.common.permissions,
          }[item.key] || item.key,
          url: item.path,
          icon: item.icon,
        })),
      },
    ],
  };

  return (
    <Sidebar collapsible="icon" variant="sidebar">
      <SidebarHeader>
        <TeamSwitcher teams={sidebarData.teams} />
      </SidebarHeader>
      <SidebarContent>
        {sidebarData.navGroups.map((group) => (
          <NavGroup key={group.title} title={group.title} items={group.items} />
        ))}
      </SidebarContent>
      <SidebarFooter>
        <NavUser user={sidebarData.user} />
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>
  );
}

function NavGroup({ title, items }: { title: string; items: NavItem[] }) {
  const { state, isMobile } = useSidebar();
  const pathname = usePathname();

  return (
    <SidebarGroup>
      <SidebarGroupLabel>{title}</SidebarGroupLabel>
      <SidebarMenu>
        {items.map((item) => {
          const key = `${item.title}-${item.url}`;
          if (!item.items) {
            return <NavLinkItem key={key} item={item} pathname={pathname} />;
          }
          if (state === "collapsed" && !isMobile) {
            return <NavCollapsedDropdown key={key} item={item} pathname={pathname} />;
          }
          return <NavCollapsibleItem key={key} item={item} pathname={pathname} />;
        })}
      </SidebarMenu>
    </SidebarGroup>
  );
}

function isActive(pathname: string, url?: string): boolean {
  if (!url) return false;
  if (pathname === url) return true;
  if (pathname.startsWith(url + "/") || pathname.startsWith(url + "?")) return true;
  return false;
}

function NavLinkItem({ item, pathname }: { item: NavLink; pathname: string }) {
  const { setOpenMobile } = useSidebar();
  const active = isActive(pathname, item.url);
  return (
    <SidebarMenuItem>
      <SidebarMenuButton
        render={<Link href={item.url} onClick={() => setOpenMobile(false)} />}
        isActive={active}
        tooltip={item.title}
      >
        {item.icon && <item.icon />}
        <span>{item.title}</span>
        {item.badge && <NavBadge>{item.badge}</NavBadge>}
      </SidebarMenuButton>
    </SidebarMenuItem>
  );
}

function NavCollapsibleItem({ item, pathname }: { item: NavCollapsible; pathname: string }) {
  const hasActive = item.items.some((sub) => isActive(pathname, sub.url));
  return (
    <Collapsible
      defaultOpen={hasActive}
      className="group/collapsible"
    >
      <SidebarMenuItem>
        <CollapsibleTrigger render={<SidebarMenuButton tooltip={item.title} />}>
          {item.icon && <item.icon />}
          <span>{item.title}</span>
          {item.badge && <NavBadge>{item.badge}</NavBadge>}
          <ChevronRight className="ms-auto transition-transform duration-200 group-data-[state=open]/collapsible:rotate-90" />
        </CollapsibleTrigger>
        <CollapsibleContent className="CollapsibleContent">
          <SidebarMenuSub>
            {item.items.map((subItem) => (
              <SidebarMenuSubItem key={subItem.title}>
                <SidebarMenuSubButton
                  render={<Link href={subItem.url} />}
                  isActive={isActive(pathname, subItem.url)}
                >
                  {subItem.icon && <subItem.icon />}
                  <span>{subItem.title}</span>
                  {subItem.badge && <NavBadge>{subItem.badge}</NavBadge>}
                </SidebarMenuSubButton>
              </SidebarMenuSubItem>
            ))}
          </SidebarMenuSub>
        </CollapsibleContent>
      </SidebarMenuItem>
    </Collapsible>
  );
}

function NavCollapsedDropdown({ item, pathname }: { item: NavCollapsible; pathname: string }) {
  return (
    <SidebarMenuItem>
      <DropdownMenu>
        <DropdownMenuTrigger
          render={<SidebarMenuButton tooltip={item.title} />}
        >
          {item.icon && <item.icon />}
          <span>{item.title}</span>
          {item.badge && <NavBadge>{item.badge}</NavBadge>}
        </DropdownMenuTrigger>
        <DropdownMenuContent side="right" align="start" sideOffset={4}>
          <DropdownMenuGroup>
            <DropdownMenuLabel>{item.title}</DropdownMenuLabel>
          </DropdownMenuGroup>
          <DropdownMenuSeparator />
          {item.items.map((sub) => (
            <DropdownMenuItem key={sub.title}>
              <Link
                href={sub.url}
                className={isActive(pathname, sub.url) ? "font-medium" : ""}
              >
                {sub.icon && <sub.icon />}
                <span>{sub.title}</span>
              </Link>
            </DropdownMenuItem>
          ))}
        </DropdownMenuContent>
      </DropdownMenu>
    </SidebarMenuItem>
  );
}

function NavBadge({ children }: { children: React.ReactNode }) {
  return <Badge className="rounded-full px-1 py-0 text-xs">{children}</Badge>;
}

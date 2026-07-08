import {
  FilePlus2,
  FileText,
  Home,
  KeySquare,
  ListTree,
  Shield,
  Users,
  Building2,
  type LucideIcon,
} from "lucide-react";

export type AppMenuDefinition = {
  key: string;
  titleKey: string;
  descriptionKey: string;
  path: string;
  permissionAction: "GET" | "POST" | "PUT" | "DELETE";
  group: "general" | "forms" | "admin";
  icon: LucideIcon;
};

export const APP_MENU_REGISTRY: AppMenuDefinition[] = [
  {
    key: "dashboard",
    titleKey: "common.dashboard",
    descriptionKey: "pages.menus.descriptions.dashboard",
    path: "/",
    permissionAction: "GET",
    group: "general",
    icon: Home,
  },
  {
    key: "forms",
    titleKey: "common.forms",
    descriptionKey: "pages.menus.descriptions.forms",
    path: "/forms",
    permissionAction: "GET",
    group: "forms",
    icon: FileText,
  },
  {
    key: "new-form",
    titleKey: "common.newForm",
    descriptionKey: "pages.menus.descriptions.newForm",
    path: "/forms/new",
    permissionAction: "GET",
    group: "forms",
    icon: FilePlus2,
  },
  {
    key: "users",
    titleKey: "common.users",
    descriptionKey: "pages.menus.descriptions.users",
    path: "/admin/users",
    permissionAction: "GET",
    group: "admin",
    icon: Users,
  },
  {
    key: "roles",
    titleKey: "common.roles",
    descriptionKey: "pages.menus.descriptions.roles",
    path: "/admin/roles",
    permissionAction: "GET",
    group: "admin",
    icon: Shield,
  },
  {
    key: "orgs",
    titleKey: "common.orgs",
    descriptionKey: "pages.menus.descriptions.orgs",
    path: "/admin/orgs",
    permissionAction: "GET",
    group: "admin",
    icon: Building2,
  },
  {
    key: "menus",
    titleKey: "common.menus",
    descriptionKey: "pages.menus.descriptions.menus",
    path: "/admin/menus",
    permissionAction: "GET",
    group: "admin",
    icon: ListTree,
  },
  {
    key: "permissions",
    titleKey: "common.permissions",
    descriptionKey: "pages.menus.descriptions.permissions",
    path: "/admin/permissions",
    permissionAction: "GET",
    group: "admin",
    icon: KeySquare,
  },
];

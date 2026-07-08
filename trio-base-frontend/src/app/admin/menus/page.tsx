"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { CheckCircle2, CircleDashed, ListTree } from "lucide-react";

import { adminApi, type MenuInfo, type PermissionInfo } from "@/lib/admin";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Card, PageHeader, Table, THead, Th, Tr, Td } from "@/components/ui";
import { AppPage } from "@/components/layout/app-page";
import { useI18n } from "@/lib/i18n";

const MENU_GROUPS = ["general", "forms", "admin"] as const;

export default function MenusAdminPage() {
  const router = useRouter();
  const { messages } = useI18n();
  const [menus, setMenus] = useState<MenuInfo[]>([]);
  const [permissions, setPermissions] = useState<PermissionInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [menuKey, setMenuKey] = useState("");
  const [menuName, setMenuName] = useState("");
  const [path, setPath] = useState("");
  const [icon, setIcon] = useState("ListTree");
  const [menuGroup, setMenuGroup] = useState<(typeof MENU_GROUPS)[number]>("admin");
  const [sortOrder, setSortOrder] = useState("100");
  const [permissionId, setPermissionId] = useState("");
  const [description, setDescription] = useState("");
  const [visible, setVisible] = useState(true);

  useEffect(() => {
    const token = localStorage.getItem("accessToken");
    if (!token) {
      router.replace("/login");
      return;
    }
    void loadData();
  }, [router]);

  async function loadData() {
    setLoading(true);
    setError("");
    try {
      const [menuList, permissionList] = await Promise.all([
        adminApi.listMenus(),
        adminApi.listPermissions(),
      ]);
      setMenus(menuList);
      setPermissions(permissionList);
    } catch (e) {
      setError(e instanceof Error ? e.message : messages.pages.permissions.loadFailed);
    } finally {
      setLoading(false);
    }
  }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    try {
      await adminApi.createMenu({
        menuKey,
        menuName,
        path,
        icon,
        menuGroup,
        sortOrder: Number(sortOrder) || 100,
        visible,
        permissionId: permissionId || undefined,
        description,
      });

      setMenuKey("");
      setMenuName("");
      setPath("");
      setIcon("ListTree");
      setMenuGroup("admin");
      setSortOrder("100");
      setPermissionId("");
      setDescription("");
      setVisible(true);
      await loadData();
    } catch (e) {
      setError(e instanceof Error ? e.message : messages.pages.permissions.createFailed);
    }
  }

  async function handleDelete(id: string) {
    setError("");
    try {
      await adminApi.deleteMenu(id);
      await loadData();
    } catch (e) {
      setError(e instanceof Error ? e.message : messages.pages.permissions.deleteFailed);
    }
  }

  const permissionMap = useMemo(
    () => new Map(permissions.map((permission) => [permission.id, permission])),
    [permissions],
  );

  const menuRows = menus.map((menu) => {
    const permission = menu.permissionId ? permissionMap.get(menu.permissionId) : undefined;
    return {
      ...menu,
      groupLabel: messages.pages.menus.groups[menu.menuGroup as keyof typeof messages.pages.menus.groups] || menu.menuGroup,
      permissionKey: permission ? `${permission.action}:${permission.resource}` : messages.pages.menus.noCoverage,
      permissionDescription: permission?.description || messages.pages.menus.noCoverage,
      coverageMatched: Boolean(permission),
      coverage: permission ? messages.pages.menus.covered : messages.pages.menus.uncovered,
    };
  });

  return (
    <AppPage
      topbarActions={(
        <Link href="/admin/permissions">
          <Button variant="outline" size="sm">{messages.common.permissions}</Button>
        </Link>
      )}
    >
      <PageHeader
        breadcrumb={messages.pages.menus.breadcrumb}
        title={messages.pages.menus.title}
        subtitle={messages.pages.menus.subtitle}
        actions={(
          <Button type="submit" form="menu-form">{messages.common.create}</Button>
        )}
      />

      {error && (
        <div className="mb-4 rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {error}
        </div>
      )}

      <div className="grid gap-6 xl:grid-cols-[0.95fr_1.05fr]">
        <Card title={messages.pages.menus.createTitle}>
          <form id="menu-form" onSubmit={handleCreate} className="space-y-4">
            <div className="grid gap-4 md:grid-cols-2">
              <div className="grid gap-2">
                <Label htmlFor="menu-key">{messages.pages.menus.fields.menuKey}</Label>
                <Input
                  id="menu-key"
                  value={menuKey}
                  onChange={(e) => setMenuKey(e.target.value)}
                  placeholder="admin-users"
                  required
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="menu-name">{messages.pages.menus.columns.menu}</Label>
                <Input
                  id="menu-name"
                  value={menuName}
                  onChange={(e) => setMenuName(e.target.value)}
                  placeholder={messages.common.users}
                  required
                />
              </div>
            </div>

            <div className="grid gap-4 md:grid-cols-2">
              <div className="grid gap-2">
                <Label htmlFor="menu-path">{messages.pages.menus.columns.path}</Label>
                <Input
                  id="menu-path"
                  value={path}
                  onChange={(e) => setPath(e.target.value)}
                  placeholder="/admin/users"
                  required
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="menu-icon">{messages.pages.menus.fields.icon}</Label>
                <Input
                  id="menu-icon"
                  value={icon}
                  onChange={(e) => setIcon(e.target.value)}
                  placeholder="Users"
                />
              </div>
            </div>

            <div className="grid gap-4 md:grid-cols-3">
              <div className="grid gap-2">
                <Label htmlFor="menu-group">{messages.pages.menus.columns.group}</Label>
                <Select value={menuGroup} onValueChange={(value) => setMenuGroup((value as (typeof MENU_GROUPS)[number]) || "admin")}>
                  <SelectTrigger id="menu-group">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {MENU_GROUPS.map((group) => (
                      <SelectItem key={group} value={group}>
                        {messages.pages.menus.groups[group]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="grid gap-2">
                <Label htmlFor="menu-sort">{messages.pages.menus.fields.sort}</Label>
                <Input
                  id="menu-sort"
                  type="number"
                  value={sortOrder}
                  onChange={(e) => setSortOrder(e.target.value)}
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="menu-visible">{messages.pages.menus.fields.visible}</Label>
                <Select value={visible ? "visible" : "hidden"} onValueChange={(value) => setVisible(value !== "hidden")}>
                  <SelectTrigger id="menu-visible">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="visible">{messages.pages.menus.fields.visibleOn}</SelectItem>
                    <SelectItem value="hidden">{messages.pages.menus.fields.visibleOff}</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="menu-permission">{messages.pages.menus.fields.permission}</Label>
              <Select value={permissionId || "none"} onValueChange={(value) => setPermissionId(value === "none" ? "" : value || "")}>
                <SelectTrigger id="menu-permission">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="none">{messages.pages.menus.noCoverage}</SelectItem>
                  {permissions.map((permission) => (
                    <SelectItem key={permission.id} value={permission.id}>
                      {permission.action}:{permission.resource}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="menu-desc">{messages.common.description}</Label>
              <textarea
                id="menu-desc"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder={messages.pages.menus.registryNote}
                rows={3}
                className="w-full rounded-lg border border-input bg-transparent px-3 py-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 placeholder:text-muted-foreground"
              />
            </div>
          </form>
        </Card>

        <Card title={messages.pages.menus.catalogTitle} subtitle={messages.pages.menus.coverageTitle}>
          {loading ? (
            <div className="py-10 text-sm text-muted-foreground">{messages.common.loading}</div>
          ) : (
            <Table>
              <THead>
                <tr>
                  <Th>{messages.pages.menus.columns.menu}</Th>
                  <Th>{messages.pages.menus.columns.path}</Th>
                  <Th>{messages.pages.menus.columns.group}</Th>
                  <Th>{messages.pages.menus.columns.permission}</Th>
                  <Th>{messages.pages.menus.columns.status}</Th>
                  <Th>{messages.common.actions}</Th>
                </tr>
              </THead>
              <tbody>
                {menuRows.map((menu) => (
                  <Tr key={menu.id}>
                    <Td>
                      <div className="flex items-center gap-2">
                        <ListTree className="size-4 text-muted-foreground" />
                        <div>
                          <div className="font-medium text-foreground">{menu.menuName}</div>
                          <div className="mt-0.5 text-xs text-muted-foreground">{menu.description || menu.menuKey}</div>
                        </div>
                      </div>
                    </Td>
                    <Td className="font-mono text-xs text-muted-foreground">{menu.path}</Td>
                    <Td>
                      <Badge variant="secondary">{menu.groupLabel}</Badge>
                    </Td>
                    <Td>
                      <div className="font-mono text-xs text-foreground">{menu.permissionKey}</div>
                      <div className="mt-0.5 text-xs text-muted-foreground">{menu.permissionDescription}</div>
                    </Td>
                    <Td>
                      <span className="inline-flex items-center gap-1 text-xs font-medium">
                        {menu.coverageMatched ? (
                          <CheckCircle2 className="size-3.5 text-emerald-600" />
                        ) : (
                          <CircleDashed className="size-3.5 text-amber-600" />
                        )}
                        {menu.coverage}
                      </span>
                    </Td>
                    <Td>
                      <Button variant="destructive" size="xs" onClick={() => void handleDelete(menu.id)}>
                        {messages.pages.permissions.delete}
                      </Button>
                    </Td>
                  </Tr>
                ))}
              </tbody>
            </Table>
          )}
        </Card>
      </div>
    </AppPage>
  );
}

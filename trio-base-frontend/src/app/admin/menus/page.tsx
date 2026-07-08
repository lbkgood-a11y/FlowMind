"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import {
  CheckCircle2,
  CircleDashed,
  ListTree,
  Trash2,
  Globe,
  FileText,
  Shield,
} from "lucide-react";

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
import {
  TreeView,
  type TreeDataItem,
} from "@/components/ui/tree-view";
import { Card, PageHeader } from "@/components/ui";
import { AppPage } from "@/components/layout/app-page";
import { useI18n } from "@/lib/i18n";

const MENU_GROUPS = ["general", "forms", "admin"] as const;

const groupIcons: Record<string, React.ComponentType<{ className?: string }>> = {
  general: Globe,
  forms: FileText,
  admin: Shield,
};

/** 将扁平菜单列表转为 TreeDataItem 树 */
function buildMenuTree(menus: MenuInfo[]): TreeDataItem[] {
  const byParent = new Map<string | null, MenuInfo[]>();
  for (const m of menus) {
    const pid = m.parentId ?? null;
    if (!byParent.has(pid)) byParent.set(pid, []);
    byParent.get(pid)!.push(m);
  }
  function build(parentId: string | null): TreeDataItem[] {
    return (byParent.get(parentId) ?? [])
      .sort((a, b) => a.sortOrder - b.sortOrder)
      .map((m) => ({
        id: m.id,
        name: m.menuName,
        icon: groupIcons[m.menuGroup] ?? ListTree,
        children: build(m.id),
      }));
  }
  return build(null);
}

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
  const [selectedMenuId, setSelectedMenuId] = useState<string | undefined>(undefined);

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
      if (selectedMenuId === id) setSelectedMenuId(undefined);
      await loadData();
    } catch (e) {
      setError(e instanceof Error ? e.message : messages.pages.permissions.deleteFailed);
    }
  }

  const permissionMap = useMemo(
    () => new Map(permissions.map((p) => [p.id, p])),
    [permissions],
  );

  const menuMap = useMemo(
    () => new Map(menus.map((m) => [m.id, m])),
    [menus],
  );

  const treeData = useMemo(() => buildMenuTree(menus), [menus]);

  const selectedMenu = selectedMenuId ? menuMap.get(selectedMenuId) : undefined;
  const selectedPermission = selectedMenu?.permissionId
    ? permissionMap.get(selectedMenu.permissionId)
    : undefined;

  return (
    <AppPage
      topbarActions={
        <Link href="/admin/permissions">
          <Button variant="outline" size="sm">
            {messages.common.permissions}
          </Button>
        </Link>
      }
    >
      <PageHeader
        breadcrumb={messages.pages.menus.breadcrumb}
        title={messages.pages.menus.title}
        subtitle={messages.pages.menus.subtitle}
        actions={
          <Button type="submit" form="menu-form">
            {messages.common.create}
          </Button>
        }
      />

      {error && (
        <div className="mb-4 rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {error}
        </div>
      )}

      <div className="grid gap-6 xl:grid-cols-[0.38fr_0.62fr]">
        {/* 左侧：菜单树 */}
        <Card
          title={messages.pages.menus.catalogTitle}
          subtitle={messages.pages.menus.coverageTitle}
        >
          {loading ? (
            <div className="py-10 text-sm text-muted-foreground">
              {messages.common.loading}
            </div>
          ) : treeData.length === 0 ? (
            <div className="py-10 text-sm text-muted-foreground">
              {messages.common.loading}
            </div>
          ) : (
            <div className="-ml-2">
              <TreeView
                data={treeData}
                initialSelectedItemId={selectedMenuId}
                onSelectChange={(item) =>
                  item && setSelectedMenuId(item.id)
                }
                expandAll
                renderItem={({ item, isSelected }) => {
                  const menu = menuMap.get(item.id);
                  if (!menu) return null;
                  const perm = menu.permissionId
                    ? permissionMap.get(menu.permissionId)
                    : undefined;
                  return (
                    <div className="flex w-full items-center justify-between gap-2 py-0.5">
                      <div className="flex items-center gap-2 min-w-0">
                        <span className="text-sm font-medium truncate">
                          {menu.menuName}
                        </span>
                        <span className="text-xs text-muted-foreground hidden sm:inline shrink-0">
                          {menu.path}
                        </span>
                      </div>
                      <span className="shrink-0">
                        {perm ? (
                          <CheckCircle2 className="size-3.5 text-emerald-600" />
                        ) : (
                          <CircleDashed className="size-3.5 text-amber-600" />
                        )}
                      </span>
                    </div>
                  );
                }}
              />
            </div>
          )}
        </Card>

        {/* 右侧：新建表单 */}
        <Card title={messages.pages.menus.createTitle}>
          {selectedMenu && (
            <div className="mb-4 rounded-lg border bg-muted/20 p-4">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium">{selectedMenu.menuName}</p>
                  <p className="mt-0.5 text-xs text-muted-foreground">
                    {selectedMenu.path} · {selectedMenu.menuKey}
                    {selectedPermission &&
                      ` · ${selectedPermission.action}:${selectedPermission.resource}`}
                  </p>
                </div>
                <Button
                  variant="destructive"
                  size="sm"
                  onClick={() => void handleDelete(selectedMenu.id)}
                >
                  <Trash2 className="size-4 mr-1" />
                  {messages.pages.permissions.delete}
                </Button>
              </div>
              {selectedMenu.description && (
                <p className="mt-2 text-xs text-muted-foreground">
                  {selectedMenu.description}
                </p>
              )}
            </div>
          )}

          <form id="menu-form" onSubmit={handleCreate} className="space-y-4">
            <div className="grid gap-4 md:grid-cols-2">
              <div className="grid gap-2">
                <Label htmlFor="menu-key">
                  {messages.pages.menus.fields.menuKey}
                </Label>
                <Input
                  id="menu-key"
                  value={menuKey}
                  onChange={(e) => setMenuKey(e.target.value)}
                  placeholder="admin-users"
                  required
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="menu-name">
                  {messages.pages.menus.columns.menu}
                </Label>
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
                <Label htmlFor="menu-path">
                  {messages.pages.menus.columns.path}
                </Label>
                <Input
                  id="menu-path"
                  value={path}
                  onChange={(e) => setPath(e.target.value)}
                  placeholder="/admin/users"
                  required
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="menu-icon">
                  {messages.pages.menus.fields.icon}
                </Label>
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
                <Label htmlFor="menu-group">
                  {messages.pages.menus.columns.group}
                </Label>
                <Select
                  value={menuGroup}
                  onValueChange={(v) =>
                    setMenuGroup(
                      (v as (typeof MENU_GROUPS)[number]) || "admin",
                    )
                  }
                >
                  <SelectTrigger id="menu-group">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {MENU_GROUPS.map((g) => (
                      <SelectItem key={g} value={g}>
                        {messages.pages.menus.groups[g]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="grid gap-2">
                <Label htmlFor="menu-sort">
                  {messages.pages.menus.fields.sort}
                </Label>
                <Input
                  id="menu-sort"
                  type="number"
                  value={sortOrder}
                  onChange={(e) => setSortOrder(e.target.value)}
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="menu-visible">
                  {messages.pages.menus.fields.visible}
                </Label>
                <Select
                  value={visible ? "visible" : "hidden"}
                  onValueChange={(v) => setVisible(v !== "hidden")}
                >
                  <SelectTrigger id="menu-visible">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="visible">
                      {messages.pages.menus.fields.visibleOn}
                    </SelectItem>
                    <SelectItem value="hidden">
                      {messages.pages.menus.fields.visibleOff}
                    </SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="menu-permission">
                {messages.pages.menus.fields.permission}
              </Label>
              <Select
                value={permissionId || "none"}
                onValueChange={(v) =>
                  setPermissionId(v === "none" ? "" : v || "")
                }
              >
                <SelectTrigger id="menu-permission">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="none">
                    {messages.pages.menus.noCoverage}
                  </SelectItem>
                  {permissions.map((p) => (
                    <SelectItem key={p.id} value={p.id}>
                      {p.action}:{p.resource}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="menu-desc">
                {messages.common.description}
              </Label>
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
      </div>
    </AppPage>
  );
}

"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Plus, Search, Shield, Trash2 } from "lucide-react";

import {
  adminApi,
  type PermissionInfo,
  type RoleInfo,
} from "@/lib/admin";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, PageHeader } from "@/components/ui";
import { AppPage } from "@/components/layout/app-page";
import { useI18n } from "@/lib/i18n";

const NEW_ROLE_ID = "__new__";

export default function RolesAdminPage() {
  const router = useRouter();
  const { messages } = useI18n();
  const [roles, setRoles] = useState<RoleInfo[]>([]);
  const [permissions, setPermissions] = useState<PermissionInfo[]>([]);
  const [selectedRoleId, setSelectedRoleId] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [search, setSearch] = useState("");

  const [roleCode, setRoleCode] = useState("");
  const [roleName, setRoleName] = useState("");
  const [description, setDescription] = useState("");
  const [selectedPermissions, setSelectedPermissions] = useState<string[]>([]);

  useEffect(() => {
    const token = localStorage.getItem("accessToken");
    if (!token) {
      router.replace("/login");
      return;
    }
    void loadData();
  }, [router]);

  useEffect(() => {
    if (!selectedRoleId || selectedRoleId === NEW_ROLE_ID) {
      return;
    }
    void loadRoleDetail(selectedRoleId);
  }, [selectedRoleId]);

  async function loadData() {
    setLoading(true);
    setError("");
    try {
      const [roleList, permissionList] = await Promise.all([
        adminApi.listRoles(),
        adminApi.listPermissions(),
      ]);
      setRoles(roleList);
      setPermissions(permissionList);
      if (!selectedRoleId && roleList[0]) {
        setSelectedRoleId(roleList[0].id);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : messages.pages.roles.loadFailed);
    } finally {
      setLoading(false);
    }
  }

  async function loadRoleDetail(roleId: string) {
    setDetailLoading(true);
    setError("");
    try {
      const detail = await adminApi.getRoleDetail(roleId);
      setRoleCode(detail.roleCode);
      setRoleName(detail.roleName);
      setDescription(detail.description || "");
      setSelectedPermissions(detail.permissionIds || []);
    } catch (e) {
      setError(e instanceof Error ? e.message : messages.pages.roles.loadFailed);
    } finally {
      setDetailLoading(false);
    }
  }

  function handleCreateMode() {
    setSelectedRoleId(NEW_ROLE_ID);
    setRoleCode("");
    setRoleName("");
    setDescription("");
    setSelectedPermissions([]);
    setError("");
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError("");
    try {
      if (selectedRoleId && selectedRoleId !== NEW_ROLE_ID) {
        await adminApi.updateRole(selectedRoleId, {
          roleName,
          description,
          permissionIds: selectedPermissions,
        });
      } else {
        await adminApi.createRole({
          roleCode,
          roleName,
          description,
          permissionIds: selectedPermissions,
        });
      }

      await loadData();
      if (selectedRoleId === NEW_ROLE_ID) {
        const latestRoles = await adminApi.listRoles();
        setRoles(latestRoles);
        const matched = latestRoles.find((role) => role.roleCode === roleCode) || latestRoles[0];
        if (matched) {
          setSelectedRoleId(matched.id);
        }
      } else if (selectedRoleId) {
        await loadRoleDetail(selectedRoleId);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : messages.pages.roles.createFailed);
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(id: string) {
    setError("");
    try {
      await adminApi.deleteRole(id);
      await loadData();
      setSelectedRoleId("");
      setRoleCode("");
      setRoleName("");
      setDescription("");
      setSelectedPermissions([]);
    } catch (e) {
      setError(e instanceof Error ? e.message : messages.pages.roles.deleteFailed);
    }
  }

  const filteredRoles = useMemo(
    () =>
      search
        ? roles.filter(
            (role) =>
              role.roleName.toLowerCase().includes(search.toLowerCase()) ||
              role.roleCode.toLowerCase().includes(search.toLowerCase()),
          )
        : roles,
    [roles, search],
  );

  const activeRole = selectedRoleId && selectedRoleId !== NEW_ROLE_ID
    ? roles.find((role) => role.id === selectedRoleId)
    : null;

  return (
    <AppPage
      topbarActions={(
        <>
          <Link href="/admin/users">
            <Button variant="outline" size="sm">{messages.common.users}</Button>
          </Link>
          <Link href="/admin/permissions">
            <Button variant="outline" size="sm">{messages.common.permissions}</Button>
          </Link>
        </>
      )}
    >
      <PageHeader
        breadcrumb={messages.pages.roles.breadcrumb}
        title={messages.pages.roles.title}
        subtitle={messages.pages.roles.permissions}
        actions={(
          <Button size="sm" onClick={handleCreateMode}>
            <Plus className="size-4" />
            {messages.pages.roles.createRole}
          </Button>
        )}
      />

      {error ? (
        <div className="mb-4 rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {error}
        </div>
      ) : null}

      <div className="grid gap-6 xl:grid-cols-[0.42fr_0.58fr]">
        <Card title={messages.pages.roles.columns.role}>
          <div className="mb-4 relative">
            <Search className="absolute left-2.5 top-2.5 size-4 text-muted-foreground" />
            <Input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder={messages.pages.users.searchPlaceholder}
              className="pl-8 h-8"
            />
          </div>

          {loading ? (
            <div className="py-10 text-sm text-muted-foreground">{messages.pages.roles.loadBusy}</div>
          ) : (
            <div className="space-y-3">
              {filteredRoles.map((role) => {
                const active = role.id === selectedRoleId;
                return (
                  <button
                    key={role.id}
                    type="button"
                    onClick={() => setSelectedRoleId(role.id)}
                    className={`flex w-full items-start justify-between rounded-lg border px-3 py-3 text-left transition-colors ${
                      active
                        ? "border-primary bg-primary/10"
                        : "border-border bg-background hover:bg-accent/40"
                    }`}
                  >
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <Shield className="size-4 text-muted-foreground" />
                        <p className="truncate font-medium">{role.roleName}</p>
                      </div>
                      <p className="mt-1 font-mono text-xs text-muted-foreground">{role.roleCode}</p>
                      <p className="mt-1 line-clamp-2 text-xs text-muted-foreground">{role.description || "-"}</p>
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </Card>

        <Card title={activeRole?.roleName || messages.pages.roles.newRole}>
          {detailLoading ? (
            <div className="py-10 text-sm text-muted-foreground">{messages.common.loading}</div>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-5">
              <div className="rounded-lg border bg-muted/20 p-4">
                <p className="text-sm font-medium">
                  {activeRole ? activeRole.roleName : messages.pages.roles.newRole}
                </p>
                <p className="mt-1 font-mono text-xs text-muted-foreground">
                  {selectedRoleId === NEW_ROLE_ID ? "NEW_ROLE" : roleCode || "-"}
                </p>
              </div>

              <div className="grid gap-4 md:grid-cols-2">
                <div className="grid gap-2">
                  <Label htmlFor="roleCode">{messages.pages.roles.roleCode}</Label>
                  <Input
                    id="roleCode"
                    value={roleCode}
                    onChange={(event) => setRoleCode(event.target.value)}
                    placeholder="FINANCE"
                    disabled={selectedRoleId !== NEW_ROLE_ID}
                  />
                </div>
                <div className="grid gap-2">
                  <Label htmlFor="roleName">{messages.pages.roles.roleName}</Label>
                  <Input
                    id="roleName"
                    value={roleName}
                    onChange={(event) => setRoleName(event.target.value)}
                    placeholder="财务经理"
                  />
                </div>
              </div>

              <div className="grid gap-2">
                <Label htmlFor="roleDesc">{messages.pages.roles.roleDescription}</Label>
                <textarea
                  id="roleDesc"
                  value={description}
                  onChange={(event) => setDescription(event.target.value)}
                  placeholder="角色描述"
                  rows={3}
                  className="w-full rounded-lg border border-input bg-transparent px-3 py-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 placeholder:text-muted-foreground"
                />
              </div>

              <div className="rounded-lg border border-border p-4">
                <div className="mb-3 flex items-center justify-between">
                  <p className="text-sm font-medium">{messages.pages.roles.permissions}</p>
                  <Badge variant="secondary">{selectedPermissions.length}</Badge>
                </div>
                <div className="grid gap-2">
                  {permissions.map((permission) => (
                    <label
                      key={permission.id}
                      className="flex items-start gap-2 rounded-lg border border-transparent px-2 py-2 text-sm text-muted-foreground hover:border-border hover:bg-accent/20"
                    >
                      <input
                        type="checkbox"
                        checked={selectedPermissions.includes(permission.id)}
                        onChange={(event) =>
                          setSelectedPermissions((current) =>
                            event.target.checked
                              ? [...current, permission.id]
                              : current.filter((item) => item !== permission.id),
                          )
                        }
                      />
                      <span>
                        <span className="font-mono text-xs text-muted-foreground">
                          {permission.action}
                        </span>{" "}
                        {permission.resource}
                        <span className="ml-2 text-xs text-muted-foreground">
                          {permission.description}
                        </span>
                      </span>
                    </label>
                  ))}
                </div>
              </div>

              <div className="flex items-center justify-between gap-3">
                <div className="text-xs text-muted-foreground">
                  {activeRole ? activeRole.description || "-" : messages.pages.roles.newRole}
                </div>
                <div className="flex gap-2">
                  {activeRole ? (
                    <Button
                      type="button"
                      variant="destructive"
                      size="sm"
                      onClick={() => void handleDelete(activeRole.id)}
                    >
                      <Trash2 className="size-4" />
                      {messages.pages.roles.delete}
                    </Button>
                  ) : null}
                  <Button type="submit" size="sm" disabled={saving}>
                    {saving ? messages.pages.roles.createBusy : messages.common.save}
                  </Button>
                </div>
              </div>
            </form>
          )}
        </Card>
      </div>
    </AppPage>
  );
}

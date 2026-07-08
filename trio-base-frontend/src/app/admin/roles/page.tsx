"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";

import { adminApi, type PermissionInfo, type RoleInfo } from "@/lib/admin";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Card,
  PageHeader,
  Table,
  THead,
  Th,
  Tr,
  Td,
} from "@/components/ui";
import { AppPage } from "@/components/layout/app-page";
import { useI18n } from "@/lib/i18n";

export default function RolesAdminPage() {
  const router = useRouter();
  const { messages } = useI18n();
  const [roles, setRoles] = useState<RoleInfo[]>([]);
  const [permissions, setPermissions] = useState<PermissionInfo[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
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
    } catch (e) {
      setError(e instanceof Error ? e.message : messages.pages.roles.loadFailed);
    } finally {
      setLoading(false);
    }
  }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError("");
    try {
      await adminApi.createRole({
        roleCode,
        roleName,
        description,
        permissionIds: selectedPermissions,
      });
      setRoleCode("");
      setRoleName("");
      setDescription("");
      setSelectedPermissions([]);
      await loadData();
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
    } catch (e) {
      setError(e instanceof Error ? e.message : messages.pages.roles.deleteFailed);
    }
  }

  return (
    <AppPage
      topbarActions={(
        <Link href="/admin/users">
          <Button variant="outline" size="sm">{messages.common.users}</Button>
        </Link>
      )}
    >
      <PageHeader
        breadcrumb={messages.pages.roles.breadcrumb}
        title={messages.pages.roles.title}
        actions={
          <Button disabled={saving} type="submit" form="role-form">
            {saving ? messages.pages.roles.createBusy : messages.pages.roles.createRole}
          </Button>
        }
      />

      <div className="grid gap-6 lg:grid-cols-[1fr_1.1fr]">
        <Card title={messages.pages.roles.newRole}>
          <form id="role-form" onSubmit={handleCreate} className="space-y-4">
            <div className="grid gap-2">
              <Label htmlFor="roleCode">{messages.pages.roles.roleCode}</Label>
              <Input
                id="roleCode"
                value={roleCode}
                onChange={(e) => setRoleCode(e.target.value)}
                placeholder="如 FINANCE"
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="roleName">{messages.pages.roles.roleName}</Label>
              <Input
                id="roleName"
                value={roleName}
                onChange={(e) => setRoleName(e.target.value)}
                placeholder="如 财务经理"
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="roleDesc">{messages.pages.roles.roleDescription}</Label>
              <textarea
                id="roleDesc"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="角色描述"
                rows={3}
                className="w-full rounded-lg border border-input bg-transparent px-3 py-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 placeholder:text-muted-foreground"
              />
            </div>

            <div className="rounded border border-border p-4">
              <p className="text-sm font-medium text-foreground">{messages.pages.roles.permissions}</p>
              <div className="mt-3 grid gap-2">
                {permissions.map((permission) => (
                  <label
                    key={permission.id}
                    className="flex items-start gap-2 text-sm text-muted-foreground"
                  >
                    <input
                      type="checkbox"
                      checked={selectedPermissions.includes(permission.id)}
                      onChange={(e) =>
                        setSelectedPermissions((current) =>
                          e.target.checked
                            ? [...current, permission.id]
                            : current.filter((item) => item !== permission.id)
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

            {error && (
              <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">{error}</div>
            )}
          </form>
        </Card>

        <Card>
          {loading ? (
            <div className="py-10 text-sm text-muted-foreground">{messages.pages.roles.loadBusy}</div>
          ) : (
            <Table>
              <THead>
                <tr>
                  <Th>{messages.pages.roles.columns.role}</Th>
                  <Th>{messages.pages.roles.columns.code}</Th>
                  <Th>{messages.pages.roles.columns.description}</Th>
                  <Th>{messages.pages.roles.columns.actions}</Th>
                </tr>
              </THead>
              <tbody>
                {roles.map((role) => (
                  <Tr key={role.id}>
                    <Td className="font-medium text-foreground">
                      {role.roleName}
                    </Td>
                    <Td className="font-mono text-xs text-muted-foreground">
                      {role.roleCode}
                    </Td>
                    <Td className="text-muted-foreground">
                      {role.description || "-"}
                    </Td>
                    <Td>
                      <Button variant="destructive" size="xs" onClick={() => void handleDelete(role.id)}>{messages.pages.roles.delete}</Button>
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

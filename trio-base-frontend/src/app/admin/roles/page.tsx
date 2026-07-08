"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Shell } from "@/components/Shell";
import { adminApi, type PermissionInfo, type RoleInfo } from "@/lib/admin";
import {
  Card,
  PageHeader,
  Table,
  THead,
  Th,
  Tr,
  Td,
} from "@/components/ui";

export default function RolesAdminPage() {
  const router = useRouter();
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
      setError(e instanceof Error ? e.message : "加载角色失败");
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
      setError(e instanceof Error ? e.message : "创建角色失败");
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
      setError(e instanceof Error ? e.message : "删除角色失败");
    }
  }

  return (
    <Shell>
      <PageHeader
        breadcrumb="Admin Console"
        title="角色管理"
        actions={
          <Link
            href="/admin/users"
            className="rounded border border-border px-4 py-2 text-sm text-fg-secondary hover:bg-surface"
          >
            用户管理
          </Link>
        }
      />

      <div className="grid gap-6 lg:grid-cols-[1fr_1.1fr]">
        <Card title="新建角色">
          <form onSubmit={handleCreate} className="space-y-4">
            <input
              value={roleCode}
              onChange={(e) => setRoleCode(e.target.value)}
              placeholder="角色编码，如 FINANCE"
              className="w-full rounded border border-border px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
            />
            <input
              value={roleName}
              onChange={(e) => setRoleName(e.target.value)}
              placeholder="角色名称，如 财务经理"
              className="w-full rounded border border-border px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
            />
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="角色描述"
              rows={3}
              className="w-full rounded border border-border px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
            />

            <div className="rounded border border-border p-4">
              <p className="text-sm font-medium text-fg-primary">权限绑定</p>
              <div className="mt-3 grid gap-2">
                {permissions.map((permission) => (
                  <label
                    key={permission.id}
                    className="flex items-start gap-2 text-sm text-fg-secondary"
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
                      <span className="font-mono text-xs text-fg-tertiary">
                        {permission.action}
                      </span>{" "}
                      {permission.resource}
                      <span className="ml-2 text-xs text-fg-tertiary">
                        {permission.description}
                      </span>
                    </span>
                  </label>
                ))}
              </div>
            </div>

            {error && (
              <div className="rounded border border-danger-fg/30 bg-danger-bg px-4 py-3 text-sm text-danger-fg">
                {error}
              </div>
            )}

            <button
              disabled={saving}
              className="rounded bg-fg-primary px-5 py-2.5 text-sm font-medium text-white hover:opacity-90 disabled:opacity-50"
            >
              {saving ? "创建中..." : "创建角色"}
            </button>
          </form>
        </Card>

        <Card>
          {loading ? (
            <div className="py-10 text-sm text-fg-tertiary">加载中...</div>
          ) : (
            <Table>
              <THead>
                <tr>
                  <Th>角色</Th>
                  <Th>编码</Th>
                  <Th>描述</Th>
                  <Th>操作</Th>
                </tr>
              </THead>
              <tbody>
                {roles.map((role) => (
                  <Tr key={role.id}>
                    <Td className="font-medium text-fg-primary">
                      {role.roleName}
                    </Td>
                    <Td className="font-mono text-xs text-fg-secondary">
                      {role.roleCode}
                    </Td>
                    <Td className="text-fg-secondary">
                      {role.description || "-"}
                    </Td>
                    <Td>
                      <button
                        onClick={() => void handleDelete(role.id)}
                        className="rounded border border-danger-fg/30 px-3 py-1.5 text-xs text-danger-fg hover:bg-danger-bg"
                      >
                        删除
                      </button>
                    </Td>
                  </Tr>
                ))}
              </tbody>
            </Table>
          )}
        </Card>
      </div>
    </Shell>
  );
}

"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Shell } from "@/components/Shell";
import { adminApi, type PermissionInfo, type RoleInfo } from "@/lib/admin";

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
      <div className="grid h-full gap-6 lg:grid-cols-[1fr_1.1fr]">
        <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-sm uppercase tracking-[0.24em] text-slate-500">Admin Console</p>
              <h1 className="mt-1 text-3xl font-semibold text-slate-900">角色管理</h1>
            </div>
            <Link href="/admin/users" className="rounded-md border border-slate-300 px-4 py-2 text-sm text-slate-700 hover:bg-slate-50">
              用户管理
            </Link>
          </div>

          <form onSubmit={handleCreate} className="mt-8 space-y-4">
            <input value={roleCode} onChange={(e) => setRoleCode(e.target.value)} placeholder="角色编码，如 FINANCE" className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" />
            <input value={roleName} onChange={(e) => setRoleName(e.target.value)} placeholder="角色名称，如 财务经理" className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" />
            <textarea value={description} onChange={(e) => setDescription(e.target.value)} placeholder="角色描述" rows={3} className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" />

            <div className="rounded-xl border border-slate-200 p-4">
              <p className="text-sm font-medium text-slate-900">权限绑定</p>
              <div className="mt-3 grid gap-2">
                {permissions.map((permission) => (
                  <label key={permission.id} className="flex items-start gap-2 text-sm text-slate-700">
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
                      <span className="font-mono text-xs text-slate-500">{permission.action}</span>{" "}
                      {permission.resource}
                      <span className="ml-2 text-xs text-slate-400">{permission.description}</span>
                    </span>
                  </label>
                ))}
              </div>
            </div>

            {error && <div className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

            <button disabled={saving} className="rounded-md bg-slate-900 px-5 py-2.5 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50">
              {saving ? "创建中..." : "创建角色"}
            </button>
          </form>
        </section>

        <section className="rounded-2xl border border-slate-200 bg-white shadow-sm">
          <div className="border-b border-slate-200 px-6 py-4">
            <h2 className="text-lg font-medium text-slate-900">角色列表</h2>
          </div>
          {loading ? (
            <div className="px-6 py-10 text-sm text-slate-500">加载中...</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full text-left text-sm">
                <thead className="bg-slate-50 text-slate-500">
                  <tr>
                    <th className="px-6 py-3 font-medium">角色</th>
                    <th className="px-6 py-3 font-medium">编码</th>
                    <th className="px-6 py-3 font-medium">描述</th>
                    <th className="px-6 py-3 font-medium">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {roles.map((role) => (
                    <tr key={role.id} className="border-t border-slate-100">
                      <td className="px-6 py-4 font-medium text-slate-900">{role.roleName}</td>
                      <td className="px-6 py-4 font-mono text-xs text-slate-600">{role.roleCode}</td>
                      <td className="px-6 py-4 text-slate-600">{role.description || "-"}</td>
                      <td className="px-6 py-4">
                        <button onClick={() => void handleDelete(role.id)} className="rounded-md border border-red-200 px-3 py-1.5 text-xs text-red-600 hover:bg-red-50">
                          删除
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </div>
    </Shell>
  );
}

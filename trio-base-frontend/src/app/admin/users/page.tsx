"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Shell } from "@/components/Shell";
import { adminApi, type RoleInfo, type UserInfoPayload } from "@/lib/admin";

export default function UsersAdminPage() {
  const router = useRouter();
  const [users, setUsers] = useState<UserInfoPayload[]>([]);
  const [roles, setRoles] = useState<RoleInfo[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState<string | null>(null);

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
      const [userPage, roleList] = await Promise.all([
        adminApi.listUsers(),
        adminApi.listRoles(),
      ]);
      setUsers(userPage.records);
      setRoles(roleList);
    } catch (e) {
      setError(e instanceof Error ? e.message : "加载用户失败");
    } finally {
      setLoading(false);
    }
  }

  async function handleStatus(user: UserInfoPayload) {
    setSavingId(user.id);
    setError("");
    try {
      await adminApi.updateUserStatus(user.id, user.status === 1 ? 0 : 1);
      await loadData();
    } catch (e) {
      setError(e instanceof Error ? e.message : "更新状态失败");
    } finally {
      setSavingId(null);
    }
  }

  async function handleAssignRole(userId: string, roleCode: string) {
    const matched = roles.find((role) => role.roleCode === roleCode);
    if (!matched) return;
    setSavingId(userId);
    setError("");
    try {
      await adminApi.assignUserRoles(userId, [matched.id]);
      await loadData();
    } catch (e) {
      setError(e instanceof Error ? e.message : "分配角色失败");
    } finally {
      setSavingId(null);
    }
  }

  return (
    <Shell>
      <div className="flex h-full flex-col gap-6">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-sm uppercase tracking-[0.24em] text-slate-500">Admin Console</p>
            <h1 className="mt-1 text-3xl font-semibold text-slate-900">用户管理</h1>
            <p className="mt-2 text-sm text-slate-600">维护用户状态与角色分配。</p>
          </div>
          <div className="flex gap-3">
            <Link href="/admin/roles" className="rounded-md border border-slate-300 px-4 py-2 text-sm text-slate-700 hover:bg-slate-50">角色管理</Link>
            <Link href="/admin/menus" className="rounded-md border border-slate-300 px-4 py-2 text-sm text-slate-700 hover:bg-slate-50">菜单权限</Link>
            <Link href="/" className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800">返回首页</Link>
          </div>
        </div>

        {error && <div className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

        <div className="rounded-2xl border border-slate-200 bg-white shadow-sm">
          {loading ? (
            <div className="px-6 py-10 text-sm text-slate-500">加载中...</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full text-left text-sm">
                <thead className="bg-slate-50 text-slate-500">
                  <tr>
                    <th className="px-6 py-3 font-medium">用户名</th>
                    <th className="px-6 py-3 font-medium">邮箱</th>
                    <th className="px-6 py-3 font-medium">角色</th>
                    <th className="px-6 py-3 font-medium">状态</th>
                    <th className="px-6 py-3 font-medium">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {users.map((user) => (
                    <tr key={user.id} className="border-t border-slate-100">
                      <td className="px-6 py-4">
                        <div className="font-medium text-slate-900">{user.username}</div>
                        <div className="mt-1 text-xs text-slate-500">{user.id}</div>
                      </td>
                      <td className="px-6 py-4 text-slate-600">{user.email || "-"}</td>
                      <td className="px-6 py-4">
                        <div className="mb-2 flex flex-wrap gap-2">
                          {(user.roles || []).map((role) => (
                            <span key={role} className="rounded-full bg-slate-100 px-2.5 py-1 text-xs text-slate-700">{role}</span>
                          ))}
                        </div>
                        <select
                          className="rounded-md border border-slate-300 px-3 py-2 text-sm"
                          defaultValue=""
                          onChange={(e) => {
                            if (e.target.value) {
                              void handleAssignRole(user.id, e.target.value);
                              e.target.value = "";
                            }
                          }}
                        >
                          <option value="">分配角色...</option>
                          {roles.map((role) => (
                            <option key={role.id} value={role.roleCode}>
                              {role.roleName}
                            </option>
                          ))}
                        </select>
                      </td>
                      <td className="px-6 py-4">
                        <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${user.status === 1 ? "bg-emerald-100 text-emerald-700" : "bg-red-100 text-red-700"}`}>
                          {user.status === 1 ? "启用" : "停用"}
                        </span>
                      </td>
                      <td className="px-6 py-4">
                        <button
                          onClick={() => void handleStatus(user)}
                          disabled={savingId === user.id}
                          className="rounded-md border border-slate-300 px-3 py-1.5 text-xs text-slate-700 hover:bg-slate-50 disabled:opacity-50"
                        >
                          {savingId === user.id ? "处理中..." : user.status === 1 ? "停用" : "启用"}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </Shell>
  );
}

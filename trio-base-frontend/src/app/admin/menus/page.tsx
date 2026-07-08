"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Shell } from "@/components/Shell";
import { adminApi, type PermissionInfo } from "@/lib/admin";

export default function MenusAdminPage() {
  const router = useRouter();
  const [permissions, setPermissions] = useState<PermissionInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [resource, setResource] = useState("");
  const [action, setAction] = useState("GET");
  const [description, setDescription] = useState("");

  useEffect(() => {
    const token = localStorage.getItem("accessToken");
    if (!token) {
      router.replace("/login");
      return;
    }
    void loadPermissions();
  }, [router]);

  async function loadPermissions() {
    setLoading(true);
    setError("");
    try {
      const list = await adminApi.listPermissions();
      setPermissions(list);
    } catch (e) {
      setError(e instanceof Error ? e.message : "加载菜单权限失败");
    } finally {
      setLoading(false);
    }
  }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    try {
      await adminApi.createPermission({ resource, action, description });
      setResource("");
      setAction("GET");
      setDescription("");
      await loadPermissions();
    } catch (e) {
      setError(e instanceof Error ? e.message : "创建权限失败");
    }
  }

  async function handleDelete(id: string) {
    setError("");
    try {
      await adminApi.deletePermission(id);
      await loadPermissions();
    } catch (e) {
      setError(e instanceof Error ? e.message : "删除权限失败");
    }
  }

  return (
    <Shell>
      <div className="grid h-full gap-6 lg:grid-cols-[0.95fr_1.05fr]">
        <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-sm uppercase tracking-[0.24em] text-slate-500">Admin Console</p>
              <h1 className="mt-1 text-3xl font-semibold text-slate-900">菜单与权限</h1>
              <p className="mt-2 text-sm text-slate-600">
                当前先以资源路径 + 动作的方式维护菜单权限，为后续真正菜单树做基础模型。
              </p>
            </div>
            <Link href="/admin/roles" className="rounded-md border border-slate-300 px-4 py-2 text-sm text-slate-700 hover:bg-slate-50">
              角色管理
            </Link>
          </div>

          <form onSubmit={handleCreate} className="mt-8 space-y-4">
            <input value={resource} onChange={(e) => setResource(e.target.value)} placeholder="/console/forms" className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" />
            <select value={action} onChange={(e) => setAction(e.target.value)} className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm">
              {["GET", "POST", "PUT", "DELETE"].map((item) => (
                <option key={item} value={item}>{item}</option>
              ))}
            </select>
            <textarea value={description} onChange={(e) => setDescription(e.target.value)} placeholder="例如：查看表单管理页面" rows={3} className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" />

            {error && <div className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

            <button className="rounded-md bg-slate-900 px-5 py-2.5 text-sm font-medium text-white hover:bg-slate-800">
              新增菜单权限
            </button>
          </form>
        </section>

        <section className="rounded-2xl border border-slate-200 bg-white shadow-sm">
          <div className="border-b border-slate-200 px-6 py-4">
            <h2 className="text-lg font-medium text-slate-900">权限目录</h2>
          </div>
          {loading ? (
            <div className="px-6 py-10 text-sm text-slate-500">加载中...</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full text-left text-sm">
                <thead className="bg-slate-50 text-slate-500">
                  <tr>
                    <th className="px-6 py-3 font-medium">资源</th>
                    <th className="px-6 py-3 font-medium">动作</th>
                    <th className="px-6 py-3 font-medium">说明</th>
                    <th className="px-6 py-3 font-medium">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {permissions.map((permission) => (
                    <tr key={permission.id} className="border-t border-slate-100">
                      <td className="px-6 py-4 font-mono text-xs text-slate-700">{permission.resource}</td>
                      <td className="px-6 py-4">
                        <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs text-slate-700">{permission.action}</span>
                      </td>
                      <td className="px-6 py-4 text-slate-600">{permission.description || "-"}</td>
                      <td className="px-6 py-4">
                        <button onClick={() => void handleDelete(permission.id)} className="rounded-md border border-red-200 px-3 py-1.5 text-xs text-red-600 hover:bg-red-50">
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

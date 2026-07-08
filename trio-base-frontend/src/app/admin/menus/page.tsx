"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Shell } from "@/components/Shell";
import { adminApi, type PermissionInfo } from "@/lib/admin";
import { Card, PageHeader, Table, THead, Th, Tr, Td } from "@/components/ui";

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
      <PageHeader
        breadcrumb="Admin Console"
        title="菜单与权限"
        subtitle="当前先以资源路径 + 动作的方式维护菜单权限，为后续真正菜单树做基础模型。"
        actions={
          <Link
            href="/admin/roles"
            className="rounded border border-border px-4 py-2 text-sm text-fg-secondary hover:bg-surface"
          >
            角色管理
          </Link>
        }
      />

      <div className="grid gap-6 lg:grid-cols-[0.95fr_1.05fr]">
        <Card title="新增菜单权限">
          <form onSubmit={handleCreate} className="space-y-4">
            <input
              value={resource}
              onChange={(e) => setResource(e.target.value)}
              placeholder="/console/forms"
              className="w-full rounded border border-border px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
            />
            <select
              value={action}
              onChange={(e) => setAction(e.target.value)}
              className="w-full rounded border border-border px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
            >
              {["GET", "POST", "PUT", "DELETE"].map((item) => (
                <option key={item} value={item}>
                  {item}
                </option>
              ))}
            </select>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="例如：查看表单管理页面"
              rows={3}
              className="w-full rounded border border-border px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
            />

            {error && (
              <div className="rounded border border-danger-fg/30 bg-danger-bg px-4 py-3 text-sm text-danger-fg">
                {error}
              </div>
            )}

            <button className="rounded bg-fg-primary px-5 py-2.5 text-sm font-medium text-white hover:opacity-90">
              新增菜单权限
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
                  <Th>资源</Th>
                  <Th>动作</Th>
                  <Th>说明</Th>
                  <Th>操作</Th>
                </tr>
              </THead>
              <tbody>
                {permissions.map((permission) => (
                  <Tr key={permission.id}>
                    <Td className="font-mono text-xs text-fg-primary">
                      {permission.resource}
                    </Td>
                    <Td>
                      <span className="rounded-full bg-surface px-2.5 py-0.5 text-xs text-fg-secondary">
                        {permission.action}
                      </span>
                    </Td>
                    <Td className="text-fg-secondary">
                      {permission.description || "-"}
                    </Td>
                    <Td>
                      <button
                        onClick={() => void handleDelete(permission.id)}
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

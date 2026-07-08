"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Shell } from "@/components/Shell";
import { adminApi, type RoleInfo, type UserInfoPayload } from "@/lib/admin";
import {
  Card,
  PageHeader,
  Table,
  THead,
  Th,
  Tr,
  Td,
  StatusBadge,
} from "@/components/ui";

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
      <PageHeader
        breadcrumb="Admin Console"
        title="用户管理"
        subtitle="维护用户状态与角色分配。"
        actions={
          <>
            <Link
              href="/admin/roles"
              className="rounded border border-border px-4 py-2 text-sm text-fg-secondary hover:bg-surface"
            >
              角色管理
            </Link>
            <Link
              href="/admin/menus"
              className="rounded border border-border px-4 py-2 text-sm text-fg-secondary hover:bg-surface"
            >
              菜单权限
            </Link>
            <Link
              href="/"
              className="rounded bg-fg-primary px-4 py-2 text-sm font-medium text-white hover:opacity-90"
            >
              返回首页
            </Link>
          </>
        }
      />

      {error && (
        <div className="rounded border border-danger-fg/30 bg-danger-bg px-4 py-3 text-sm text-danger-fg">
          {error}
        </div>
      )}

      <Card>
        {loading ? (
          <div className="py-10 text-sm text-fg-tertiary">加载中...</div>
        ) : (
          <Table>
            <THead>
              <tr>
                <Th>用户名</Th>
                <Th>邮箱</Th>
                <Th>角色</Th>
                <Th>状态</Th>
                <Th>操作</Th>
              </tr>
            </THead>
            <tbody>
              {users.map((user) => (
                <Tr key={user.id}>
                  <Td>
                    <div className="font-medium text-fg-primary">{user.username}</div>
                    <div className="mt-0.5 text-xs text-fg-tertiary">{user.id}</div>
                  </Td>
                  <Td className="text-fg-secondary">{user.email || "-"}</Td>
                  <Td>
                    <div className="mb-2 flex flex-wrap gap-2">
                      {(user.roles || []).map((role) => (
                        <span
                          key={role}
                          className="rounded-full bg-surface px-2.5 py-0.5 text-xs text-fg-secondary"
                        >
                          {role}
                        </span>
                      ))}
                    </div>
                    <select
                      className="rounded border border-border px-3 py-2 text-sm"
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
                  </Td>
                  <Td>
                    <StatusBadge
                      status={user.status === 1 ? "success" : "danger"}
                      label={user.status === 1 ? "启用" : "停用"}
                    />
                  </Td>
                  <Td>
                    <button
                      onClick={() => void handleStatus(user)}
                      disabled={savingId === user.id}
                      className="rounded border border-border px-3 py-1.5 text-xs text-fg-secondary hover:bg-surface disabled:opacity-50"
                    >
                      {savingId === user.id
                        ? "处理中..."
                        : user.status === 1
                          ? "停用"
                          : "启用"}
                    </button>
                  </Td>
                </Tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>
    </Shell>
  );
}

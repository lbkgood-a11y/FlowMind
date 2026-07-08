"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";

import { adminApi, type PermissionInfo } from "@/lib/admin";
import type { PageResult } from "@/lib/lowcode";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
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
import { ChevronLeft, ChevronRight } from "lucide-react";

export default function PermissionsAdminPage() {
  const router = useRouter();
  const { messages } = useI18n();
  const [permissionPage, setPermissionPage] = useState<PageResult<PermissionInfo> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [page, setPage] = useState(1);
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
  }, [router, page]);

  async function loadPermissions() {
    setLoading(true);
    setError("");
    try {
      const result = await adminApi.listPermissionsPage(page, 20);
      setPermissionPage(result);
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
      await adminApi.createPermission({ resource, action, description });
      setResource("");
      setAction("GET");
      setDescription("");
      setPage(1);
      await loadPermissions();
    } catch (e) {
      setError(e instanceof Error ? e.message : messages.pages.permissions.createFailed);
    }
  }

  async function handleDelete(id: string) {
    setError("");
    try {
      await adminApi.deletePermission(id);
      await loadPermissions();
    } catch (e) {
      setError(e instanceof Error ? e.message : messages.pages.permissions.deleteFailed);
    }
  }

  const totalPages = permissionPage ? Math.ceil(permissionPage.total / permissionPage.size) : 1;

  return (
    <AppPage
      topbarActions={(
        <>
          <Link href="/admin/menus">
            <Button variant="outline" size="sm">{messages.common.menus}</Button>
          </Link>
          <Link href="/admin/roles">
            <Button variant="outline" size="sm">{messages.common.roles}</Button>
          </Link>
        </>
      )}
    >
      <PageHeader
        breadcrumb={messages.pages.permissions.breadcrumb}
        title={messages.pages.permissions.title}
        subtitle={messages.pages.permissions.subtitle}
        actions={
          <Button type="submit" form="permission-form">
            {messages.pages.permissions.newPermission}
          </Button>
        }
      />

      <div className="grid gap-6 lg:grid-cols-[0.95fr_1.05fr]">
        <Card title={messages.pages.permissions.newPermission}>
          <form id="permission-form" onSubmit={handleCreate} className="space-y-4">
            <div className="grid gap-2">
              <Label htmlFor="resource">{messages.pages.permissions.resource}</Label>
              <Input
                id="resource"
                value={resource}
                onChange={(e) => setResource(e.target.value)}
                placeholder="/admin/users"
                required
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="action">{messages.pages.permissions.action}</Label>
              <Select value={action} onValueChange={(value) => setAction(value ?? "GET")}>
                <SelectTrigger id="action">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {["GET", "POST", "PUT", "DELETE"].map((item) => (
                    <SelectItem key={item} value={item}>
                      {item}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid gap-2">
              <Label htmlFor="permDesc">{messages.pages.permissions.description}</Label>
              <textarea
                id="permDesc"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="例如：查看用户管理页面"
                rows={3}
                className="w-full rounded-lg border border-input bg-transparent px-3 py-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 placeholder:text-muted-foreground"
              />
            </div>

            {error && (
              <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
                {error}
              </div>
            )}
          </form>
        </Card>

        <Card title={messages.pages.permissions.title}>
          {loading ? (
            <div className="py-10 text-sm text-muted-foreground">
              {messages.pages.permissions.loadBusy}
            </div>
          ) : !permissionPage || permissionPage.records.length === 0 ? (
            <div className="py-10 text-sm text-muted-foreground">
              {messages.pages.permissions.loadBusy}
            </div>
          ) : (
            <>
              <Table>
                <THead>
                  <tr>
                    <Th>{messages.pages.permissions.columns.resource}</Th>
                    <Th>{messages.pages.permissions.columns.action}</Th>
                    <Th>{messages.pages.permissions.columns.description}</Th>
                    <Th>{messages.pages.permissions.columns.actions}</Th>
                  </tr>
                </THead>
                <tbody>
                  {permissionPage.records.map((permission) => (
                    <Tr key={permission.id}>
                      <Td className="font-mono text-xs text-foreground">
                        {permission.resource}
                      </Td>
                      <Td>
                        <Badge variant="secondary">{permission.action}</Badge>
                      </Td>
                      <Td className="text-muted-foreground">
                        {permission.description || "-"}
                      </Td>
                      <Td>
                        <Button
                          variant="destructive"
                          size="xs"
                          onClick={() => void handleDelete(permission.id)}
                        >
                          {messages.pages.permissions.delete}
                        </Button>
                      </Td>
                    </Tr>
                  ))}
                </tbody>
              </Table>

              {totalPages > 1 && (
                <div className="flex items-center justify-between border-t px-4 py-3">
                  <p className="text-xs text-muted-foreground">
                    {messages.pages.users.total
                      .replace("{total}", String(permissionPage.total))
                      .replace("{page}", String(page))
                      .replace("{pages}", String(totalPages))}
                  </p>
                  <div className="flex gap-2">
                    <Button
                      variant="outline"
                      size="xs"
                      disabled={page <= 1}
                      onClick={() => setPage((p) => Math.max(1, p - 1))}
                    >
                      <ChevronLeft /> {messages.pages.users.previous}
                    </Button>
                    <Button
                      variant="outline"
                      size="xs"
                      disabled={page >= totalPages}
                      onClick={() => setPage((p) => p + 1)}
                    >
                      {messages.pages.users.next} <ChevronRight />
                    </Button>
                  </div>
                </div>
              )}
            </>
          )}
        </Card>
      </div>
    </AppPage>
  );
}

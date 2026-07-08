"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { adminApi, type RoleInfo, type UserInfoPayload } from "@/lib/admin";
import { orgApi, type OrgUnitInfo, type UserOrgRelation } from "@/lib/org";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { PageHeader } from "@/components/ui/PageHeader";
import { AppPage } from "@/components/layout/app-page";
import { useI18n } from "@/lib/i18n";
import {
  Search,
  Plus,
  CircleCheck,
  CircleX,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";

export default function UsersAdminPage() {
  const router = useRouter();
  const { messages } = useI18n();
  const [users, setUsers] = useState<UserInfoPayload[]>([]);
  const [roles, setRoles] = useState<RoleInfo[]>([]);
  const [orgUnits, setOrgUnits] = useState<OrgUnitInfo[]>([]);
  const [orgRelations, setOrgRelations] = useState<UserOrgRelation[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState<string | null>(null);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [search, setSearch] = useState("");

  // New user dialog
  const [newUsername, setNewUsername] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [newEmail, setNewEmail] = useState("");
  const [creating, setCreating] = useState(false);
  const [newDialogOpen, setNewDialogOpen] = useState(false);

  useEffect(() => {
    const token = localStorage.getItem("accessToken");
    if (!token) {
      router.replace("/login");
      return;
    }
    void loadData();
  }, [router, page]);

  async function loadData() {
    setLoading(true);
    setError("");
    try {
      const [userPage, roleList, unitList, relationList] = await Promise.all([
        adminApi.listUsers(page, 20),
        adminApi.listRoles(),
        orgApi.listOrgUnits(),
        orgApi.listUserOrgRelations(),
      ]);
      setUsers(userPage.records);
      setTotal(userPage.total);
      setRoles(roleList);
      setOrgUnits(unitList);
      setOrgRelations(relationList);
    } catch (e) {
      setError(e instanceof Error ? e.message : messages.pages.users.loadFailed);
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
      setError(e instanceof Error ? e.message : messages.pages.users.updateFailed);
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
      setError(e instanceof Error ? e.message : messages.pages.users.assignFailed);
    } finally {
      setSavingId(null);
    }
  }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    setCreating(true);
    setError("");
    try {
      const params = new URLSearchParams({ username: newUsername, password: newPassword });
      if (newEmail) params.append("email", newEmail);
      const res = await fetch(`/api/v1/auth/register?${params.toString()}`, {
        method: "POST",
      });
      if (!res.ok) {
        const data = await res.json();
        throw new Error(data.message || "创建失败");
      }
      setNewDialogOpen(false);
      setNewUsername("");
      setNewPassword("");
      setNewEmail("");
      await loadData();
    } catch (e) {
      setError(e instanceof Error ? e.message : messages.pages.users.createFailed);
    } finally {
      setCreating(false);
    }
  }

  const totalPages = Math.ceil(total / 20);
  const filteredUsers = search
    ? users.filter((u) => u.username.includes(search) || (u.email && u.email.includes(search)))
    : users;

  const orgUnitMap = new Map(orgUnits.map((unit) => [unit.id, unit]));

  function getInitials(name: string): string {
    return name.slice(0, 2).toUpperCase();
  }

  return (
    <AppPage
      topbarActions={(
        <>
          <Link href="/admin/roles">
            <Button variant="outline" size="sm">{messages.common.roles}</Button>
          </Link>
          <Link href="/admin/menus">
            <Button variant="outline" size="sm">{messages.common.menus}</Button>
          </Link>
          <Link href="/admin/permissions">
            <Button variant="outline" size="sm">{messages.common.permissions}</Button>
          </Link>
        </>
      )}
    >
      <PageHeader
        breadcrumb={messages.pages.users.breadcrumb}
        title={messages.pages.users.title}
        subtitle={messages.pages.users.subtitle}
        actions={(
          <Dialog open={newDialogOpen} onOpenChange={setNewDialogOpen}>
            <Button size="sm" onClick={() => setNewDialogOpen(true)}>
              <Plus /> {messages.pages.users.createUser}
            </Button>
            <DialogContent>
              <form onSubmit={handleCreate}>
                <DialogHeader>
                  <DialogTitle>{messages.pages.users.createUser}</DialogTitle>
                  <DialogDescription>{messages.pages.users.createUserDescription}</DialogDescription>
                </DialogHeader>
                <div className="grid gap-4 py-4">
                  <div className="grid gap-2">
                    <Label htmlFor="nu-username">{messages.common.username}</Label>
                    <Input id="nu-username" value={newUsername} onChange={(e) => setNewUsername(e.target.value)} placeholder="username" required />
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="nu-email">{messages.common.email}</Label>
                    <Input id="nu-email" type="email" value={newEmail} onChange={(e) => setNewEmail(e.target.value)} placeholder={messages.pages.users.emailPlaceholder} />
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="nu-password">{messages.common.password}</Label>
                    <Input id="nu-password" type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} placeholder="至少8位，含大小写字母+数字" required />
                    <p className="text-xs text-muted-foreground">{messages.pages.users.passwordHint}</p>
                  </div>
                </div>
                <DialogFooter>
                  <Button type="button" variant="outline" onClick={() => setNewDialogOpen(false)}>{messages.common.cancel}</Button>
                  <Button type="submit" disabled={creating}>{creating ? messages.pages.users.createBusy : messages.common.create}</Button>
                </DialogFooter>
              </form>
            </DialogContent>
          </Dialog>
        )}
      />

        {error && (
          <div className="mb-4 rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">{error}</div>
        )}

        <Card>
          <CardHeader className="pb-3">
            <div className="flex items-center justify-between">
              <CardTitle>{messages.pages.users.allUsers}</CardTitle>
              <div className="relative w-64">
                <Search className="absolute left-2.5 top-2.5 size-4 text-muted-foreground" />
                <Input
                  placeholder={messages.pages.users.searchPlaceholder}
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  className="pl-8 h-8"
                />
              </div>
            </div>
          </CardHeader>
          <CardContent className="p-0">
            {loading ? (
              <div className="py-10 text-center text-sm text-muted-foreground">{messages.common.loading}</div>
            ) : filteredUsers.length === 0 ? (
              <div className="py-10 text-center text-sm text-muted-foreground">{messages.pages.users.empty}</div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm">
                  <thead className="bg-muted text-xs text-muted-foreground">
                    <tr>
                      <th className="whitespace-nowrap px-4 py-3 font-medium">{messages.pages.users.columns.user}</th>
                      <th className="whitespace-nowrap px-4 py-3 font-medium">{messages.pages.users.columns.email}</th>
                      <th className="whitespace-nowrap px-4 py-3 font-medium">{messages.pages.users.columns.roles}</th>
                      <th className="whitespace-nowrap px-4 py-3 font-medium">{messages.common.orgs}</th>
                      <th className="whitespace-nowrap px-4 py-3 font-medium">{messages.pages.users.columns.status}</th>
                      <th className="whitespace-nowrap px-4 py-3 font-medium">{messages.pages.users.columns.createdAt}</th>
                      <th className="whitespace-nowrap px-4 py-3 font-medium">{messages.pages.users.columns.actions}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredUsers.map((user) => (
                      <tr
                        key={user.id}
                        className="border-t transition-colors even:bg-muted/30 hover:bg-accent"
                      >
                        <td className="px-4 py-3">
                          <div className="flex items-center gap-3">
                            <Avatar className="size-8">
                              <AvatarFallback className="text-xs">{getInitials(user.username)}</AvatarFallback>
                            </Avatar>
                            <div>
                              <div className="font-medium text-foreground">{user.username}</div>
                              <div className="text-xs text-muted-foreground">{user.id}</div>
                            </div>
                          </div>
                        </td>
                        <td className="px-4 py-3 text-muted-foreground">{user.email || "-"}</td>
                        <td className="px-4 py-3">
                          <div className="mb-2 flex flex-wrap gap-2">
                            {(user.roles || []).map((role) => (
                              <Badge key={role} variant="secondary">{role}</Badge>
                            ))}
                          </div>
                          <Select
                            value=""
                            onValueChange={(v) => handleAssignRole(user.id, v ?? "")}
                          >
                            <SelectTrigger className="h-7 text-xs w-32">
                              <SelectValue placeholder={messages.pages.users.assignRole} />
                            </SelectTrigger>
                            <SelectContent>
                              {roles.map((role) => (
                                <SelectItem key={role.id} value={role.roleCode}>
                                  {role.roleName}
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex flex-wrap gap-2">
                            {orgRelations
                              .filter((relation) => relation.userId === user.id)
                              .map((relation) => orgUnitMap.get(relation.orgUnitId))
                              .filter(Boolean)
                              .map((unit) => (
                                <Badge key={unit!.id} variant="outline">{unit!.unitName}</Badge>
                              ))}
                          </div>
                        </td>
                        <td className="px-4 py-3">
                          {user.status === 1 ? (
                            <span className="inline-flex items-center gap-1 text-xs font-medium text-emerald-600">
                              <CircleCheck className="size-3.5" /> {messages.pages.users.enabled}
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1 text-xs font-medium text-destructive">
                              <CircleX className="size-3.5" /> {messages.pages.users.disabled}
                            </span>
                          )}
                        </td>
                        <td className="px-4 py-3 text-xs text-muted-foreground">
                          {user.createdAt ? new Date(user.createdAt).toLocaleDateString("zh-CN") : "-"}
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex gap-2">
                            <Button
                              variant={user.status === 1 ? "outline" : "default"}
                              size="xs"
                              onClick={() => void handleStatus(user)}
                              disabled={savingId === user.id}
                            >
                              {savingId === user.id
                                ? "..."
                                : user.status === 1
                                  ? messages.pages.users.disable
                                  : messages.pages.users.enable}
                            </Button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </CardContent>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-between border-t px-4 py-3">
              <p className="text-xs text-muted-foreground">
                {messages.pages.users.total
                  .replace("{total}", String(total))
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
        </Card>
    </AppPage>
  );
}

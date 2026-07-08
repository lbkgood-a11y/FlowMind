"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Building2, ChevronRight, Users } from "lucide-react";

import { adminApi, type UserInfoPayload } from "@/lib/admin";
import { orgApi, type OrgUnitInfo, type UserOrgRelation } from "@/lib/org";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Card, PageHeader } from "@/components/ui";
import { AppPage } from "@/components/layout/app-page";
import { useI18n } from "@/lib/i18n";

const ROOT_PARENT = "__root__";

export default function OrgAdminPage() {
  const router = useRouter();
  const { messages } = useI18n();
  const [units, setUnits] = useState<OrgUnitInfo[]>([]);
  const [relations, setRelations] = useState<UserOrgRelation[]>([]);
  const [users, setUsers] = useState<UserInfoPayload[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [unitCode, setUnitCode] = useState("");
  const [unitName, setUnitName] = useState("");
  const [parentId, setParentId] = useState("");
  const [sortOrder, setSortOrder] = useState("100");
  const [description, setDescription] = useState("");
  const [enabled, setEnabled] = useState(true);

  const [selectedUserId, setSelectedUserId] = useState("");
  const [selectedOrgIds, setSelectedOrgIds] = useState<string[]>([]);

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
      const [unitList, relationList, userPage] = await Promise.all([
        orgApi.listOrgUnits(),
        orgApi.listUserOrgRelations(),
        adminApi.listUsers(1, 200),
      ]);
      setUnits(unitList);
      setRelations(relationList);
      setUsers(userPage.records);
      if (!selectedUserId && userPage.records[0]) {
        setSelectedUserId(userPage.records[0].id);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : messages.pages.orgs.loadFailed);
    } finally {
      setLoading(false);
    }
  }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    try {
      await orgApi.createOrgUnit({
        unitCode,
        unitName,
        parentId: parentId || undefined,
        sortOrder: Number(sortOrder) || 100,
        enabled,
        description,
      });
      setUnitCode("");
      setUnitName("");
      setParentId("");
      setSortOrder("100");
      setDescription("");
      setEnabled(true);
      await loadData();
    } catch (e) {
      setError(e instanceof Error ? e.message : messages.pages.orgs.createFailed);
    }
  }

  async function handleDelete(id: string) {
    setError("");
    try {
      await orgApi.deleteOrgUnit(id);
      await loadData();
    } catch (e) {
      setError(e instanceof Error ? e.message : messages.pages.orgs.deleteFailed);
    }
  }

  async function handleSaveAssignments() {
    if (!selectedUserId) {
      return;
    }
    setError("");
    try {
      await orgApi.assignUserOrgUnits(selectedUserId, selectedOrgIds);
      await loadData();
    } catch (e) {
      setError(e instanceof Error ? e.message : messages.pages.orgs.assignFailed);
    }
  }

  const unitMap = useMemo(() => new Map(units.map((unit) => [unit.id, unit])), [units]);
  const userOrgMap = useMemo(() => {
    const map = new Map<string, string[]>();
    for (const relation of relations) {
      const current = map.get(relation.userId) || [];
      current.push(relation.orgUnitId);
      map.set(relation.userId, current);
    }
    return map;
  }, [relations]);

  useEffect(() => {
    if (selectedUserId) {
      setSelectedOrgIds(userOrgMap.get(selectedUserId) || []);
    }
  }, [selectedUserId, userOrgMap]);

  const sortedUnits = useMemo(
    () =>
      [...units].sort((a, b) => {
        const pathA = a.treePath || a.id;
        const pathB = b.treePath || b.id;
        return pathA.localeCompare(pathB);
      }),
    [units],
  );

  function getDepth(treePath?: string): number {
    if (!treePath) {
      return 0;
    }
    return Math.max(treePath.split("/").filter(Boolean).length - 1, 0);
  }

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
        breadcrumb={messages.pages.orgs.breadcrumb}
        title={messages.pages.orgs.title}
        subtitle={messages.pages.orgs.subtitle}
        actions={(
          <Button type="submit" form="org-unit-form">{messages.common.create}</Button>
        )}
      />

      {error && (
        <div className="mb-4 rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {error}
        </div>
      )}

      <div className="grid gap-6 xl:grid-cols-[0.95fr_1.05fr]">
        <Card title={messages.pages.orgs.createTitle}>
          <form id="org-unit-form" onSubmit={handleCreate} className="space-y-4">
            <div className="grid gap-4 md:grid-cols-2">
              <div className="grid gap-2">
                <Label htmlFor="unit-code">{messages.pages.orgs.fields.code}</Label>
                <Input
                  id="unit-code"
                  value={unitCode}
                  onChange={(e) => setUnitCode(e.target.value)}
                  placeholder="TECH-FE"
                  required
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="unit-name">{messages.pages.orgs.fields.name}</Label>
                <Input
                  id="unit-name"
                  value={unitName}
                  onChange={(e) => setUnitName(e.target.value)}
                  placeholder="前端组"
                  required
                />
              </div>
            </div>

            <div className="grid gap-4 md:grid-cols-3">
              <div className="grid gap-2">
                <Label htmlFor="unit-parent">{messages.pages.orgs.fields.parent}</Label>
                <Select value={parentId || ROOT_PARENT} onValueChange={(value) => setParentId(value === ROOT_PARENT ? "" : value || "")}>
                  <SelectTrigger id="unit-parent">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value={ROOT_PARENT}>{messages.pages.orgs.noParent}</SelectItem>
                    {sortedUnits.map((unit) => (
                      <SelectItem key={unit.id} value={unit.id}>
                        {unit.unitName}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="grid gap-2">
                <Label htmlFor="unit-sort">{messages.pages.orgs.fields.sort}</Label>
                <Input
                  id="unit-sort"
                  type="number"
                  value={sortOrder}
                  onChange={(e) => setSortOrder(e.target.value)}
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="unit-status">{messages.pages.orgs.fields.status}</Label>
                <Select value={enabled ? "enabled" : "disabled"} onValueChange={(value) => setEnabled(value !== "disabled")}>
                  <SelectTrigger id="unit-status">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="enabled">{messages.pages.orgs.fields.enabled}</SelectItem>
                    <SelectItem value="disabled">{messages.pages.orgs.fields.disabled}</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="unit-desc">{messages.pages.orgs.fields.description}</Label>
              <textarea
                id="unit-desc"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder={messages.pages.orgs.subtitle}
                rows={3}
                className="w-full rounded-lg border border-input bg-transparent px-3 py-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 placeholder:text-muted-foreground"
              />
            </div>
          </form>
        </Card>

        <Card title={messages.pages.orgs.treeTitle} subtitle={messages.pages.orgs.assignmentHint}>
          {loading ? (
            <div className="py-10 text-sm text-muted-foreground">{messages.common.loading}</div>
          ) : sortedUnits.length === 0 ? (
            <div className="py-10 text-sm text-muted-foreground">{messages.pages.orgs.empty}</div>
          ) : (
            <div className="space-y-3">
              {sortedUnits.map((unit) => {
                const depth = getDepth(unit.treePath);
                const memberCount = relations.filter((relation) => relation.orgUnitId === unit.id).length;
                return (
                  <div key={unit.id} className="rounded-lg border border-border bg-muted/20 p-3">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0" style={{ paddingLeft: `${depth * 16}px` }}>
                        <div className="flex items-center gap-2">
                          {depth > 0 ? <ChevronRight className="size-4 text-muted-foreground" /> : <Building2 className="size-4 text-muted-foreground" />}
                          <p className="font-medium text-foreground">{unit.unitName}</p>
                        </div>
                        <p className="mt-1 text-xs text-muted-foreground">{unit.unitCode} · {unit.treePath}</p>
                        {unit.description ? (
                          <p className="mt-1 text-xs text-muted-foreground">{unit.description}</p>
                        ) : null}
                      </div>
                      <div className="flex shrink-0 items-center gap-2">
                        <Badge variant="secondary">
                          <Users className="mr-1 size-3" />
                          {memberCount}
                        </Badge>
                        <Button variant="destructive" size="xs" onClick={() => void handleDelete(unit.id)}>
                          {messages.pages.permissions.delete}
                        </Button>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </Card>
      </div>

      <div className="mt-6">
        <Card title={messages.pages.orgs.membersTitle}>
          {loading ? (
            <div className="py-10 text-sm text-muted-foreground">{messages.common.loading}</div>
          ) : (
            <div className="grid gap-6 lg:grid-cols-[0.35fr_0.65fr]">
              <div className="space-y-3">
                {users.map((user) => (
                  <button
                    key={user.id}
                    type="button"
                    onClick={() => setSelectedUserId(user.id)}
                    className={`flex w-full items-center justify-between rounded-lg border px-3 py-3 text-left text-sm transition-colors ${
                      selectedUserId === user.id
                        ? "border-primary bg-primary/10"
                        : "border-border bg-background hover:bg-accent/40"
                    }`}
                  >
                    <div className="min-w-0">
                      <p className="truncate font-medium">{user.username}</p>
                      <p className="truncate text-xs text-muted-foreground">{user.email || user.id}</p>
                    </div>
                    <Badge variant="secondary">
                      {(userOrgMap.get(user.id) || []).length}
                    </Badge>
                  </button>
                ))}
              </div>

              <div>
                <div className="mb-4 flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium">{messages.pages.orgs.fields.assign}</p>
                    <p className="text-xs text-muted-foreground">{messages.pages.orgs.assignmentHint}</p>
                  </div>
                  <Button size="sm" onClick={() => void handleSaveAssignments()} disabled={!selectedUserId}>
                    {messages.pages.orgs.saveAssignment}
                  </Button>
                </div>

                <div className="space-y-2">
                  {sortedUnits.map((unit) => {
                    const depth = getDepth(unit.treePath);
                    const checked = selectedOrgIds.includes(unit.id);
                    return (
                      <label
                        key={unit.id}
                        className="flex items-start gap-3 rounded-lg border border-border px-3 py-3 text-sm hover:bg-accent/30"
                        style={{ paddingLeft: `${12 + depth * 16}px` }}
                      >
                        <input
                          type="checkbox"
                          checked={checked}
                          onChange={(event) => {
                            setSelectedOrgIds((current) =>
                              event.target.checked
                                ? [...current, unit.id]
                                : current.filter((item) => item !== unit.id),
                            );
                          }}
                        />
                        <span>
                          <span className="block font-medium">{unit.unitName}</span>
                          <span className="block text-xs text-muted-foreground">
                            {unit.unitCode} · {unitMap.get(unit.parentId || "")?.unitName || messages.pages.orgs.noParent}
                          </span>
                        </span>
                      </label>
                    );
                  })}
                </div>
              </div>
            </div>
          )}
        </Card>
      </div>
    </AppPage>
  );
}

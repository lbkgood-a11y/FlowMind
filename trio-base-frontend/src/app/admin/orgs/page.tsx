"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import {
  Building2,
  ChevronRight,
  Users,
  GitBranch,
  Plus,
  Trash2,
} from "lucide-react";

import { adminApi, type UserInfoPayload } from "@/lib/admin";
import { orgApi, type OrgUnitInfo, type UserOrgRelation } from "@/lib/org";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  TreeView,
  type TreeDataItem,
} from "@/components/ui/tree-view";
import { Card, PageHeader } from "@/components/ui";
import { AppPage } from "@/components/layout/app-page";
import { useI18n } from "@/lib/i18n";

const ROOT_PARENT = "__root__";

/** 将扁平的组织单元列表转为 TreeDataItem 树 */
function buildOrgTree(
  units: OrgUnitInfo[],
  parentId: string | null = null,
): TreeDataItem[] {
  return units
    .filter((u) => (u.parentId ?? null) === parentId)
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .map((u) => ({
      id: u.id,
      name: u.unitName,
      icon: u.parentId ? ChevronRight : Building2,
      children: buildOrgTree(units, u.id),
    }));
}

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
  const [selectedUnitId, setSelectedUnitId] = useState("");

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
      if (!selectedUnitId && unitList[0]) {
        setSelectedUnitId(unitList[0].id);
      }
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
      if (selectedUnitId === id) setSelectedUnitId("");
      await loadData();
    } catch (e) {
      setError(e instanceof Error ? e.message : messages.pages.orgs.deleteFailed);
    }
  }

  async function handleSaveAssignments() {
    if (!selectedUserId) return;
    setError("");
    try {
      await orgApi.assignUserOrgUnits(selectedUserId, selectedOrgIds);
      await loadData();
    } catch (e) {
      setError(e instanceof Error ? e.message : messages.pages.orgs.assignFailed);
    }
  }

  const unitMap = useMemo(
    () => new Map(units.map((u) => [u.id, u])),
    [units],
  );
  const userOrgMap = useMemo(() => {
    const map = new Map<string, string[]>();
    for (const r of relations) {
      const cur = map.get(r.userId) ?? [];
      cur.push(r.orgUnitId);
      map.set(r.userId, cur);
    }
    return map;
  }, [relations]);

  useEffect(() => {
    if (selectedUserId) {
      setSelectedOrgIds(userOrgMap.get(selectedUserId) ?? []);
    }
  }, [selectedUserId, userOrgMap]);

  const treeData = useMemo(() => buildOrgTree(units), [units]);

  const sortedUnits = useMemo(
    () =>
      [...units].sort((a, b) => {
        const pa = a.treePath || a.id;
        const pb = b.treePath || b.id;
        return pa.localeCompare(pb);
      }),
    [units],
  );

  const selectedUnit = selectedUnitId ? unitMap.get(selectedUnitId) : undefined;

  return (
    <AppPage
      topbarActions={
        <>
          <Link href="/admin/users">
            <Button variant="outline" size="sm">
              {messages.common.users}
            </Button>
          </Link>
          <Link href="/admin/permissions">
            <Button variant="outline" size="sm">
              {messages.common.permissions}
            </Button>
          </Link>
        </>
      }
    >
      <PageHeader
        breadcrumb={messages.pages.orgs.breadcrumb}
        title={messages.pages.orgs.title}
        subtitle={messages.pages.orgs.subtitle}
        actions={
          <Button type="submit" form="org-unit-form">
            {messages.common.create}
          </Button>
        }
      />

      {error && (
        <div className="mb-4 rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {error}
        </div>
      )}

      <div className="grid gap-6 xl:grid-cols-[0.38fr_0.62fr]">
        {/* 左侧：组织树 */}
        <Card
          title={messages.pages.orgs.treeTitle}
          subtitle={messages.pages.orgs.assignmentHint}
        >
          {loading ? (
            <div className="py-10 text-sm text-muted-foreground">
              {messages.common.loading}
            </div>
          ) : treeData.length === 0 ? (
            <div className="py-10 text-sm text-muted-foreground">
              {messages.pages.orgs.empty}
            </div>
          ) : (
            <div className="-ml-2">
              <TreeView
                data={treeData}
                initialSelectedItemId={selectedUnitId}
                onSelectChange={(item) =>
                  item && setSelectedUnitId(item.id)
                }
                expandAll
                renderItem={({ item, isSelected }) => {
                  const unit = unitMap.get(item.id);
                  if (!unit) return null;
                  const memberCount = relations.filter(
                    (r) => r.orgUnitId === unit.id,
                  ).length;
                  return (
                    <div
                      className={`flex w-full items-center justify-between gap-2 py-0.5 ${
                        isSelected ? "" : ""
                      }`}
                    >
                      <div className="flex items-center gap-2 min-w-0">
                        <GitBranch className="size-3.5 shrink-0 text-muted-foreground" />
                        <span className="text-sm font-medium truncate">
                          {unit.unitName}
                        </span>
                        <span className="text-xs text-muted-foreground shrink-0">
                          {unit.unitCode}
                        </span>
                      </div>
                      <Badge
                        variant="secondary"
                        className="shrink-0 text-xs px-1.5 py-0 h-5"
                      >
                        <Users className="size-3 mr-0.5" />
                        {memberCount}
                      </Badge>
                    </div>
                  );
                }}
              />
            </div>
          )}
        </Card>

        {/* 右侧：详情 / 建组 / 成员 */}
        <Card>
          <Tabs defaultValue="unit" className="space-y-4">
            <TabsList>
              <TabsTrigger value="unit">
                {messages.pages.orgs.createTitle}
              </TabsTrigger>
              <TabsTrigger value="members">
                {messages.pages.orgs.membersTitle}
              </TabsTrigger>
            </TabsList>

            <TabsContent value="unit">
              {/* 当前选中节点信息 */}
              {selectedUnit ? (
                <div className="mb-4 rounded-lg border bg-muted/20 p-4">
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="text-sm font-medium">
                        {selectedUnit.unitName}
                      </p>
                      <p className="mt-0.5 text-xs text-muted-foreground">
                        {selectedUnit.unitCode} · {selectedUnit.treePath}
                      </p>
                    </div>
                    <Button
                      variant="destructive"
                      size="sm"
                      onClick={() => void handleDelete(selectedUnit.id)}
                    >
                      <Trash2 className="size-4 mr-1" />
                      {messages.pages.permissions.delete}
                    </Button>
                  </div>
                  {selectedUnit.description && (
                    <p className="mt-2 text-xs text-muted-foreground">
                      {selectedUnit.description}
                    </p>
                  )}
                </div>
              ) : (
                <div className="mb-4 rounded-lg border bg-muted/20 p-4">
                  <p className="text-sm text-muted-foreground">
                    {messages.pages.orgs.noParent}
                  </p>
                </div>
              )}

              <form id="org-unit-form" onSubmit={handleCreate} className="space-y-4">
                <div className="grid gap-4 md:grid-cols-2">
                  <div className="grid gap-2">
                    <Label htmlFor="unit-code">
                      {messages.pages.orgs.fields.code}
                    </Label>
                    <Input
                      id="unit-code"
                      value={unitCode}
                      onChange={(e) => setUnitCode(e.target.value)}
                      placeholder="TECH-FE"
                      required
                    />
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="unit-name">
                      {messages.pages.orgs.fields.name}
                    </Label>
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
                    <Label htmlFor="unit-parent">
                      {messages.pages.orgs.fields.parent}
                    </Label>
                    <Select
                      value={parentId || ROOT_PARENT}
                      onValueChange={(v) =>
                        setParentId(v === ROOT_PARENT ? "" : v || "")
                      }
                    >
                      <SelectTrigger id="unit-parent">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value={ROOT_PARENT}>
                          {messages.pages.orgs.noParent}
                        </SelectItem>
                        {sortedUnits.map((u) => (
                          <SelectItem key={u.id} value={u.id}>
                            {u.unitName}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="unit-sort">
                      {messages.pages.orgs.fields.sort}
                    </Label>
                    <Input
                      id="unit-sort"
                      type="number"
                      value={sortOrder}
                      onChange={(e) => setSortOrder(e.target.value)}
                    />
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="unit-status">
                      {messages.pages.orgs.fields.status}
                    </Label>
                    <Select
                      value={enabled ? "enabled" : "disabled"}
                      onValueChange={(v) => setEnabled(v !== "disabled")}
                    >
                      <SelectTrigger id="unit-status">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="enabled">
                          {messages.pages.orgs.fields.enabled}
                        </SelectItem>
                        <SelectItem value="disabled">
                          {messages.pages.orgs.fields.disabled}
                        </SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                </div>

                <div className="grid gap-2">
                  <Label htmlFor="unit-desc">
                    {messages.pages.orgs.fields.description}
                  </Label>
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
            </TabsContent>

            <TabsContent value="members">
              {loading ? (
                <div className="py-10 text-sm text-muted-foreground">
                  {messages.common.loading}
                </div>
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
                          <p className="truncate text-xs text-muted-foreground">
                            {user.email || user.id}
                          </p>
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
                        <p className="text-sm font-medium">
                          {messages.pages.orgs.fields.assign}
                        </p>
                        <p className="text-xs text-muted-foreground">
                          {messages.pages.orgs.assignmentHint}
                        </p>
                      </div>
                      <Button
                        size="sm"
                        onClick={() => void handleSaveAssignments()}
                        disabled={!selectedUserId}
                      >
                        {messages.pages.orgs.saveAssignment}
                      </Button>
                    </div>

                    <div className="space-y-2">
                      {sortedUnits.map((unit) => {
                        const depth = unit.treePath
                          ? Math.max(
                              unit.treePath.split("/").filter(Boolean).length - 1,
                              0,
                            )
                          : 0;
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
                                setSelectedOrgIds((cur) =>
                                  event.target.checked
                                    ? [...cur, unit.id]
                                    : cur.filter((i) => i !== unit.id),
                                );
                              }}
                            />
                            <span>
                              <span className="block font-medium">
                                {unit.unitName}
                              </span>
                              <span className="block text-xs text-muted-foreground">
                                {unit.unitCode} ·{" "}
                                {unitMap.get(unit.parentId ?? "")?.unitName ??
                                  messages.pages.orgs.noParent}
                              </span>
                            </span>
                          </label>
                        );
                      })}
                    </div>
                  </div>
                </div>
              )}
            </TabsContent>
          </Tabs>
        </Card>
      </div>
    </AppPage>
  );
}

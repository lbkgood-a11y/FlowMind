"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { lowcodeApi, type FormDefinition } from "@/lib/lowcode";
import { Shell } from "@/components/Shell";
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

export default function FormsPage() {
  const router = useRouter();
  const [forms, setForms] = useState<FormDefinition[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [publishingId, setPublishingId] = useState<string | null>(null);

  useEffect(() => {
    const token = localStorage.getItem("accessToken");
    if (!token) {
      router.replace("/login");
      return;
    }
    void loadForms();
  }, [router]);

  async function loadForms() {
    setLoading(true);
    setError("");
    try {
      const page = await lowcodeApi.listForms();
      setForms(page.records);
    } catch (e) {
      setError(e instanceof Error ? e.message : "加载表单失败");
    } finally {
      setLoading(false);
    }
  }

  async function handlePublish(formId: string) {
    setPublishingId(formId);
    setError("");
    try {
      await lowcodeApi.publishForm(formId);
      await loadForms();
    } catch (e) {
      setError(e instanceof Error ? e.message : "发布失败");
    } finally {
      setPublishingId(null);
    }
  }

  return (
    <Shell>
      <PageHeader
        breadcrumb="Form Studio"
        title="表单配置台"
        subtitle="管理统一表单定义，为后续流程和数据沉淀提供结构化入口。"
        actions={
          <>
            <Link
              href="/"
              className="rounded border border-border px-4 py-2 text-sm text-fg-secondary hover:bg-surface"
            >
              返回首页
            </Link>
            <Link
              href="/forms/new"
              className="rounded bg-fg-primary px-4 py-2 text-sm font-medium text-white hover:opacity-90"
            >
              新建表单
            </Link>
          </>
        }
      />

      {error && (
        <div className="rounded border border-danger-fg/30 bg-danger-bg px-4 py-3 text-sm text-danger-fg">
          {error}
        </div>
      )}

      <Card title="表单列表">
        {loading ? (
          <div className="py-10 text-sm text-fg-tertiary">加载中...</div>
        ) : forms.length === 0 ? (
          <div className="py-10 text-sm text-fg-tertiary">
            还没有表单，先创建一个试试。
          </div>
        ) : (
          <Table>
            <THead>
              <tr>
                <Th>名称</Th>
                <Th>表单 Key</Th>
                <Th>状态</Th>
                <Th>版本</Th>
                <Th>创建人</Th>
                <Th>操作</Th>
              </tr>
            </THead>
            <tbody>
              {forms.map((form) => (
                <Tr key={form.id}>
                  <Td>
                    <div className="font-medium text-fg-primary">{form.name}</div>
                    <div className="mt-0.5 text-xs text-fg-tertiary">
                      {form.description || "暂无描述"}
                    </div>
                  </Td>
                  <Td className="font-mono text-xs text-fg-secondary">
                    {form.formKey}
                  </Td>
                  <Td>
                    <StatusBadge
                      status={form.status === "PUBLISHED" ? "success" : "warning"}
                      label={form.status}
                    />
                  </Td>
                  <Td className="text-fg-primary">{form.version}</Td>
                  <Td className="text-fg-secondary">{form.createdBy || "-"}</Td>
                  <Td>
                    <div className="flex flex-wrap gap-2">
                      <Link
                        href={`/forms/${form.formKey}/submit?id=${form.id}`}
                        className="rounded border border-border px-3 py-1.5 text-xs text-fg-secondary hover:bg-surface"
                      >
                        提交测试
                      </Link>
                      {form.status !== "PUBLISHED" && (
                        <button
                          onClick={() => void handlePublish(form.id)}
                          disabled={publishingId === form.id}
                          className="rounded bg-fg-primary px-3 py-1.5 text-xs text-white hover:opacity-90 disabled:opacity-50"
                        >
                          {publishingId === form.id ? "发布中..." : "发布"}
                        </button>
                      )}
                    </div>
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

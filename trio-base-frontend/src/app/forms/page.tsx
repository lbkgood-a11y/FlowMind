"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { lowcodeApi, type FormDefinition } from "@/lib/lowcode";
import { Shell } from "@/components/Shell";

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
      <div className="flex h-full flex-col gap-6">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-sm uppercase tracking-[0.24em] text-slate-500">
              Form Studio
            </p>
            <h1 className="mt-1 text-3xl font-semibold text-slate-900">表单配置台</h1>
            <p className="mt-2 text-sm text-slate-600">
              管理统一表单定义，为后续流程和数据沉淀提供结构化入口。
            </p>
          </div>
          <div className="flex gap-3">
            <Link
              href="/"
              className="rounded-md border border-slate-300 px-4 py-2 text-sm text-slate-700 hover:bg-slate-50"
            >
              返回首页
            </Link>
            <Link
              href="/forms/new"
              className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800"
            >
              新建表单
            </Link>
          </div>
        </div>

        {error && (
          <div className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            {error}
          </div>
        )}

        <div className="rounded-2xl border border-slate-200 bg-white shadow-sm">
          <div className="border-b border-slate-200 px-6 py-4">
            <h2 className="text-lg font-medium text-slate-900">表单列表</h2>
          </div>

          {loading ? (
            <div className="px-6 py-10 text-sm text-slate-500">加载中...</div>
          ) : forms.length === 0 ? (
            <div className="px-6 py-10 text-sm text-slate-500">还没有表单，先创建一个试试。</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full text-left text-sm">
                <thead className="bg-slate-50 text-slate-500">
                  <tr>
                    <th className="px-6 py-3 font-medium">名称</th>
                    <th className="px-6 py-3 font-medium">表单 Key</th>
                    <th className="px-6 py-3 font-medium">状态</th>
                    <th className="px-6 py-3 font-medium">版本</th>
                    <th className="px-6 py-3 font-medium">创建人</th>
                    <th className="px-6 py-3 font-medium">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {forms.map((form) => (
                    <tr key={form.id} className="border-t border-slate-100">
                      <td className="px-6 py-4">
                        <div className="font-medium text-slate-900">{form.name}</div>
                        <div className="mt-1 text-xs text-slate-500">{form.description || "暂无描述"}</div>
                      </td>
                      <td className="px-6 py-4 font-mono text-xs text-slate-700">{form.formKey}</td>
                      <td className="px-6 py-4">
                        <span
                          className={`rounded-full px-2.5 py-1 text-xs font-medium ${
                            form.status === "PUBLISHED"
                              ? "bg-emerald-100 text-emerald-700"
                              : "bg-amber-100 text-amber-700"
                          }`}
                        >
                          {form.status}
                        </span>
                      </td>
                      <td className="px-6 py-4 text-slate-700">{form.version}</td>
                      <td className="px-6 py-4 text-slate-600">{form.createdBy || "-"}</td>
                      <td className="px-6 py-4">
                        <div className="flex flex-wrap gap-2">
                          <Link
                            href={`/forms/${form.formKey}/submit?id=${form.id}`}
                            className="rounded-md border border-slate-300 px-3 py-1.5 text-xs text-slate-700 hover:bg-slate-50"
                          >
                            提交测试
                          </Link>
                          {form.status !== "PUBLISHED" && (
                            <button
                              onClick={() => void handlePublish(form.id)}
                              disabled={publishingId === form.id}
                              className="rounded-md bg-slate-900 px-3 py-1.5 text-xs text-white hover:bg-slate-800 disabled:opacity-50"
                            >
                              {publishingId === form.id ? "发布中..." : "发布"}
                            </button>
                          )}
                        </div>
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

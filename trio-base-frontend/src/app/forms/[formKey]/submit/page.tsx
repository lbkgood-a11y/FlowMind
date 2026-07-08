"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { useRouter, useParams } from "next/navigation";
import { Shell } from "@/components/Shell";
import { lowcodeApi, type FormDefinition, type FormInstance } from "@/lib/lowcode";

export default function FormSubmitPage() {
  const router = useRouter();
  const params = useParams<{ formKey: string }>();
  const searchParams = useSearchParams();
  const formId = searchParams.get("id");

  const [form, setForm] = useState<FormDefinition | null>(null);
  const [values, setValues] = useState<Record<string, string>>({});
  const [submitted, setSubmitted] = useState<FormInstance | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    const token = localStorage.getItem("accessToken");
    if (!token) {
      router.replace("/login");
      return;
    }

    if (!formId) {
      setError("缺少表单 id，无法加载表单详情");
      setLoading(false);
      return;
    }

    void loadForm(formId);
  }, [formId, router]);

  async function loadForm(id: string) {
    setLoading(true);
    setError("");
    try {
      const detail = await lowcodeApi.getForm(id);
      setForm(detail);
      const defaults = Object.fromEntries(
        (detail.fields || []).map((field) => [field.fieldKey, field.defaultValue || ""])
      );
      setValues(defaults);
    } catch (e) {
      setError(e instanceof Error ? e.message : "加载表单失败");
    } finally {
      setLoading(false);
    }
  }

  const orderedFields = useMemo(
    () => [...(form?.fields || [])].sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0)),
    [form]
  );

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!form) return;
    setSubmitting(true);
    setError("");
    try {
      const result = await lowcodeApi.submitForm(params.formKey, {
        submittedBy: JSON.parse(localStorage.getItem("user") || "{}").username || "anonymous",
        data: values,
      });
      setSubmitted(result);
    } catch (e) {
      setError(e instanceof Error ? e.message : "提交失败");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Shell>
      <div className="mx-auto flex h-full w-full max-w-5xl flex-col gap-6">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-sm uppercase tracking-[0.24em] text-slate-500">Runtime Preview</p>
            <h1 className="mt-1 text-3xl font-semibold text-slate-900">表单提交测试</h1>
            <p className="mt-2 text-sm text-slate-600">
              用运行时视角验证表单定义是否可真正收集结构化数据。
            </p>
          </div>
          <div className="flex gap-3">
            <Link
              href="/forms"
              className="rounded-md border border-slate-300 px-4 py-2 text-sm text-slate-700 hover:bg-slate-50"
            >
              返回列表
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

        {loading ? (
          <div className="rounded-2xl border border-slate-200 bg-white px-6 py-10 text-sm text-slate-500 shadow-sm">
            加载中...
          </div>
        ) : !form ? (
          <div className="rounded-2xl border border-slate-200 bg-white px-6 py-10 text-sm text-slate-500 shadow-sm">
            未找到表单。
          </div>
        ) : (
          <div className="grid gap-6 lg:grid-cols-[1.1fr_0.9fr]">
            <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
              <h2 className="text-xl font-semibold text-slate-900">{form.name}</h2>
              <p className="mt-2 text-sm text-slate-600">{form.description || "暂无描述"}</p>
              <p className="mt-3 text-xs text-slate-500">
                表单 Key：<span className="font-mono">{form.formKey}</span>
              </p>

              <form onSubmit={handleSubmit} className="mt-8 space-y-4">
                {orderedFields.map((field) => (
                  <label key={field.fieldKey} className="block">
                    <span className="mb-1 block text-sm font-medium text-slate-700">
                      {field.label}
                      {field.required ? <span className="ml-1 text-red-500">*</span> : null}
                    </span>

                    {field.fieldType === "textarea" ? (
                      <textarea
                        value={values[field.fieldKey] || ""}
                        onChange={(e) =>
                          setValues((current) => ({ ...current, [field.fieldKey]: e.target.value }))
                        }
                        rows={4}
                        required={Boolean(field.required)}
                        placeholder={field.placeholder || ""}
                        className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
                      />
                    ) : (
                      <input
                        type={field.fieldType === "number" ? "number" : "text"}
                        value={values[field.fieldKey] || ""}
                        onChange={(e) =>
                          setValues((current) => ({ ...current, [field.fieldKey]: e.target.value }))
                        }
                        required={Boolean(field.required)}
                        placeholder={field.placeholder || ""}
                        className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
                      />
                    )}
                  </label>
                ))}

                <button
                  type="submit"
                  disabled={submitting}
                  className="rounded-md bg-slate-900 px-5 py-2.5 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50"
                >
                  {submitting ? "提交中..." : "提交表单"}
                </button>
              </form>
            </section>

            <aside className="space-y-6">
              <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
                <h3 className="text-lg font-medium text-slate-900">表单元数据</h3>
                <pre className="mt-4 overflow-auto rounded-xl bg-slate-950 p-4 text-xs leading-6 text-slate-100">
                  {JSON.stringify(form, null, 2)}
                </pre>
              </div>

              <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
                <h3 className="text-lg font-medium text-slate-900">最近提交结果</h3>
                {submitted ? (
                  <pre className="mt-4 overflow-auto rounded-xl bg-slate-950 p-4 text-xs leading-6 text-slate-100">
                    {JSON.stringify(submitted, null, 2)}
                  </pre>
                ) : (
                  <p className="mt-4 text-sm text-slate-500">
                    还没有提交，提交后会在这里展示实例数据。
                  </p>
                )}
              </div>
            </aside>
          </div>
        )}
      </div>
    </Shell>
  );
}

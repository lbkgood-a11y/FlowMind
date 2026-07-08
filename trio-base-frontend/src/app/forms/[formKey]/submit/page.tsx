"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { useRouter, useParams } from "next/navigation";
import { Shell } from "@/components/Shell";
import { lowcodeApi, type FormDefinition, type FormInstance } from "@/lib/lowcode";
import { Card, PageHeader } from "@/components/ui";

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
        (detail.fields || []).map((field) => [field.fieldKey, field.defaultValue || ""]),
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
    [form],
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
      <PageHeader
        breadcrumb="Runtime Preview"
        title="表单提交测试"
        subtitle="用运行时视角验证表单定义是否可真正收集结构化数据。"
        actions={
          <>
            <Link
              href="/forms"
              className="rounded border border-border px-4 py-2 text-sm text-fg-secondary hover:bg-surface"
            >
              返回列表
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

      {loading ? (
        <Card>
          <div className="py-10 text-sm text-fg-tertiary">加载中...</div>
        </Card>
      ) : !form ? (
        <Card>
          <div className="py-10 text-sm text-fg-tertiary">未找到表单。</div>
        </Card>
      ) : (
        <div className="grid gap-6 lg:grid-cols-[1.1fr_0.9fr]">
          <Card>
            <h2 className="text-base font-medium text-fg-primary">{form.name}</h2>
            <p className="mt-1 text-sm text-fg-secondary">
              {form.description || "暂无描述"}
            </p>
            <p className="mt-2 text-xs text-fg-tertiary">
              表单 Key：
              <span className="font-mono">{form.formKey}</span>
            </p>

            <form onSubmit={handleSubmit} className="mt-6 space-y-4">
              {orderedFields.map((field) => (
                <label key={field.fieldKey} className="block">
                  <span className="mb-1 block text-sm font-medium text-fg-primary">
                    {field.label}
                    {field.required ? (
                      <span className="ml-1 text-danger-fg">*</span>
                    ) : null}
                  </span>

                  {field.fieldType === "textarea" ? (
                    <textarea
                      value={values[field.fieldKey] || ""}
                      onChange={(e) =>
                        setValues((current) => ({
                          ...current,
                          [field.fieldKey]: e.target.value,
                        }))
                      }
                      rows={4}
                      required={Boolean(field.required)}
                      placeholder={field.placeholder || ""}
                      className="w-full rounded border border-border px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
                    />
                  ) : (
                    <input
                      type={field.fieldType === "number" ? "number" : "text"}
                      value={values[field.fieldKey] || ""}
                      onChange={(e) =>
                        setValues((current) => ({
                          ...current,
                          [field.fieldKey]: e.target.value,
                        }))
                      }
                      required={Boolean(field.required)}
                      placeholder={field.placeholder || ""}
                      className="w-full rounded border border-border px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
                    />
                  )}
                </label>
              ))}

              <button
                type="submit"
                disabled={submitting}
                className="rounded bg-fg-primary px-5 py-2.5 text-sm font-medium text-white hover:opacity-90 disabled:opacity-50"
              >
                {submitting ? "提交中..." : "提交表单"}
              </button>
            </form>
          </Card>

          <aside className="space-y-6">
            <Card title="表单元数据">
              <pre className="overflow-auto rounded bg-fg-primary p-4 text-xs leading-6 text-slate-100">
                {JSON.stringify(form, null, 2)}
              </pre>
            </Card>

            <Card title="最近提交结果">
              {submitted ? (
                <pre className="overflow-auto rounded bg-fg-primary p-4 text-xs leading-6 text-slate-100">
                  {JSON.stringify(submitted, null, 2)}
                </pre>
              ) : (
                <p className="text-sm text-fg-tertiary">
                  还没有提交，提交后会在这里展示实例数据。
                </p>
              )}
            </Card>
          </aside>
        </div>
      )}
    </Shell>
  );
}

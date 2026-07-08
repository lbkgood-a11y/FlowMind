"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Shell } from "@/components/Shell";
import { lowcodeApi, type FormFieldSchema } from "@/lib/lowcode";

const FIELD_TYPES = ["text", "textarea", "number", "select", "date"];

function createEmptyField(sortOrder: number): FormFieldSchema {
  return {
    fieldKey: "",
    label: "",
    fieldType: "text",
    required: false,
    defaultValue: "",
    placeholder: "",
    optionsJson: "",
    sortOrder,
  };
}

export default function NewFormPage() {
  const router = useRouter();
  const [formKey, setFormKey] = useState("");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [fields, setFields] = useState<FormFieldSchema[]>([createEmptyField(1)]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const schemaPreview = useMemo(
    () =>
      JSON.stringify(
        {
          formKey,
          title: name,
          properties: fields.reduce<Record<string, unknown>>((acc, field) => {
            if (field.fieldKey) {
              acc[field.fieldKey] = {
                title: field.label,
                type: field.fieldType === "number" ? "number" : "string",
                placeholder: field.placeholder,
              };
            }
            return acc;
          }, {}),
          required: fields.filter((field) => field.required).map((field) => field.fieldKey),
        },
        null,
        2
      ),
    [fields, formKey, name]
  );

  function updateField(index: number, patch: Partial<FormFieldSchema>) {
    setFields((current) =>
      current.map((field, fieldIndex) => (fieldIndex === index ? { ...field, ...patch } : field))
    );
  }

  function addField() {
    setFields((current) => [...current, createEmptyField(current.length + 1)]);
  }

  function removeField(index: number) {
    setFields((current) => current.filter((_, fieldIndex) => fieldIndex !== index));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError("");

    if (!formKey || !name) {
      setError("表单 Key 和名称不能为空");
      return;
    }

    if (fields.some((field) => !field.fieldKey || !field.label)) {
      setError("所有字段都需要填写字段 Key 和标签");
      return;
    }

    setLoading(true);
    try {
      const created = await lowcodeApi.createForm({
        formKey,
        name,
        description,
        schemaJson: schemaPreview,
        uiSchemaJson: JSON.stringify({ layout: "single-column" }),
        fields,
      });
      router.push(`/forms/${created.formKey}/submit?id=${created.id}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : "创建表单失败");
    } finally {
      setLoading(false);
    }
  }

  return (
    <Shell>
      <div className="grid h-full gap-6 lg:grid-cols-[1.3fr_0.9fr]">
        <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <div className="flex items-start justify-between gap-4">
            <div>
              <p className="text-sm uppercase tracking-[0.24em] text-slate-500">Form Builder</p>
              <h1 className="mt-1 text-3xl font-semibold text-slate-900">新建表单</h1>
            </div>
            <Link
              href="/forms"
              className="rounded-md border border-slate-300 px-4 py-2 text-sm text-slate-700 hover:bg-slate-50"
            >
              返回列表
            </Link>
          </div>

          <form onSubmit={handleSubmit} className="mt-8 space-y-6">
            <div className="grid gap-4 md:grid-cols-2">
              <label className="block">
                <span className="mb-1 block text-sm font-medium text-slate-700">表单 Key</span>
                <input
                  value={formKey}
                  onChange={(e) => setFormKey(e.target.value)}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
                  placeholder="expense-approval"
                />
              </label>
              <label className="block">
                <span className="mb-1 block text-sm font-medium text-slate-700">表单名称</span>
                <input
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
                  placeholder="费用报销申请"
                />
              </label>
            </div>

            <label className="block">
              <span className="mb-1 block text-sm font-medium text-slate-700">描述</span>
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                rows={3}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
                placeholder="这个表单用于什么场景"
              />
            </label>

            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-lg font-medium text-slate-900">字段配置</h2>
                <button
                  type="button"
                  onClick={addField}
                  className="rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-700 hover:bg-slate-50"
                >
                  添加字段
                </button>
              </div>

              {fields.map((field, index) => (
                <div key={`${field.fieldKey}-${index}`} className="rounded-xl border border-slate-200 bg-slate-50 p-4">
                  <div className="grid gap-4 md:grid-cols-2">
                    <label className="block">
                      <span className="mb-1 block text-sm font-medium text-slate-700">字段 Key</span>
                      <input
                        value={field.fieldKey}
                        onChange={(e) => updateField(index, { fieldKey: e.target.value })}
                        className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
                        placeholder="amount"
                      />
                    </label>
                    <label className="block">
                      <span className="mb-1 block text-sm font-medium text-slate-700">标签</span>
                      <input
                        value={field.label}
                        onChange={(e) => updateField(index, { label: e.target.value })}
                        className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
                        placeholder="报销金额"
                      />
                    </label>
                    <label className="block">
                      <span className="mb-1 block text-sm font-medium text-slate-700">类型</span>
                      <select
                        value={field.fieldType}
                        onChange={(e) => updateField(index, { fieldType: e.target.value })}
                        className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
                      >
                        {FIELD_TYPES.map((type) => (
                          <option key={type} value={type}>
                            {type}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label className="block">
                      <span className="mb-1 block text-sm font-medium text-slate-700">占位提示</span>
                      <input
                        value={field.placeholder || ""}
                        onChange={(e) => updateField(index, { placeholder: e.target.value })}
                        className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
                        placeholder="请输入内容"
                      />
                    </label>
                  </div>

                  <div className="mt-4 flex items-center justify-between">
                    <label className="flex items-center gap-2 text-sm text-slate-700">
                      <input
                        type="checkbox"
                        checked={Boolean(field.required)}
                        onChange={(e) => updateField(index, { required: e.target.checked })}
                      />
                      必填字段
                    </label>
                    {fields.length > 1 && (
                      <button
                        type="button"
                        onClick={() => removeField(index)}
                        className="text-sm text-red-600 hover:text-red-700"
                      >
                        删除字段
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>

            {error && (
              <div className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                {error}
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="rounded-md bg-slate-900 px-5 py-2.5 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50"
            >
              {loading ? "创建中..." : "创建表单"}
            </button>
          </form>
        </section>

        <aside className="rounded-2xl border border-slate-200 bg-slate-950 p-6 text-slate-100 shadow-sm">
          <p className="text-sm uppercase tracking-[0.24em] text-slate-400">Schema Preview</p>
          <pre className="mt-4 overflow-auto rounded-xl bg-slate-900 p-4 text-xs leading-6 text-slate-200">
            {schemaPreview}
          </pre>
        </aside>
      </div>
    </Shell>
  );
}

"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Shell } from "@/components/Shell";
import { lowcodeApi, type FormFieldSchema } from "@/lib/lowcode";
import { Card, PageHeader } from "@/components/ui";

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
          required: fields
            .filter((field) => field.required)
            .map((field) => field.fieldKey),
        },
        null,
        2,
      ),
    [fields, formKey, name],
  );

  function updateField(index: number, patch: Partial<FormFieldSchema>) {
    setFields((current) =>
      current.map((field, fieldIndex) =>
        fieldIndex === index ? { ...field, ...patch } : field,
      ),
    );
  }

  function addField() {
    setFields((current) => [...current, createEmptyField(current.length + 1)]);
  }

  function removeField(index: number) {
    setFields((current) =>
      current.filter((_, fieldIndex) => fieldIndex !== index),
    );
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
      <PageHeader
        breadcrumb="Form Builder"
        title="新建表单"
        actions={
          <Link
            href="/forms"
            className="rounded border border-border px-4 py-2 text-sm text-fg-secondary hover:bg-surface"
          >
            返回列表
          </Link>
        }
      />

      <div className="grid gap-6 lg:grid-cols-[1.3fr_0.9fr]">
        <Card>
          <form onSubmit={handleSubmit} className="space-y-6">
            <div className="grid gap-4 md:grid-cols-2">
              <label className="block">
                <span className="mb-1 block text-sm font-medium text-fg-primary">
                  表单 Key
                </span>
                <input
                  value={formKey}
                  onChange={(e) => setFormKey(e.target.value)}
                  className="w-full rounded border border-border px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
                  placeholder="expense-approval"
                />
              </label>
              <label className="block">
                <span className="mb-1 block text-sm font-medium text-fg-primary">
                  表单名称
                </span>
                <input
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className="w-full rounded border border-border px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
                  placeholder="费用报销申请"
                />
              </label>
            </div>

            <label className="block">
              <span className="mb-1 block text-sm font-medium text-fg-primary">
                描述
              </span>
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                rows={3}
                className="w-full rounded border border-border px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
                placeholder="这个表单用于什么场景"
              />
            </label>

            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-base font-medium text-fg-primary">
                  字段配置
                </h2>
                <button
                  type="button"
                  onClick={addField}
                  className="rounded border border-border px-3 py-1.5 text-sm text-fg-secondary hover:bg-surface"
                >
                  添加字段
                </button>
              </div>

              {fields.map((field, index) => (
                <div
                  key={`${field.fieldKey}-${index}`}
                  className="rounded border border-border bg-surface p-4"
                >
                  <div className="grid gap-4 md:grid-cols-2">
                    <label className="block">
                      <span className="mb-1 block text-sm font-medium text-fg-primary">
                        字段 Key
                      </span>
                      <input
                        value={field.fieldKey}
                        onChange={(e) =>
                          updateField(index, { fieldKey: e.target.value })
                        }
                        className="w-full rounded border border-border bg-white px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
                        placeholder="amount"
                      />
                    </label>
                    <label className="block">
                      <span className="mb-1 block text-sm font-medium text-fg-primary">
                        标签
                      </span>
                      <input
                        value={field.label}
                        onChange={(e) =>
                          updateField(index, { label: e.target.value })
                        }
                        className="w-full rounded border border-border bg-white px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
                        placeholder="报销金额"
                      />
                    </label>
                    <label className="block">
                      <span className="mb-1 block text-sm font-medium text-fg-primary">
                        类型
                      </span>
                      <select
                        value={field.fieldType}
                        onChange={(e) =>
                          updateField(index, { fieldType: e.target.value })
                        }
                        className="w-full rounded border border-border bg-white px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
                      >
                        {FIELD_TYPES.map((type) => (
                          <option key={type} value={type}>
                            {type}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label className="block">
                      <span className="mb-1 block text-sm font-medium text-fg-primary">
                        占位提示
                      </span>
                      <input
                        value={field.placeholder || ""}
                        onChange={(e) =>
                          updateField(index, { placeholder: e.target.value })
                        }
                        className="w-full rounded border border-border bg-white px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
                        placeholder="请输入内容"
                      />
                    </label>
                  </div>

                  <div className="mt-4 flex items-center justify-between">
                    <label className="flex items-center gap-2 text-sm text-fg-secondary">
                      <input
                        type="checkbox"
                        checked={Boolean(field.required)}
                        onChange={(e) =>
                          updateField(index, { required: e.target.checked })
                        }
                      />
                      必填字段
                    </label>
                    {fields.length > 1 && (
                      <button
                        type="button"
                        onClick={() => removeField(index)}
                        className="text-sm text-danger-fg hover:text-danger-fg/80"
                      >
                        删除字段
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>

            {error && (
              <div className="rounded border border-danger-fg/30 bg-danger-bg px-4 py-3 text-sm text-danger-fg">
                {error}
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="rounded bg-fg-primary px-5 py-2.5 text-sm font-medium text-white hover:opacity-90 disabled:opacity-50"
            >
              {loading ? "创建中..." : "创建表单"}
            </button>
          </form>
        </Card>

        <aside className="rounded border border-border bg-fg-primary px-4 py-4 text-slate-100">
          <p className="text-xs uppercase tracking-[0.24em] text-fg-secondary">
            Schema Preview
          </p>
          <pre className="mt-4 overflow-auto rounded bg-black/20 p-4 text-xs leading-6 text-slate-200">
            {schemaPreview}
          </pre>
        </aside>
      </div>
    </Shell>
  );
}

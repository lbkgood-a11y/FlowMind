"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";

import { lowcodeApi, type FormFieldSchema } from "@/lib/lowcode";
import { Button } from "@/components/ui/button";
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
  const { messages } = useI18n();
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
      setError(messages.pages.formBuilder.validationMissingMeta);
      return;
    }

    if (fields.some((field) => !field.fieldKey || !field.label)) {
      setError(messages.pages.formBuilder.validationMissingField);
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
      setError(e instanceof Error ? e.message : messages.pages.formBuilder.createFailed);
    } finally {
      setLoading(false);
    }
  }

  return (
    <AppPage
      topbarActions={(
        <Link href="/forms">
          <Button variant="outline" size="sm">返回列表</Button>
        </Link>
      )}
    >
      <PageHeader
        breadcrumb={messages.pages.formBuilder.breadcrumb}
        title={messages.pages.formBuilder.title}
        actions={
          <Button type="submit" form="new-form-builder" disabled={loading}>
            {loading ? messages.pages.formBuilder.createBusy : messages.pages.formBuilder.create}
          </Button>
        }
      />

      <div className="grid gap-6 lg:grid-cols-[1.3fr_0.9fr]">
        <Card>
          <form id="new-form-builder" onSubmit={handleSubmit} className="space-y-6">
            <div className="grid gap-4 md:grid-cols-2">
              <div className="grid gap-2">
                <Label htmlFor="formKey">{messages.pages.formBuilder.formKey}</Label>
                <Input
                  id="formKey"
                  value={formKey}
                  onChange={(e) => setFormKey(e.target.value)}
                  placeholder="expense-approval"
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="formName">{messages.pages.formBuilder.formName}</Label>
                <Input
                  id="formName"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="费用报销申请"
                />
              </div>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="formDesc">{messages.pages.formBuilder.formDescription}</Label>
              <textarea
                id="formDesc"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                rows={3}
                className="w-full rounded-lg border border-input bg-transparent px-3 py-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 placeholder:text-muted-foreground"
                placeholder={messages.pages.formBuilder.formDescriptionPlaceholder}
              />
            </div>

            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-base font-medium text-foreground">
                  {messages.pages.formBuilder.sectionTitle}
                </h2>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={addField}
                >
                  {messages.pages.formBuilder.addField}
                </Button>
              </div>

              {fields.map((field, index) => (
                <div
                  key={`${field.fieldKey}-${index}`}
                  className="rounded-lg border border-border bg-muted/20 p-4"
                >
                  <div className="grid gap-4 md:grid-cols-2">
                    <div className="grid gap-2">
                      <Label className="text-xs text-muted-foreground">{messages.pages.formBuilder.fieldKey}</Label>
                      <Input
                        value={field.fieldKey}
                        onChange={(e) => updateField(index, { fieldKey: e.target.value })}
                        placeholder="amount"
                        className="bg-white"
                      />
                    </div>
                    <div className="grid gap-2">
                      <Label className="text-xs text-muted-foreground">{messages.pages.formBuilder.fieldLabel}</Label>
                      <Input
                        value={field.label}
                        onChange={(e) => updateField(index, { label: e.target.value })}
                        placeholder="报销金额"
                        className="bg-white"
                      />
                    </div>
                    <div className="grid gap-2">
                      <Label className="text-xs text-muted-foreground">{messages.pages.formBuilder.fieldType}</Label>
                      <Select
                        value={field.fieldType}
                        onValueChange={(v) => updateField(index, { fieldType: v ?? "text" })}
                      >
                        <SelectTrigger className="bg-white">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {FIELD_TYPES.map((type) => (
                            <SelectItem key={type} value={type}>{type}</SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>
                    <div className="grid gap-2">
                      <Label className="text-xs text-muted-foreground">{messages.pages.formBuilder.placeholder}</Label>
                      <Input
                        value={field.placeholder || ""}
                        onChange={(e) => updateField(index, { placeholder: e.target.value })}
                        placeholder="请输入内容"
                        className="bg-white"
                      />
                    </div>
                  </div>

                  <div className="mt-4 flex items-center justify-between">
                    <label className="flex items-center gap-2 text-sm text-muted-foreground">
                      <input
                        type="checkbox"
                        checked={Boolean(field.required)}
                        onChange={(e) => updateField(index, { required: e.target.checked })}
                      />
                      {messages.pages.formBuilder.required}
                    </label>
                    {fields.length > 1 && (
                      <Button
                        type="button"
                        variant="ghost"
                        size="xs"
                        className="text-destructive hover:text-destructive/80"
                        onClick={() => removeField(index)}
                      >
                        {messages.pages.formBuilder.removeField}
                      </Button>
                    )}
                  </div>
                </div>
              ))}
            </div>

            {error && (
              <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">{error}</div>
            )}
          </form>
        </Card>

        <Card title={messages.pages.formBuilder.schemaPreview} className="overflow-hidden">
          <pre className="overflow-auto rounded-lg bg-slate-950 p-4 text-xs leading-6 text-slate-200">
            {schemaPreview}
          </pre>
        </Card>
      </div>
    </AppPage>
  );
}

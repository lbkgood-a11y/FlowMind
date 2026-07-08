"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { useRouter, useParams } from "next/navigation";

import { lowcodeApi, type FormDefinition, type FormInstance } from "@/lib/lowcode";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, PageHeader } from "@/components/ui";
import { AppPage } from "@/components/layout/app-page";
import { useI18n } from "@/lib/i18n";

export default function FormSubmitPage() {
  const router = useRouter();
  const { messages } = useI18n();
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
      setError(messages.pages.formSubmit.missingId);
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
      setError(e instanceof Error ? e.message : messages.pages.formSubmit.loadFailed);
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
      setError(e instanceof Error ? e.message : messages.pages.formSubmit.submitFailed);
    } finally {
      setSubmitting(false);
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
        breadcrumb={messages.pages.formSubmit.breadcrumb}
        title={messages.pages.formSubmit.title}
        subtitle={messages.pages.formSubmit.subtitle}
        actions={
          <Link href="/forms/new">
            <Button variant="default" size="sm">{messages.common.newForm}</Button>
          </Link>
        }
      />

      {error && (
        <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">{error}</div>
      )}

      {loading ? (
        <Card>
          <div className="py-10 text-sm text-muted-foreground">{messages.common.loading}</div>
        </Card>
      ) : !form ? (
        <Card>
          <div className="py-10 text-sm text-muted-foreground">{messages.pages.formSubmit.missingForm}</div>
        </Card>
      ) : (
        <div className="grid gap-6 lg:grid-cols-[1.1fr_0.9fr]">
          <Card>
            <h2 className="text-base font-medium text-foreground">{form.name}</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              {form.description || messages.pages.formSubmit.noDescription}
            </p>
            <p className="mt-2 text-xs text-muted-foreground">
              {messages.pages.formSubmit.formKey}:
              <span className="font-mono">{form.formKey}</span>
            </p>

            <form onSubmit={handleSubmit} className="mt-6 space-y-4">
              {orderedFields.map((field) => (
                <div key={field.fieldKey} className="grid gap-2">
                  <Label>
                    {field.label}
                    {field.required ? (
                      <span className="ml-1 text-destructive">*</span>
                    ) : null}
                  </Label>

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
                      className="w-full rounded-lg border border-input bg-transparent px-3 py-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 placeholder:text-muted-foreground"
                    />
                  ) : (
                    <Input
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
                    />
                  )}
                </div>
              ))}

              <Button type="submit" disabled={submitting}>
                {submitting ? messages.pages.formSubmit.submitBusy : messages.pages.formSubmit.submit}
              </Button>
            </form>
          </Card>

          <aside className="space-y-6">
            <Card title={messages.pages.formSubmit.metadata}>
              <pre className="overflow-auto rounded-lg bg-slate-950 p-4 text-xs leading-6 text-slate-200">
                {JSON.stringify(form, null, 2)}
              </pre>
            </Card>

            <Card title={messages.pages.formSubmit.latestResult}>
              {submitted ? (
                <pre className="overflow-auto rounded-lg bg-slate-950 p-4 text-xs leading-6 text-slate-200">
                  {JSON.stringify(submitted, null, 2)}
                </pre>
              ) : (
                <p className="text-sm text-muted-foreground">
                  {messages.pages.formSubmit.latestResultEmpty}
                </p>
              )}
            </Card>
          </aside>
        </div>
      )}
    </AppPage>
  );
}

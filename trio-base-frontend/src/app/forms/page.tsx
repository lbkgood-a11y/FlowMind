"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { lowcodeApi, type FormDefinition } from "@/lib/lowcode";

import { Button } from "@/components/ui/button";
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
import { AppPage } from "@/components/layout/app-page";
import { useI18n } from "@/lib/i18n";

export default function FormsPage() {
  const router = useRouter();
  const { messages } = useI18n();
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
    <AppPage
      topbarActions={(
        <Link href="/">
          <Button variant="outline" size="sm">返回首页</Button>
        </Link>
      )}
    >
      <PageHeader
        breadcrumb={messages.pages.forms.breadcrumb}
        title={messages.pages.forms.title}
        subtitle={messages.pages.forms.subtitle}
        actions={
          <Link href="/forms/new">
            <Button variant="default" size="sm">{messages.common.newForm}</Button>
          </Link>
        }
      />

      {error && (
        <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">{error}</div>
      )}

      <Card title={messages.pages.forms.tableTitle}>
        {loading ? (
          <div className="py-10 text-sm text-muted-foreground">{messages.common.loading}</div>
        ) : forms.length === 0 ? (
          <div className="py-10 text-sm text-muted-foreground">
            {messages.pages.forms.empty}
          </div>
        ) : (
          <Table>
            <THead>
              <tr>
                <Th>{messages.pages.forms.columns.name}</Th>
                <Th>{messages.pages.forms.columns.key}</Th>
                <Th>{messages.pages.forms.columns.status}</Th>
                <Th>{messages.pages.forms.columns.version}</Th>
                <Th>{messages.pages.forms.columns.creator}</Th>
                <Th>{messages.pages.forms.columns.actions}</Th>
              </tr>
            </THead>
            <tbody>
                {forms.map((form) => (
                  <Tr key={form.id}>
                    <Td>
                    <div className="font-medium text-foreground">{form.name}</div>
                    <div className="mt-0.5 text-xs text-muted-foreground">
                        {form.description || messages.pages.forms.descriptionFallback}
                      </div>
                    </Td>
                  <Td className="font-mono text-xs text-muted-foreground">
                    {form.formKey}
                  </Td>
                  <Td>
                    <StatusBadge
                      status={form.status === "PUBLISHED" ? "success" : "warning"}
                      label={form.status}
                    />
                  </Td>
                  <Td className="text-foreground">{form.version}</Td>
                  <Td className="text-muted-foreground">{form.createdBy || "-"}</Td>
                  <Td>
                    <div className="flex flex-wrap gap-2">
                      <Link href={`/forms/${form.formKey}/submit?id=${form.id}`}>
                        <Button variant="outline" size="xs">{messages.pages.forms.submitTest}</Button>
                      </Link>
                      {form.status !== "PUBLISHED" && (
                        <Button
                          variant="default"
                          size="xs"
                          onClick={() => void handlePublish(form.id)}
                          disabled={publishingId === form.id}
                        >
                          {publishingId === form.id ? messages.pages.forms.publishing : messages.pages.forms.publish}
                        </Button>
                      )}
                    </div>
                  </Td>
                </Tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>
    </AppPage>
  );
}

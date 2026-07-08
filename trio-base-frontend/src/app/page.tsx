"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Shell } from "@/components/Shell";
import { Card, PageHeader } from "@/components/ui";

export default function Home() {
  const router = useRouter();
  const [authenticated, setAuthenticated] = useState(false);

  useEffect(() => {
    const token = localStorage.getItem("accessToken");
    if (!token) {
      router.replace("/login");
    } else {
      setAuthenticated(true);
    }
  }, [router]);

  if (!authenticated) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <p className="text-fg-tertiary">加载中...</p>
      </div>
    );
  }

  return (
    <Shell>
      <PageHeader
        breadcrumb="TrioBase Console"
        title="流程、数据、AI 三核驱动"
        subtitle="当前版本先把统一表单能力做成一个可见的入口，后续会继续把流程引擎和数据分析能力接进来。"
      />

      <div className="grid gap-4 md:grid-cols-2">
        <Link href="/forms">
          <Card className="transition hover:-translate-y-0.5 hover:border-brand/30">
            <p className="text-xs uppercase tracking-[0.24em] text-fg-tertiary">
              Form Studio
            </p>
            <h2 className="mt-2 text-base font-medium text-fg-primary">表单配置台</h2>
            <p className="mt-1 text-sm leading-6 text-fg-secondary">
              创建统一表单定义、发布表单、提交实例，为流程编排和数据沉淀提供结构化入口。
            </p>
          </Card>
        </Link>

        <Link href="/admin/users">
          <Card className="transition hover:-translate-y-0.5 hover:border-brand/30">
            <p className="text-xs uppercase tracking-[0.24em] text-fg-tertiary">
              Access Control
            </p>
            <h2 className="mt-2 text-base font-medium text-fg-primary">权限管理台</h2>
            <p className="mt-1 text-sm leading-6 text-fg-secondary">
              维护用户、角色、菜单与权限目录，为流程和表单运行时提供安全边界。
            </p>
          </Card>
        </Link>
      </div>

      <Card title="AI 助手">
        <p className="text-sm text-fg-secondary">
          保留右侧智能助手，后续会逐步接入 AI 辅助填单、流程发起建议和数据分析解释。
        </p>
      </Card>
    </Shell>
  );
}

"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ChatPanel } from "@/components/ChatPanel";
import { Shell } from "@/components/Shell";

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
        <p className="text-slate-500">加载中...</p>
      </div>
    );
  }

  return (
    <Shell>
      <div className="flex h-full gap-4">
        <main className="flex-1 space-y-6">
          <section className="rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
            <p className="text-sm uppercase tracking-[0.24em] text-slate-500">
              TrioBase Console
            </p>
            <h1 className="mt-2 text-3xl font-semibold text-slate-900">
              流程、数据、AI 三核驱动
            </h1>
            <p className="mt-3 max-w-3xl text-sm leading-7 text-slate-600">
              当前版本先把统一表单能力做成一个可见的入口，后续会继续把流程引擎和数据分析能力接进来。
            </p>
          </section>

          <section className="grid gap-4 md:grid-cols-2">
            <Link
              href="/forms"
              className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition hover:-translate-y-0.5 hover:border-slate-300"
            >
              <p className="text-sm uppercase tracking-[0.24em] text-slate-500">
                Form Studio
              </p>
              <h2 className="mt-3 text-xl font-semibold text-slate-900">表单配置台</h2>
              <p className="mt-2 text-sm leading-6 text-slate-600">
                创建统一表单定义、发布表单、提交实例，为流程编排和数据沉淀提供结构化入口。
              </p>
            </Link>

            <Link
              href="/admin/users"
              className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition hover:-translate-y-0.5 hover:border-slate-300"
            >
              <p className="text-sm uppercase tracking-[0.24em] text-slate-500">
                Access Control
              </p>
              <h2 className="mt-3 text-xl font-semibold text-slate-900">权限管理台</h2>
              <p className="mt-2 text-sm leading-6 text-slate-600">
                维护用户、角色、菜单与权限目录，为流程和表单运行时提供安全边界。
              </p>
            </Link>
          </section>

          <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
            <h2 className="text-lg font-medium text-slate-900">AI 助手</h2>
            <p className="mt-2 text-sm text-slate-600">
              保留右侧智能助手，后续会逐步接入 AI 辅助填单、流程发起建议和数据分析解释。
            </p>
          </section>
        </main>
        <aside className="w-96">
          <ChatPanel />
        </aside>
      </div>
    </Shell>
  );
}

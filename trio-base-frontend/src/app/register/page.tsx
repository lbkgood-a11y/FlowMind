"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Bot, Database, GitBranch, ShieldCheck } from "lucide-react";
import { useI18n } from "@/lib/i18n";
import { useAppStore } from "@/stores/app-store";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

const PWD_RULES = [/.{8,}/, /[a-z]/, /[A-Z]/, /\d/];

export default function RegisterPage() {
  const router = useRouter();
  const { messages } = useI18n();
  const setMode = useAppStore((s) => s.setMode);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [email, setEmail] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const pwdOk = PWD_RULES.every((rule) => rule.test(password));
  const confirmOk = password === confirm && confirm.length > 0;

  async function handleRegister(e: React.FormEvent) {
    e.preventDefault();
    setError("");

    if (!pwdOk) {
      setError(messages.auth.passwordRuleFailed);
      return;
    }
    if (!confirmOk) {
      setError(messages.auth.passwordMismatch);
      return;
    }

    setLoading(true);
    try {
      const params = new URLSearchParams({ username, password });
      if (email) params.append("email", email);

      const res = await fetch(`/api/v1/auth/register?${params.toString()}`, {
        method: "POST",
      });

      if (!res.ok) {
        const data = await res.json();
        setError(data.message || messages.auth.registerFailed);
        return;
      }

      const { data: result } = await res.json();
      localStorage.setItem("accessToken", result.accessToken);
      localStorage.setItem("refreshToken", result.refreshToken);
      localStorage.setItem(
        "user",
        JSON.stringify({
          userId: result.userId,
          username: result.username,
          roles: result.roles,
        })
      );

      setMode("gui");
      router.push("/");
    } catch {
      setError(messages.auth.networkError);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="grid min-h-svh bg-background lg:grid-cols-2">
      <section className="relative hidden overflow-hidden border-r bg-sidebar text-sidebar-foreground lg:flex">
        <div className="absolute inset-0 bg-[linear-gradient(to_right,var(--border)_1px,transparent_1px),linear-gradient(to_bottom,var(--border)_1px,transparent_1px)] bg-[size:48px_48px] opacity-40" />
        <div className="relative flex min-h-svh w-full flex-col justify-between p-10">
          <Link href="/" className="flex items-center gap-3 font-semibold">
            <span className="flex size-9 items-center justify-center rounded-lg bg-sidebar-primary text-sidebar-primary-foreground">
              <Bot className="size-5" />
            </span>
            <span>TrioBase</span>
          </Link>

          <div className="max-w-xl space-y-6">
            <div className="space-y-3">
              <p className="text-sm font-medium text-muted-foreground">
                {messages.auth.eyebrow}
              </p>
              <h1 className="text-4xl font-semibold leading-tight">
                {messages.auth.registerHeroTitle}
              </h1>
              <p className="text-base leading-7 text-muted-foreground">
                {messages.auth.registerHeroDescription}
              </p>
            </div>

            <div className="grid gap-3 text-sm sm:grid-cols-3">
              <div className="rounded-lg border bg-background/50 p-3">
                <GitBranch className="mb-3 size-4 text-muted-foreground" />
                <p className="font-medium">{messages.auth.featureUnifiedFlow}</p>
              </div>
              <div className="rounded-lg border bg-background/50 p-3">
                <Database className="mb-3 size-4 text-muted-foreground" />
                <p className="font-medium">{messages.auth.featureUnifiedData}</p>
              </div>
              <div className="rounded-lg border bg-background/50 p-3">
                <ShieldCheck className="mb-3 size-4 text-muted-foreground" />
                <p className="font-medium">{messages.auth.featureUnifiedSecurity}</p>
              </div>
            </div>
          </div>

          <p className="text-xs text-muted-foreground">
            {messages.auth.registerNote}
          </p>
        </div>
      </section>

      <main className="flex min-h-svh items-center justify-center px-4 py-10 sm:px-6">
        <div className="w-full max-w-[22rem] space-y-6 sm:max-w-sm">
          <div className="space-y-2 text-center">
            <div className="mx-auto flex size-10 items-center justify-center rounded-lg bg-primary text-primary-foreground lg:hidden">
              <Bot className="size-5" />
            </div>
            <h1 className="text-2xl font-semibold tracking-tight">{messages.auth.registerTitle}</h1>
            <p className="text-sm text-muted-foreground">
              {messages.auth.registerSubtitle}
            </p>
          </div>

        <form onSubmit={handleRegister} className="space-y-4">
          <div className="grid gap-2">
            <Label htmlFor="username">{messages.common.username}</Label>
            <Input
              id="username"
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="请输入用户名"
              required
            />
          </div>

          <div className="grid gap-2">
            <Label htmlFor="email">
              {messages.common.email} <span className="text-muted-foreground">({messages.auth.optional})</span>
            </Label>
            <Input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="name@company.com"
            />
          </div>

          <div className="grid gap-2">
            <Label htmlFor="password">{messages.common.password}</Label>
            <Input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="请输入密码"
              required
            />
            <ul className="space-y-0.5">
              {messages.auth.passwordRules.map((label, index) => {
                const ok = PWD_RULES[index].test(password);
                return (
                  <li
                    key={label}
                    className={`text-xs ${ok ? "text-success-fg" : "text-muted-foreground"}`}
                  >
                    {ok ? "✓" : "○"} {label}
                  </li>
                );
              })}
            </ul>
          </div>

          <div className="grid gap-2">
            <Label htmlFor="confirm">{messages.common.confirmPassword}</Label>
            <Input
              id="confirm"
              type="password"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              placeholder="再次输入密码"
              required
              aria-invalid={confirm.length > 0 && !confirmOk ? true : undefined}
            />
            {confirm.length > 0 && !confirmOk && (
              <p className="text-xs text-destructive">{messages.auth.passwordMismatch}</p>
            )}
          </div>

          {error && <p className="text-sm text-destructive">{error}</p>}

          <Button type="submit" disabled={loading} className="w-full">
            {loading ? messages.auth.registerBusy : messages.auth.submitRegister}
          </Button>
        </form>

        <p className="mt-4 text-center text-sm text-muted-foreground">
          {messages.auth.hasAccount}
          <Link href="/login" className="ml-1 font-medium text-primary hover:underline">
            {messages.auth.goLogin}
          </Link>
        </p>
      </div>
      </main>
    </div>
  );
}

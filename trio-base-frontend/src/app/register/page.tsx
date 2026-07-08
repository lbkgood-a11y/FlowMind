"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useAppStore } from "@/stores/app-store";

const PWD_RULES = [
  { re: /.{8,}/, label: "至少 8 位" },
  { re: /[a-z]/, label: "包含小写字母" },
  { re: /[A-Z]/, label: "包含大写字母" },
  { re: /\d/, label: "包含数字" },
];

export default function RegisterPage() {
  const router = useRouter();
  const setMode = useAppStore((s) => s.setMode);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [email, setEmail] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const pwdOk = PWD_RULES.every((r) => r.re.test(password));
  const confirmOk = password === confirm && confirm.length > 0;

  async function handleRegister(e: React.FormEvent) {
    e.preventDefault();
    setError("");

    if (!pwdOk) {
      setError("密码不满足强度要求");
      return;
    }
    if (!confirmOk) {
      setError("两次密码不一致");
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
        setError(data.message || "注册失败");
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
      setError("网络错误，请稍后重试");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50">
      <div className="w-full max-w-sm rounded-lg border bg-white p-8 shadow-sm">
        <h1 className="mb-6 text-center text-2xl font-bold text-slate-900">
          注册 TrioBase
        </h1>

        <form onSubmit={handleRegister} className="space-y-4">
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">
              用户名
            </label>
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="w-full rounded-md border px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
              placeholder="请输入用户名"
              required
            />
          </div>

          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">
              邮箱 <span className="text-slate-400">(选填)</span>
            </label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full rounded-md border px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
              placeholder="name@company.com"
            />
          </div>

          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">
              密码
            </label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full rounded-md border px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
              placeholder="请输入密码"
              required
            />
            <ul className="mt-2 space-y-0.5">
              {PWD_RULES.map((rule) => {
                const ok = rule.re.test(password);
                return (
                  <li
                    key={rule.label}
                    className={`text-xs ${ok ? "text-green-600" : "text-slate-400"}`}
                  >
                    {ok ? "✓" : "○"} {rule.label}
                  </li>
                );
              })}
            </ul>
          </div>

          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">
              确认密码
            </label>
            <input
              type="password"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              className={`w-full rounded-md border px-3 py-2 text-sm outline-none focus:ring-1 ${
                confirm.length > 0 && !confirmOk
                  ? "border-red-400 focus:border-red-500 focus:ring-red-500"
                  : "focus:border-blue-500 focus:ring-blue-500"
              }`}
              placeholder="再次输入密码"
              required
            />
            {confirm.length > 0 && !confirmOk && (
              <p className="mt-1 text-xs text-red-500">两次密码不一致</p>
            )}
          </div>

          {error && <p className="text-sm text-red-600">{error}</p>}

          <button
            type="submit"
            disabled={loading}
            className="w-full rounded-md bg-slate-900 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50"
          >
            {loading ? "注册中..." : "注 册"}
          </button>
        </form>

        <p className="mt-4 text-center text-sm text-slate-500">
          已有账号？
          <Link href="/login" className="ml-1 text-blue-600 hover:underline">
            去登录
          </Link>
        </p>
      </div>
    </div>
  );
}

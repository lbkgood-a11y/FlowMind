"use client";

import { useChat } from "ai/react";

/**
 * LUI 智能助手面板 — 铁律 9：使用 Vercel AI SDK 的 useChat 钩子实现原生 SSE 流式渲染。
 * 严禁用传统 HTTP 阻塞请求等待全部结果返回。
 */
export function ChatPanel() {
  const { messages, input, handleInputChange, handleSubmit } = useChat({
    api: "/api/v1/ai/chat",
    headers: {
      Authorization: `Bearer ${typeof window !== "undefined" ? localStorage.getItem("accessToken") || "" : ""}`,
    },
    onError: (e) => {
      if (e.message === "Unauthorized") {
        window.location.href = "/login";
      }
    },
  });

  return (
    <div className="flex h-full flex-col rounded-lg border">
      <div className="border-b px-4 py-2 font-semibold">AI Assistant</div>
      <div className="flex-1 overflow-y-auto p-4 space-y-3">
        {messages.map((m) => (
          <div
            key={m.id}
            className={`rounded-md px-3 py-2 text-sm ${
              m.role === "user"
                ? "bg-primary text-primary-foreground ml-8"
                : "bg-muted mr-8"
            }`}
          >
            {m.content}
          </div>
        ))}
      </div>
      <form onSubmit={handleSubmit} className="border-t p-3">
        <input
          value={input}
          onChange={handleInputChange}
          placeholder="输入指令..."
          className="w-full rounded-md border px-3 py-2 text-sm outline-none"
        />
      </form>
    </div>
  );
}

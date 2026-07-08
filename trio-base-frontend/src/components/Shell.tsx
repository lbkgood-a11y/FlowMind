"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState } from "react";

export function Shell({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(() => new QueryClient());

  return (
    <QueryClientProvider client={queryClient}>
      <div className="mx-auto flex min-h-screen w-full flex-col gap-6 p-6">
        {children}
      </div>
    </QueryClientProvider>
  );
}

import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "TrioBase",
  description: "AI-native enterprise intelligent foundation",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="zh-CN">
      <body className="min-h-screen antialiased">
        {children}
      </body>
    </html>
  );
}

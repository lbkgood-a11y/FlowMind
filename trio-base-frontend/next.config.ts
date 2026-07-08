import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  outputFileTracingRoot: process.cwd(),
  transpilePackages: ["@trio-base/ui", "@trio-base/ai-chat"],
  async rewrites() {
    return [
      // 开发环境绕过网关直连具体服务
      { source: "/api/v1/auth/:path*", destination: "http://localhost:8081/api/v1/auth/:path*" },
      { source: "/api/v1/users/:path*", destination: "http://localhost:8081/api/v1/users/:path*" },
      { source: "/api/v1/roles/:path*", destination: "http://localhost:8081/api/v1/roles/:path*" },
      { source: "/api/v1/permissions/:path*", destination: "http://localhost:8081/api/v1/permissions/:path*" },
      { source: "/api/v1/menus/:path*", destination: "http://localhost:8081/api/v1/menus/:path*" },
      { source: "/api/v1/org/:path*", destination: "http://localhost:8082/api/v1/org/:path*" },
      { source: "/api/v1/forms/:path*", destination: "http://localhost:8085/api/v1/forms/:path*" },
      { source: "/api/v1/form-instances/:path*", destination: "http://localhost:8085/api/v1/form-instances/:path*" },
      // 其余走网关
      { source: "/api/v1/:path*", destination: "http://localhost:8080/api/v1/:path*" },
      { source: "/health", destination: "http://localhost:8080/health" },
    ];
  },
};

export default nextConfig;

#!/usr/bin/env bash
# TrioBase 全栈停止脚本
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "🛑 停止后端 Java 服务..."
pkill -f "service-auth" 2>/dev/null && echo "   Auth 已停止" || true
pkill -f "service-org" 2>/dev/null && echo "   Org 已停止" || true
pkill -f "service-lowcode" 2>/dev/null && echo "   Lowcode 已停止" || true
pkill -f "platform-gateway" 2>/dev/null && echo "   Gateway 已停止" || true
sleep 2

echo "🛑 停止前端 Dev Server..."
pkill -f "next dev" 2>/dev/null && echo "   Frontend 已停止" || true

echo "🛑 停止 Docker 容器..."
docker compose -f "${ROOT_DIR}/docker/docker-compose.yml" stop 2>/dev/null && echo "   Docker 容器已停止" || true

echo ""
echo "✅  全栈已停止"
echo "💡 重启: bash ${ROOT_DIR}/scripts/start-all.sh"

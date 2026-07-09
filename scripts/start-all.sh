#!/usr/bin/env bash
# ============================================================
# TrioBase 全栈快速启动脚本（优化版：并行编译 + 并行启动）
# 使用方式: bash scripts/start-all.sh [--rebuild]
# ============================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="${ROOT_DIR}/logs"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
err()   { echo -e "${RED}[ERR]${NC}   $1"; }

mkdir -p "${LOG_DIR}"

REBUILD=false
if [ "${1:-}" = "--rebuild" ]; then REBUILD=true; fi

SERVICES_LIST="trio-base-services/service-auth,trio-base-services/service-org,trio-base-services/service-lowcode,trio-base-services/service-workflow-engine,trio-base-platform/platform-gateway"

# ── 前置检查 ──────────────────────────────────────────
if ! docker info &>/dev/null; then
    err "Docker 未运行，请先启动 Docker Desktop"
    exit 1
fi
info "Docker OK"

# ── Step 1: Docker 基础设施 ───────────────────────────
info "启动基础设施..."
for svc in postgres nacos redis; do
  if docker ps --format '{{.Names}}' | grep -q "triobase-${svc}"; then
    info "  triobase-${svc} 已在运行"
  else
    docker compose -f "${ROOT_DIR}/docker/docker-compose.yml" up -d "${svc}" 2>&1 | tail -1
  fi
done
info "基础设施就绪"

# ── Step 2: Maven 并行编译（仅一次） ──────────────────
if $REBUILD || [ ! -f "${ROOT_DIR}/trio-base-services/service-auth/target/service-auth-0.1.0-SNAPSHOT.jar" ]; then
    info "Maven 并行编译（-T 1C）..."
    cd "${ROOT_DIR}" && mvn package -DskipTests -T 1C -q -pl "${SERVICES_LIST}" -am
    info "编译完成"
else
    info "JAR 已存在，跳过编译（--rebuild 强制重编）"
fi

# ── Step 3: 杀残留 + 并行启动 JAR ────────────────────
pkill -f "triobase.*SNAPSHOT.jar" 2>/dev/null || true
sleep 1

info "并行启动 5 个服务..."
nohup java -jar "${ROOT_DIR}/trio-base-services/service-auth/target/service-auth-0.1.0-SNAPSHOT.jar" > "${LOG_DIR}/service-auth.log" 2>&1 &
nohup java -jar "${ROOT_DIR}/trio-base-services/service-org/target/service-org-0.1.0-SNAPSHOT.jar" > "${LOG_DIR}/service-org.log" 2>&1 &
nohup java -jar "${ROOT_DIR}/trio-base-services/service-lowcode/target/service-lowcode-0.1.0-SNAPSHOT.jar" > "${LOG_DIR}/service-lowcode.log" 2>&1 &
nohup java -jar "${ROOT_DIR}/trio-base-services/service-workflow-engine/target/service-workflow-engine-0.1.0-SNAPSHOT.jar" > "${LOG_DIR}/service-workflow-engine.log" 2>&1 &
nohup java -jar "${ROOT_DIR}/trio-base-platform/platform-gateway/target/platform-gateway-0.1.0-SNAPSHOT.jar" > "${LOG_DIR}/platform-gateway.log" 2>&1 &

# 等 Gateway 就绪作为启动完成标志
for i in $(seq 1 30); do
  if curl -s -o /dev/null http://localhost:8080 2>/dev/null; then
    info "全部服务就绪"
    break
  fi
  sleep 2
done

# ── 最终状态 ──────────────────────────────────────────
echo ""
echo "  后端服务:"
echo "    Gateway (8080):  $(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080 2>/dev/null || echo 'DOWN')"
echo "    Auth    (8081):  $(curl -s -o /dev/null -w '%{http_code}' http://localhost:8081 2>/dev/null || echo 'DOWN')"
echo "    Org     (8082):  $(curl -s -o /dev/null -w '%{http_code}' http://localhost:8082 2>/dev/null || echo 'DOWN')"
echo "    Lowcode (8085):  $(curl -s -o /dev/null -w '%{http_code}' http://localhost:8085 2>/dev/null || echo 'DOWN')"
echo "    Workflow(8086):  $(curl -s -o /dev/null -w '%{http_code}' http://localhost:8086 2>/dev/null || echo 'DOWN')"
echo ""
echo "  🔗  前端:   http://localhost:5173"
echo "  🔑  登录:   admin / admin123"
echo "  📋  日志:   ${LOG_DIR}/"
echo "  🛑  停止:   pkill -f 'triobase.*SNAPSHOT.jar'"

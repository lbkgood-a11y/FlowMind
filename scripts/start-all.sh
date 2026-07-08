#!/usr/bin/env bash
# ============================================================
# TrioBase 全栈快速启动脚本
# 使用方式: bash scripts/start-all.sh [--rebuild]
# ============================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="${ROOT_DIR}/logs"

# Colors
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
err()   { echo -e "${RED}[ERR]${NC}   $1"; }
step()  { echo -e "\n${CYAN}━━━ $1 ━━━${NC}"; }
title() { echo -e "\n${CYAN}════════════════════════════════════════════════════════════${NC}"; echo -e "${CYAN}  $1${NC}"; echo -e "${CYAN}════════════════════════════════════════════════════════════${NC}"; }

mkdir -p "${LOG_DIR}"

REBUILD=false
if [ "${1:-}" = "--rebuild" ]; then REBUILD=true; fi

# ── 前置检查 ──────────────────────────────────────────
title "TrioBase 全栈启动"

if ! docker info &>/dev/null; then
    err "Docker 未运行，请先启动 Docker Desktop"
    exit 1
fi
info "Docker OK"

JAVA_VER=$(java -version 2>&1 | head -1)
if [[ "$JAVA_VER" != *"25"* ]]; then
    warn "当前 Java 版本: $JAVA_VER（推荐 JDK 25）"
fi

# ── Step 1: Docker 基础设施 ───────────────────────────
step "1/5  启动 Docker 基础设施"

DOCKER_COMPOSE_FILE="${ROOT_DIR}/docker/docker-compose.yml"
SERVICES=("postgres" "nacos" "redis" "neo4j" "clickhouse" "temporal")

# 逐个启动避免镜像拉取失败阻塞
for svc in "${SERVICES[@]}"; do
  if docker ps --format '{{.Names}}' | grep -q "triobase-${svc}"; then
    info "  ✅ triobase-${svc} 已在运行"
  else
    info "  🚀 启动 triobase-${svc}..."
    docker compose -f "${DOCKER_COMPOSE_FILE}" up -d "${svc}" 2>&1 | tail -1
  fi
done

# 检查关键服务就绪
info "  等待关键服务就绪..."
for i in {1..30}; do
  if nc -z localhost 5432 && nc -z localhost 8848 && nc -z localhost 6379; then
    break
  fi
  sleep 2
done
info "  ✅ Postgres / Nacos / Redis 就绪"

# ── Step 2: Maven 编译（只做一次） ────────────────────
step "2/5  Maven 编译 Java 模块（首次或 --rebuild 时）"

if $REBUILD || [ ! -f "${ROOT_DIR}/trio-base-services/service-auth/target/service-auth-0.1.0-SNAPSHOT.jar" ]; then
    info "  编译 common 模块..."
    cd "${ROOT_DIR}" && mvn clean install -DskipTests -pl trio-base-common -am -q 2>&1 | grep -v "^$"
    info "  打包 services..."
    cd "${ROOT_DIR}" && mvn clean package -DskipTests -am -q -pl trio-base-services/service-auth 2>&1 | grep -v "^$"
    cd "${ROOT_DIR}" && mvn clean package -DskipTests -am -q -pl trio-base-services/service-org 2>&1 | grep -v "^$"
    cd "${ROOT_DIR}" && mvn clean package -DskipTests -am -q -pl trio-base-services/service-lowcode 2>&1 | grep -v "^$"
    cd "${ROOT_DIR}" && mvn clean package -DskipTests -am -q -pl trio-base-platform/platform-gateway 2>&1 | grep -v "^$"
    info "  ✅ 编译打包完成"
else
    info "  ⏭️  JAR 已存在，跳过编译（如需重新编译请加 --rebuild 参数）"
fi

# ── Step 3: 低代码数据库表（首次需手动初始化） ────────
step "3/5  初始化低代码数据库表（首次）"

TABLE_COUNT=$(docker exec triobase-postgres psql -U triobase -d triobase -t -c "SELECT count(*) FROM information_schema.tables WHERE table_name LIKE 'lc_%';" 2>/dev/null | tr -d ' ')
if [ "${TABLE_COUNT}" = "0" ]; then
    info "  创建低代码表单表..."
    docker exec -i triobase-postgres psql -U triobase -d triobase < "${ROOT_DIR}/trio-base-services/service-lowcode/src/main/resources/db/migration/V2__lowcode_form_schema.sql"
    info "  ✅ 低代码表创建完成"
else
    info "  ⏭️  低代码表已存在，跳过"
fi

# ── Step 4: 启动后端 Java 服务 ────────────────────────
step "4/5  启动后端 Java 服务"

# 杀掉残留进程
pkill -f "service-auth" 2>/dev/null || true
pkill -f "service-org" 2>/dev/null || true
pkill -f "service-lowcode" 2>/dev/null || true
pkill -f "platform-gateway" 2>/dev/null || true
sleep 2

info "  🚀 启动 Auth 服务 (8081)..."
nohup java -jar "${ROOT_DIR}/trio-base-services/service-auth/target/service-auth-0.1.0-SNAPSHOT.jar" \
    > "${LOG_DIR}/service-auth.log" 2>&1 &
AUTH_PID=$!
echo "    PID: ${AUTH_PID}"

# 等 Auth 就绪
for i in {1..30}; do
  if curl -s -o /dev/null -w '%{http_code}' -X POST http://localhost:8081/api/v1/auth/login \
       -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}' 2>/dev/null | grep -q "200\|403\|401"; then
    break
  fi
  sleep 2
done

info "  🚀 启动 Org 服务 (8082)..."
nohup java -jar "${ROOT_DIR}/trio-base-services/service-org/target/service-org-0.1.0-SNAPSHOT.jar" \
    > "${LOG_DIR}/service-org.log" 2>&1 &
ORG_PID=$!
echo "    PID: ${ORG_PID}"
sleep 5

info "  🚀 启动 Lowcode 服务 (8085)..."
nohup java -jar "${ROOT_DIR}/trio-base-services/service-lowcode/target/service-lowcode-0.1.0-SNAPSHOT.jar" \
    > "${LOG_DIR}/service-lowcode.log" 2>&1 &
LOWCODE_PID=$!
echo "    PID: ${LOWCODE_PID}"
sleep 5

info "  🚀 启动 Gateway 服务 (8080)..."
nohup java -jar "${ROOT_DIR}/trio-base-platform/platform-gateway/target/platform-gateway-0.1.0-SNAPSHOT.jar" \
    > "${LOG_DIR}/platform-gateway.log" 2>&1 &
GATEWAY_PID=$!
echo "    PID: ${GATEWAY_PID}"
sleep 3

# ── Step 5: 启动前端 ──────────────────────────────────
step "5/5  启动前端 Dev Server (3000)"

if lsof -i :3000 &>/dev/null 2>&1; then
    info "  ⏭️  前端已在运行"
else
    info "  🚀 启动 Next.js..."
    cd "${ROOT_DIR}/trio-base-frontend"
    nohup npm run dev > "${LOG_DIR}/frontend.log" 2>&1 &
    echo "    PID: $!"
    sleep 3
fi

# ── 最终验证 ──────────────────────────────────────────
title "✅  全栈启动完成"

echo ""
echo "  🔗  访问地址:"
echo "      前端:      http://localhost:3000"
echo "      API 网关:  http://localhost:8080"
echo "      Auth:      http://localhost:8081"
echo "      Org:       http://localhost:8082"
echo "      Nacos:     http://localhost:8848/nacos"
echo "      Neo4j:     http://localhost:7474"
echo ""
echo "  🔑  登录凭据:  admin / admin123"
echo ""
echo "  📋  日志文件:  ${LOG_DIR}/"
echo "  🛑  停止命令:  pkill -f 'service-auth|service-org|service-lowcode|platform-gateway'"
echo "               docker compose -f ${DOCKER_COMPOSE_FILE} stop"
echo ""

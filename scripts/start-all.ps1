# ============================================================
# TrioBase 全栈快速启动脚本 — Windows PowerShell 版
# 使用方式: .\scripts\start-all.ps1 [-Rebuild]
# ============================================================
param([switch]$Rebuild)

$ErrorActionPreference = "Stop"
$rootDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$logDir = "$rootDir\logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

$services = @(
    @{Name="service-auth";             Port=8081; Jar="$rootDir\trio-base-services\service-auth\target\service-auth-0.1.0-SNAPSHOT.jar"},
    @{Name="service-org";              Port=8082; Jar="$rootDir\trio-base-services\service-org\target\service-org-0.1.0-SNAPSHOT.jar"},
    @{Name="service-tenant";           Port=8083; Jar="$rootDir\trio-base-services\service-tenant\target\service-tenant-0.1.0-SNAPSHOT.jar"},
    @{Name="service-lowcode";          Port=8085; Jar="$rootDir\trio-base-services\service-lowcode\target\service-lowcode-0.1.0-SNAPSHOT.jar"},
    @{Name="service-workflow-engine";  Port=8086; Jar="$rootDir\trio-base-services\service-workflow-engine\target\service-workflow-engine-0.1.0-SNAPSHOT.jar"},
    @{Name="service-action";           Port=8089; Jar="$rootDir\trio-base-services\service-action\target\service-action-0.1.0-SNAPSHOT.jar"},
    @{Name="platform-gateway";         Port=8080; Jar="$rootDir\trio-base-platform\platform-gateway\target\platform-gateway-0.1.0-SNAPSHOT.jar"}
)

# ── Step 1: Maven 并行编译 ──────────────────────────
$firstJar = "$rootDir\trio-base-services\service-auth\target\service-auth-0.1.0-SNAPSHOT.jar"
if ($Rebuild -or -not (Test-Path $firstJar)) {
    Write-Host "[BUILD] Maven 并行编译 (-T 1C)..." -ForegroundColor Green
    $serviceList = ($services | ForEach-Object { $_.Name -replace "platform-gateway","platform-gateway" }) -join ","
    $serviceList = "trio-base-services/service-auth,trio-base-services/service-org,trio-base-services/service-tenant,trio-base-services/service-lowcode,trio-base-services/service-workflow-engine,trio-base-services/service-action,trio-base-platform/platform-gateway"
    mvn package -DskipTests -T 1C -q -pl $serviceList -am
    if ($LASTEXITCODE -ne 0) { throw "Maven 编译失败" }
    Write-Host "[BUILD] 编译完成" -ForegroundColor Green
} else {
    Write-Host "[BUILD] JAR 已存在，跳过编译" -ForegroundColor Yellow
}

# ── Step 2: 杀残留进程 ──────────────────────────────
foreach ($svc in $services) {
    $conn = Get-NetTCPConnection -LocalPort $svc.Port -State Listen -ErrorAction SilentlyContinue
    if ($conn) {
        Stop-Process -Id $conn.OwningProcess -Force -ErrorAction SilentlyContinue
        Write-Host "  Killed PID $($conn.OwningProcess) on port $($svc.Port)"
    }
}
Start-Sleep -Seconds 1

# ── Step 3: 并行启动 JAR ────────────────────────────
Write-Host "[START] 并行启动 7 个服务..." -ForegroundColor Green
foreach ($svc in $services) {
    $logFile = "$logDir\$($svc.Name).log"
    Start-Process -WindowStyle Hidden -FilePath "java" -ArgumentList "-jar", $svc.Jar -RedirectStandardOutput $logFile -RedirectStandardError "$logFile.err"
    Write-Host "  $($svc.Name) (:$($svc.Port))"
}

# ── Step 4: 等待就绪 ────────────────────────────────
Write-Host "[WAIT] 等待 Gateway (8080) 就绪..." -ForegroundColor Cyan
$ready = $false
for ($i = 0; $i -lt 30; $i++) {
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:8080" -TimeoutSec 2 -UseBasicParsing -ErrorAction SilentlyContinue
        if ($r) { $ready = $true; break }
    } catch { }
    Start-Sleep -Seconds 2
}

# ── 最终状态 ────────────────────────────────────────
Write-Host ""
Write-Host "=== TrioBase 全栈启动完成 ===" -ForegroundColor Green
Write-Host ""
Write-Host "  后端服务:"
foreach ($svc in $services) {
    try {
        $status = (Invoke-WebRequest -Uri "http://localhost:$($svc.Port)" -TimeoutSec 2 -UseBasicParsing -ErrorAction SilentlyContinue).StatusCode
        Write-Host "    $($svc.Name.PadRight(22)) :$($svc.Port)  $status"
    } catch {
        Write-Host "    $($svc.Name.PadRight(22)) :$($svc.Port)  DOWN" -ForegroundColor Red
    }
}
Write-Host ""
Write-Host "  Frontend:  http://localhost:5173"
Write-Host "  Login:     admin / admin123"
Write-Host "  Logs:      $logDir\"

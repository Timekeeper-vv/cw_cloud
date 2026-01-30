# Start four core services and frontend
# Services: gateway, system, infra, monitor, frontend

Write-Host "Starting YuDao Cloud Core Services" -ForegroundColor Green

# 获取脚本所在目录
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

# 1. Start infrastructure services (MySQL, Redis, Nacos)
Write-Host "`nStarting infrastructure services (MySQL, Redis, Nacos)..." -ForegroundColor Yellow
docker compose -f docker-compose-simple.yml up mysql redis nacos -d

if ($LASTEXITCODE -ne 0) {
    Write-Host "Docker services failed to start, please check if Docker is running" -ForegroundColor Red
    exit 1
}

Write-Host "Waiting for infrastructure services to start (30 seconds)..." -ForegroundColor Yellow
Start-Sleep -Seconds 30

# 2. Create log directories
Write-Host "`nCreating log directories..." -ForegroundColor Yellow
$logDirs = @(
    "yudao-gateway\logs",
    "yudao-module-system\yudao-module-system-server\logs",
    "yudao-module-infra\yudao-module-infra-server\logs",
    "yudao-module-monitor\yudao-module-monitor-server\logs"
)

foreach ($dir in $logDirs) {
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
}

# 3. Start Gateway service (port 48080)
Write-Host "`nStarting Gateway service (port 48080)..." -ForegroundColor Cyan
Set-Location "yudao-gateway"
Start-Process -FilePath "java" -ArgumentList "-jar", "target\yudao-gateway.jar", "--spring.profiles.active=local", "--server.port=48080" -RedirectStandardOutput "logs\gateway.log" -RedirectStandardError "logs\gateway-error.log" -WindowStyle Hidden
Set-Location $scriptDir
Write-Host "Gateway service started" -ForegroundColor Green
Start-Sleep -Seconds 30

# 4. Start System service (port 48081)
Write-Host "`nStarting System service (port 48081)..." -ForegroundColor Cyan
Set-Location "yudao-module-system\yudao-module-system-server"
Start-Process -FilePath "java" -ArgumentList "-jar", "target\yudao-module-system-server.jar", "--spring.profiles.active=local", "--server.port=48081" -RedirectStandardOutput "logs\system.log" -RedirectStandardError "logs\system-error.log" -WindowStyle Hidden
Set-Location $scriptDir
Write-Host "System service started" -ForegroundColor Green
Start-Sleep -Seconds 30

# 5. Start Infra service (port 48082)
Write-Host "`nStarting Infra service (port 48082)..." -ForegroundColor Cyan
Set-Location "yudao-module-infra\yudao-module-infra-server"
Start-Process -FilePath "java" -ArgumentList "-jar", "target\yudao-module-infra-server.jar", "--spring.profiles.active=local", "--server.port=48082" -RedirectStandardOutput "logs\infra.log" -RedirectStandardError "logs\infra-error.log" -WindowStyle Hidden
Set-Location $scriptDir
Write-Host "Infra service started" -ForegroundColor Green
Start-Sleep -Seconds 30

# 6. Start Monitor service (port 48090)
Write-Host "`nStarting Monitor service (port 48090)..." -ForegroundColor Cyan
Set-Location "yudao-module-monitor\yudao-module-monitor-server"
Start-Process -FilePath "java" -ArgumentList "-jar", "target\yudao-module-monitor-server.jar", "--spring.profiles.active=local", "--server.port=48090" -RedirectStandardOutput "logs\monitor.log" -RedirectStandardError "logs\monitor-error.log" -WindowStyle Hidden
Set-Location $scriptDir
Write-Host "Monitor service started" -ForegroundColor Green
Start-Sleep -Seconds 30

# 7. Start frontend service
Write-Host "`nStarting frontend service..." -ForegroundColor Cyan
$frontendDir = Join-Path (Split-Path -Parent $scriptDir) "yudao-ui-admin-vue3"
if (Test-Path $frontendDir) {
    Set-Location $frontendDir
    
    # Check if dependencies are installed
    if (-not (Test-Path "node_modules")) {
        Write-Host "Installing frontend dependencies..." -ForegroundColor Yellow
        if (Get-Command pnpm -ErrorAction SilentlyContinue) {
            pnpm install
        } elseif (Get-Command npm -ErrorAction SilentlyContinue) {
            npm install
        } else {
            Write-Host "pnpm or npm not found, please install Node.js first" -ForegroundColor Red
            Set-Location $scriptDir
            exit 1
        }
    }
    
    # Start frontend development server
    Write-Host "Starting frontend development server..." -ForegroundColor Yellow
    if (Get-Command pnpm -ErrorAction SilentlyContinue) {
        Start-Process -FilePath "pnpm" -ArgumentList "run", "dev" -WindowStyle Normal
    } else {
        Start-Process -FilePath "npm" -ArgumentList "run", "dev" -WindowStyle Normal
    }
    
    Set-Location $scriptDir
    Write-Host "Frontend service started" -ForegroundColor Green
} else {
    Write-Host "Frontend directory not found: $frontendDir" -ForegroundColor Yellow
}

# 8. Display startup information
Write-Host "`nAll services started successfully!" -ForegroundColor Green
Write-Host "`nService access addresses:" -ForegroundColor Cyan
Write-Host "  - Gateway:  http://localhost:48080" -ForegroundColor White
Write-Host "  - System:   http://localhost:48081" -ForegroundColor White
Write-Host "  - Infra:    http://localhost:48082" -ForegroundColor White
Write-Host "  - Monitor:  http://localhost:48090" -ForegroundColor White
Write-Host "  - Frontend: http://localhost:5173 (or http://localhost:3000)" -ForegroundColor White
Write-Host "`nTip: Service logs are located in the logs directory of each service" -ForegroundColor Yellow

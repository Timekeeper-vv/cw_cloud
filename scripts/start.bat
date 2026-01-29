@echo off
chcp 65001 >nul
color 0A
title 工业健康监测系统 - 一键启动

echo.
echo ========================================
echo   工业健康监测系统 - 一键启动
echo ========================================
echo.

set "PROJECT_ROOT=E:\Code\CW_Cloud"
set "BACKEND_ROOT=%PROJECT_ROOT%\yudao-cloud"
set "FRONTEND_ROOT=%PROJECT_ROOT%\yudao-ui-admin-vue3"

cd /d "%PROJECT_ROOT%"

:: ========================================
:: 阶段1: 基础设施
:: ========================================
echo [阶段1/4] 检查基础设施...
echo.

echo   检查 MySQL...
netstat -ano 2>nul | findstr ":3306" | findstr "LISTENING" >nul
if %errorlevel%==0 (echo   [OK] MySQL 3306) else (echo   [X] MySQL 未运行)

echo   检查 Redis...
netstat -ano 2>nul | findstr ":6379" | findstr "LISTENING" >nul
if %errorlevel%==0 (echo   [OK] Redis 6379) else (echo   [X] Redis 未运行)

echo   检查 Nacos...
netstat -ano 2>nul | findstr ":8848" | findstr "LISTENING" >nul
if %errorlevel%==0 (
    echo   [OK] Nacos 8848
) else (
    echo   [INFO] 启动 Nacos...
    start "Nacos" /MIN cmd /c "cd /d %FRONTEND_ROOT%\nacos\bin && startup.cmd -m standalone"
    echo   等待 Nacos 启动...
    timeout /t 20 /nobreak >nul
)

echo   检查 Kafka...
netstat -ano 2>nul | findstr ":9092" | findstr "LISTENING" >nul
if %errorlevel%==0 (
    echo   [OK] Kafka 9092
) else (
    if exist "%PROJECT_ROOT%\kafka\bin\windows\kafka-server-start.bat" (
        echo   [INFO] 检查 Zookeeper...
        netstat -ano 2>nul | findstr ":2181" | findstr "LISTENING" >nul
        if not %errorlevel%==0 (
            echo   [INFO] 启动 Zookeeper...
            start "Zookeeper" /MIN cmd /c "cd /d %PROJECT_ROOT%\kafka && bin\windows\zookeeper-server-start.bat config\zookeeper.properties"
            timeout /t 8 /nobreak >nul
        )
        echo   [INFO] 启动 Kafka...
        start "Kafka" /MIN cmd /c "cd /d %PROJECT_ROOT%\kafka && bin\windows\kafka-server-start.bat config\server.properties"
        timeout /t 10 /nobreak >nul
    ) else (
        echo   [SKIP] Kafka 未安装
    )
)

echo.

:: ========================================
:: 阶段2: 后端微服务
:: ========================================
echo [阶段2/4] 启动后端微服务...
echo.

set "GATEWAY_JAR=%BACKEND_ROOT%\yudao-gateway\target\yudao-gateway.jar"
set "SYSTEM_JAR=%BACKEND_ROOT%\yudao-module-system\yudao-module-system-server\target\yudao-module-system-server.jar"
set "INFRA_JAR=%BACKEND_ROOT%\yudao-module-infra\yudao-module-infra-server\target\yudao-module-infra-server.jar"

echo   检查 Gateway...
netstat -ano 2>nul | findstr ":48080" | findstr "LISTENING" >nul
if %errorlevel%==0 (
    echo   [OK] Gateway 48080 已运行
) else (
    echo   [INFO] 启动 Gateway 48080...
    start "Gateway" /MIN java -jar "%GATEWAY_JAR%" --spring.profiles.active=local --server.port=48080
    timeout /t 15 /nobreak >nul
)

echo   检查 System...
netstat -ano 2>nul | findstr ":48081" | findstr "LISTENING" >nul
if %errorlevel%==0 (
    echo   [OK] System 48081 已运行
) else (
    echo   [INFO] 启动 System 48081...
    start "System" /MIN java -jar "%SYSTEM_JAR%" --spring.profiles.active=local --server.port=48081
    timeout /t 15 /nobreak >nul
)

echo   检查 Infra...
netstat -ano 2>nul | findstr ":48082" | findstr "LISTENING" >nul
if %errorlevel%==0 (
    echo   [OK] Infra 48082 已运行
) else (
    echo   [INFO] 启动 Infra 48082...
    start "Infra" /MIN java -jar "%INFRA_JAR%" --spring.profiles.active=local --server.port=48082
    timeout /t 10 /nobreak >nul
)

echo.

:: ========================================
:: 阶段3: 数据处理服务
:: ========================================
echo [阶段3/4] 启动数据处理服务...
echo.

echo   检查 Backend滤波...
netstat -ano 2>nul | findstr ":8080" | findstr "LISTENING" >nul
if %errorlevel%==0 (
    echo   [OK] Backend滤波 8080 已运行
) else (
    if exist "%PROJECT_ROOT%\backend.jar" (
        echo   [INFO] 启动 Backend滤波 8080...
        start "Backend-Filter" /MIN java --add-opens java.base/java.lang=ALL-UNNAMED -jar "%PROJECT_ROOT%\backend.jar"
        timeout /t 5 /nobreak >nul
    ) else (
        echo   [SKIP] backend.jar 不存在
    )
)

echo   检查 TDMS API...
netstat -ano 2>nul | findstr ":3002" | findstr "LISTENING" >nul
if %errorlevel%==0 (
    echo   [OK] TDMS API 3002 已运行
) else (
    echo   [INFO] 启动 TDMS API 3002...
    start "TDMS-API" /MIN cmd /c "cd /d %PROJECT_ROOT%\services && node tdms-api-server.js"
    timeout /t 2 /nobreak >nul
)

echo   检查 WebSocket Bridge...
netstat -ano 2>nul | findstr ":8081" | findstr "LISTENING" >nul
if %errorlevel%==0 (
    echo   [OK] WebSocket 8081 已运行
) else (
    echo   [INFO] 启动 WebSocket Bridge 8081...
    start "WebSocket-Bridge" /MIN cmd /c "cd /d %PROJECT_ROOT%\services && node websocket-bridge.js"
    timeout /t 2 /nobreak >nul
)

echo.

:: ========================================
:: 阶段4: 前端服务
:: ========================================
echo [阶段4/4] 启动前端服务...
echo.

echo   [INFO] 启动 Vue3 前端...
start "Vue3-Frontend" cmd /c "cd /d %FRONTEND_ROOT% && npm run dev"
timeout /t 5 /nobreak >nul

echo.
echo ========================================
echo   启动完成！
echo ========================================
echo.
echo   前端: http://localhost:80
echo   账号: admin / admin123
echo.
echo   Nacos: http://localhost:8848/nacos
echo.

:: 显示最终状态
echo [最终状态检查]
call scripts\status.bat

#!/bin/bash
set -e
echo "🛑 停止 YuDao Cloud"

# 停前端（Vite）
pkill -f "vite.*yudao-ui-admin-vue3" || true
pkill -f "node.*vite" || true

# 停 Java 微服务 & 自定义服务器
pkill -f "yudao-gateway.jar" || true
pkill -f "yudao-module-system-server.jar" || true
pkill -f "yudao-module-infra-server.jar" || true
pkill -f "SimpleFilterServer" || true

# 停 Docker 基础设施
docker compose -f docker-compose-simple.yml stop || true

echo "✅ 所有服务已停止"

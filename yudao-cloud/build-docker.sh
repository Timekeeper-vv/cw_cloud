#!/bin/bash

# Docker构建脚本
echo "🐳 开始构建 YuDao Cloud Docker 镜像..."

# 设置颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 错误处理
set -e

# 函数：打印带颜色的消息
print_message() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[$(date +'%Y-%m-%d %H:%M:%S')] ✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}[$(date +'%Y-%m-%d %H:%M:%S')] ⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}[$(date +'%Y-%m-%d %H:%M:%S')] ❌ $1${NC}"
}

# 1. 清理旧的构建产物
print_message "清理旧的构建产物..."
mvn clean -q

# 2. 编译Java项目
print_message "编译Java项目..."
mvn package -DskipTests -q

# 检查JAR文件是否生成成功
check_jar_files() {
    local files=(
        "yudao-gateway/target/yudao-gateway.jar"
        "yudao-module-system/yudao-module-system-server/target/yudao-module-system-server.jar"
        "yudao-module-infra/yudao-module-infra-server/target/yudao-module-infra-server.jar"
    )
    
    for file in "${files[@]}"; do
        if [ ! -f "$file" ]; then
            print_error "JAR文件不存在: $file"
            exit 1
        else
            print_success "JAR文件已生成: $file"
        fi
    done
}

check_jar_files

# 3. 构建前端项目
print_message "构建前端项目..."
cd ../yudao-ui-admin-vue3

# 检查Node.js版本
if ! command -v node &> /dev/null; then
    print_error "Node.js 未安装"
    exit 1
fi

# 使用nvm确保正确的Node版本
if [ -f ~/.nvm/nvm.sh ]; then
    source ~/.nvm/nvm.sh
    nvm use 18 || {
        print_warning "Node.js 18 未安装，正在安装..."
        nvm install 18
        nvm use 18
    }
fi

# 安装依赖并构建
npm ci --silent
npm run build:prod

print_success "前端项目构建完成"

# 返回后端目录
cd ../yudao-cloud

# 4. 构建Docker镜像
print_message "开始构建Docker镜像..."

# 检查Docker是否运行
if ! docker info &> /dev/null; then
    print_error "Docker 未运行，请启动Docker"
    exit 1
fi

# 使用docker compose构建所有镜像
print_message "使用 docker compose 构建镜像..."
docker compose build --parallel

print_success "Docker镜像构建完成"

# 5. 显示构建的镜像
print_message "显示构建的镜像列表:"
docker images | grep -E "(yudao|mysql|redis|nacos)" || echo "没有找到相关镜像"

# 6. 提示下一步操作
echo ""
print_success "🎉 构建完成！"
echo ""
echo "下一步操作："
echo "1. 启动所有服务: docker compose up -d"
echo "2. 查看服务状态: docker compose ps"
echo "3. 查看服务日志: docker compose logs -f [service-name]"
echo "4. 停止所有服务: docker compose down"
echo ""
echo "访问地址："
echo "- 前端管理界面: http://localhost:3000"
echo "- API网关: http://localhost:48080"
echo "- Nacos控制台: http://localhost:8848/nacos (nacos/nacos)"
echo ""

# 🪟 Windows 部署指南 - 工业健康监测云平台

## 📋 环境检查结果

✅ **Node.js**: v22.21.0 (已安装)  
✅ **Java**: 23.0.1 (已安装)  
❌ **Docker**: 未安装 (需要安装)

---

## 🚀 快速部署步骤

### 第一步:安装Docker Desktop(推荐)

1. **下载Docker Desktop for Windows**
   - 访问: https://www.docker.com/products/docker-desktop/
   - 下载并安装 Docker Desktop

2. **启动Docker Desktop**
   - 安装完成后,启动 Docker Desktop
   - 等待 Docker 引擎启动完成
   - 确认任务栏中 Docker 图标显示为运行状态

3. **验证Docker安装**
   ```powershell
   docker --version
   docker-compose --version
   ```

---

### 第二步:启动后端基础设施

使用 Docker Compose 启动 MySQL、Redis、Nacos:

```powershell
# 进入后端项目目录
cd e:\Code\CW_Cloud\yudao-cloud

# 启动基础设施服务
docker-compose up -d mysql redis nacos

# 查看服务状态
docker-compose ps

# 查看服务日志
docker-compose logs -f
```

**等待时间**: 约 1-2 分钟,等待所有服务启动完成。

**验证服务**:
- MySQL: http://localhost:3306 (用户: root, 密码: 20041102)
- Redis: http://localhost:6379
- Nacos: http://localhost:8848/nacos (用户: nacos, 密码: nacos)

---

### 第三步:启动前端服务

```powershell
# 进入前端项目目录
cd e:\Code\CW_Cloud\yudao-ui-admin-vue3

# 安装依赖(首次运行)
npm install

# 启动开发服务器
npm run dev
```

**访问地址**: http://localhost:3000  
**默认账号**: admin / admin123

---

## 🎯 简化部署方案(仅前端)

如果您只想先看前端界面,可以只启动前端:

```powershell
cd e:\Code\CW_Cloud\yudao-ui-admin-vue3
npm install
npm run dev
```

> ⚠️ **注意**: 没有后端支持,前端某些功能可能无法正常工作。

---

## 🔧 完整后端部署(可选)

如果需要完整的后端服务(包括微服务),需要额外步骤:

### 1. 编译后端项目

```powershell
cd e:\Code\CW_Cloud\yudao-cloud

# 编译整个项目
mvn clean install -DskipTests

# 或者只编译核心模块
cd yudao-gateway
mvn clean package -DskipTests

cd ..\yudao-module-system\yudao-module-system-server
mvn clean package -DskipTests

cd ..\..\yudao-module-infra\yudao-module-infra-server
mvn clean package -DskipTests
```

### 2. 启动微服务

需要按顺序启动:

```powershell
# 1. 启动 Gateway (网关)
cd e:\Code\CW_Cloud\yudao-cloud\yudao-gateway
java -jar target\yudao-gateway.jar --spring.profiles.active=local --server.port=48080

# 2. 启动 System (系统服务) - 新开一个终端
cd e:\Code\CW_Cloud\yudao-cloud\yudao-module-system\yudao-module-system-server
java -jar target\yudao-module-system-server.jar --spring.profiles.active=local --server.port=48081

# 3. 启动 Infra (基础设施) - 新开一个终端
cd e:\Code\CW_Cloud\yudao-cloud\yudao-module-infra\yudao-module-infra-server
java -jar target\yudao-module-infra-server.jar --spring.profiles.active=local --server.port=48082
```

---

## 📝 端口说明

| 服务 | 端口 | 说明 |
|------|------|------|
| 前端界面 | 3000 | Vue3 开发服务器 |
| API网关 | 48080 | Spring Cloud Gateway |
| 系统服务 | 48081 | System微服务 |
| 基础设施 | 48082 | Infra微服务 |
| MySQL | 3306 | 数据库 |
| Redis | 6379 | 缓存 |
| Nacos | 8848 | 注册/配置中心 |

---

## 🛠️ 常见问题

### 1. Docker 安装失败?

- 确保开启 Windows 的 Hyper-V 或 WSL2
- 以管理员身份运行安装程序

### 2. 端口被占用?

检查并关闭占用端口的进程:

```powershell
# 查看占用端口的进程
netstat -ano | findstr "3000"
netstat -ano | findstr "48080"

# 终止进程 (PID 是进程ID)
taskkill /PID <进程ID> /F
```

### 3. npm install 失败?

尝试使用淘宝镜像:

```powershell
npm config set registry https://registry.npmmirror.com
npm install
```

或使用 pnpm (更快):

```powershell
npm install -g pnpm
pnpm install
```

### 4. Java 版本问题?

项目要求 JDK 1.8+,您当前是 JDK 23,应该兼容。如果出现问题,可以降级到 JDK 17 或 JDK 11。

---

## ✅ 验证部署成功

1. **前端服务**:
   - 访问 http://localhost:3000
   - 看到登录页面

2. **后端服务** (如果启动了):
   - 访问 http://localhost:8848/nacos
   - 查看服务注册列表

3. **完整功能**:
   - 登录系统 (admin / admin123)
   - 访问各个功能模块

---

## 🎉 快速启动命令总结

### 只启动前端

```powershell
cd e:\Code\CW_Cloud\yudao-ui-admin-vue3
npm install
npm run dev
```

### 完整部署(需要Docker)

```powershell
# 终端 1: 启动基础设施
cd e:\Code\CW_Cloud\yudao-cloud
docker-compose up -d mysql redis nacos

# 终端 2: 启动前端
cd e:\Code\CW_Cloud\yudao-ui-admin-vue3
npm run dev
```

---

## 📞 需要帮助?

如有问题,请参考项目文档:
- [README.md](yudao-cloud/README.md)
- [系统使用指南](yudao-cloud/系统使用指南.md)
- [Docker部署说明](yudao-cloud/Docker部署说明.md)

---

**部署时间**: 2025-11-24  
**推荐方案**: Docker + 前端开发模式

# 🏭 工业健康监测平台

> 基于 Spring Cloud Alibaba + Vue3 的工业设备健康监测系统

**在线访问**: http://8.145.42.157  
**默认账号**: admin / admin123

---

## 📁 项目结构

```
CW_Cloud/
├── yudao-cloud/          # 后端微服务（Spring Cloud）
├── yudao-ui-admin-vue3/  # 前端项目（Vue3 + Element Plus）
├── services/             # 数据处理服务（Node.js/Python）
├── scripts/              # 启动脚本（Windows .bat）
├── sql/                  # 数据库脚本
├── config/               # 配置文件
├── docs/                 # 项目文档
├── tools/                # 工具和测试文件
├── kafka/                # Kafka 配置
├── floatdata/            # FloatData 数据源
└── data/                 # 数据文件
```

---

## 🚀 快速启动

### Windows 本地开发

```bash
# 一键启动所有服务
scripts\start.bat

# 停止所有服务
scripts\stop.bat

# 检查系统状态
scripts\status.bat
```

访问地址: http://localhost:80

### 云服务器部署

详见 [docs/云服务器部署指南.md](docs/云服务器部署指南.md)

---

## 🔧 技术栈

**后端**
- Spring Cloud Alibaba 2022
- Spring Boot 3.x
- MyBatis Plus
- MySQL 8.0 / Redis

**前端**
- Vue 3 + TypeScript
- Element Plus
- Vite
- ECharts

**中间件**
- Nacos（注册中心/配置中心）
- Kafka（消息队列）

---

## 📊 服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| Gateway | 48080 | API 网关 |
| System | 48081 | 系统服务 |
| Infra | 48082 | 基础设施服务 |
| Frontend | 80/3000 | 前端页面 |
| Nacos | 8848 | 注册中心 |
| MySQL | 3306 | 数据库 |
| Redis | 6379 | 缓存 |

---

## 📝 数据库初始化

```bash
# 1. 创建数据表（滤波结果、异常检测等）
mysql -u root -p123456 ruoyi_vue_pro < sql/init-tables.sql

# 2. 添加菜单配置（实时监控、自适应滤波器）
mysql -u root -p123456 ruoyi_vue_pro < sql/init-menus.sql
```

---

## 📚 文档目录

- [云服务器部署指南](docs/云服务器部署指南.md)
- [Windows部署指南](docs/Windows部署指南.md)
- [系统架构说明](docs/系统架构说明.md)
- [快速启动指南](docs/快速启动指南.md)

---

## 🔄 开发流程

```bash
# 1. 本地修改代码
# 2. 提交到 GitHub
git add . && git commit -m "xxx" && git push

# 3. 服务器拉取并部署
cd /opt/cw-cloud/CW_Cloud
git pull
cd yudao-ui-admin-vue3
npm run build:prod
```

---

**最后更新**: 2025-12-16

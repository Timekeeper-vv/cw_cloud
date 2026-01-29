# 脚本使用指南 | Scripts Usage Guide

简化后的项目只保留了 **4 个核心脚本**，让使用更简单明了。

---

## 📝 核心脚本清单

| 脚本 | 用途 | 使用频率 |
|------|------|---------|
| `start.ps1` | 启动系统 | ⭐⭐⭐ 必需 |
| `stop.bat` | 停止系统 | ⭐⭐⭐ 必需 |
| `monitor.ps1` | 监控消息 | ⭐⭐ 常用 |
| `test-performance.ps1` | 性能测试 | ⭐ 可选 |

---

## 🚀 使用方法

### 1. 启动系统 - `start.ps1`

**这是你最常用的脚本！**

#### 基本用法（默认模式）
```powershell
.\start.ps1
```
启动系统 + 模拟数据源（后台运行，不弹窗）

#### 高级用法

**使用真实 TDMS 数据**
```powershell
.\start.ps1 -DataSource tdms
```

**使用壁画断铅数据**
```powershell
.\start.ps1 -DataSource wallpainting
```

**显示所有组件窗口（调试用）**
```powershell
.\start.ps1 -ShowWindows
```

**组合使用**
```powershell
.\start.ps1 -DataSource tdms -ShowWindows
```

#### 启动后看到什么？
```
========================================
  FloatData Streaming System
  Starting Components...
========================================

[1/5] Starting Zookeeper...
[2/5] Starting Kafka Broker...
[3/5] Creating Kafka topics...
      [OK] Topics created
[4/5] Starting Netty Server (port 9090)...
[5/5] Starting Spark Processor...

========================================
  [SUCCESS] System started!
========================================

Components running:
  [1] Zookeeper       - localhost:2181
  [2] Kafka Broker    - localhost:9092
  [3] Netty Server    - localhost:9090
  [4] Spark Processor - Processing streams

Next steps:
  1. Monitor messages: .\monitor.ps1
  2. Stop system: .\stop.bat
  3. Test performance: .\test-performance.ps1
```

---

### 2. 监控消息 - `monitor.ps1`

启动系统后，在**新的 PowerShell 窗口**运行：

```powershell
.\monitor.ps1
```

#### 看到什么？
实时显示 Kafka 消息：
```json
{"timestamp":1700000000000,"sensorId":1,"samples":[0.123,0.456,...],"sampleRate":1000000}
{"timestamp":1700000001000,"sensorId":1,"samples":[0.234,0.567,...],"sampleRate":1000000}
...
```

按 `Ctrl + C` 停止监控（不影响系统运行）

---

### 3. 停止系统 - `stop.bat`

```batch
.\stop.bat
```
或者直接双击 `stop.bat` 文件

**作用**：
- 停止所有 Java 进程
- 停止 Kafka
- 停止 Zookeeper
- 清理后台进程

---

### 4. 性能测试 - `test-performance.ps1`

**前提条件**：
1. 已运行 `.\start.ps1` 启动系统
2. 已准备测试数据 `tdms-export.bin`

#### 运行测试（70秒）
```powershell
.\test-performance.ps1 -DurationSeconds 70
```

#### 测试结果
```
[INFO] Waiting for Netty server on port 9090...
[INFO] Starting HighRateDataSender for 70 seconds...
[INFO] Sender stopped after duration.
[INFO] Log stored at highrate.log

[RESULT] Average throughput over 30 samples: 65,044.04 samples/sec
```

---

## 📋 完整工作流程

### 场景1：日常测试（最常用）

```powershell
# 1. 启动系统
.\start.ps1

# 2. 监控消息（新窗口）
.\monitor.ps1

# 3. 完成后停止
.\stop.bat
```

### 场景2：使用真实TDMS数据

```powershell
# 1. 启动系统（TDMS模式）
.\start.ps1 -DataSource tdms

# 2. 在新窗口运行TDMS读取器
python tdms-reader.py

# 3. 监控消息（新窗口）
.\monitor.ps1

# 4. 完成后停止
.\stop.bat
```

### 场景3：性能测试

```powershell
# 1. 启动系统
.\start.ps1

# 2. 导出测试数据（首次需要）
python export-tdms-binary.py

# 3. 运行性能测试
.\test-performance.ps1 -DurationSeconds 70

# 4. 完成后停止
.\stop.bat
```

### 场景4：调试模式（显示所有窗口）

```powershell
# 启动时显示所有组件窗口
.\start.ps1 -ShowWindows
```
每个组件会在独立的 CMD 窗口中运行，方便查看日志。

---

## ❓ 常见问题

### Q1: 启动失败怎么办？

**检查步骤**：
1. 确认已构建项目：`mvn clean package -DskipTests`
2. 确认 Kafka 已安装：查看 `kafka_3.6.0/` 目录
3. 检查端口占用：`netstat -ano | findstr :9090`

### Q2: 如何查看系统是否正常运行？

```powershell
# 检查 Java 进程
Get-Process java

# 检查端口占用
netstat -ano | findstr :9090
netstat -ano | findstr :9092
netstat -ano | findstr :2181
```

### Q3: 性能测试连接失败？

**原因**：Netty Server 未启动或未就绪

**解决**：
1. 先运行 `.\start.ps1`
2. 等待 10 秒
3. 再运行 `.\test-performance.ps1`

### Q4: 如何彻底清理？

```powershell
# 1. 停止所有服务
.\stop.bat

# 2. 删除 Kafka 数据（可选）
Remove-Item -Recurse kafka_3.6.0\logs\*

# 3. 删除日志文件
Remove-Item *.log, *.err
```

---

## 🔧 脚本参数说明

### start.ps1 参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|-------|------|
| `-DataSource` | string | simulator | 数据源类型：simulator / tdms / wallpainting |
| `-ShowWindows` | switch | false | 是否显示组件窗口 |

**示例**：
```powershell
# 使用默认参数
.\start.ps1

# 指定数据源
.\start.ps1 -DataSource tdms

# 显示窗口
.\start.ps1 -ShowWindows

# 组合使用
.\start.ps1 -DataSource wallpainting -ShowWindows
```

### test-performance.ps1 参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|-------|------|
| `-DurationSeconds` | int | 70 | 测试持续时间（秒） |
| `-LogPath` | string | highrate.log | 日志文件路径 |
| `-ErrPath` | string | highrate.err | 错误日志路径 |

**示例**：
```powershell
# 使用默认参数（70秒）
.\test-performance.ps1

# 自定义测试时间
.\test-performance.ps1 -DurationSeconds 120

# 自定义日志文件
.\test-performance.ps1 -LogPath test.log -ErrPath test.err
```

---

## 📚 更多资源

- 主文档：`README.md`
- 架构说明：`docs/ARCHITECTURE.md`
- 部署指南：`docs/DEPLOYMENT_GUIDE.md`
- 快速开始：`docs/QUICK_RUN.md`

---

**简化说明**：
- 从 8 个脚本简化为 4 个
- 所有功能通过 `start.ps1` 的参数控制
- 减少混淆，提高易用性

**最后更新**：2025-11-16

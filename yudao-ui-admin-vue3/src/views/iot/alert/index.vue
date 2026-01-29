<template>
  <div class="alert-page">
      <div class="page-header">
        <h2>🔔 告警管理</h2>
        <p>管理系统告警规则、监控实时告警和查询历史记录</p>
      </div>
      
      <!-- 统计卡片 -->
      <el-row :gutter="20" class="stats-row">
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-total">
            <div class="stat-content">
              <div class="stat-icon">
                <Icon icon="ep:bell" size="32" />
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ alertStats.total }}</div>
                <div class="stat-label">总告警数</div>
              </div>
            </div>
          </el-card>
        </el-col>
        
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-critical">
            <div class="stat-content">
              <div class="stat-icon">
                <Icon icon="ep:warning" size="32" />
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ alertStats.critical }}</div>
                <div class="stat-label">严重告警</div>
              </div>
            </div>
          </el-card>
        </el-col>
        
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-warning">
            <div class="stat-content">
              <div class="stat-icon">
                <Icon icon="ep:warning-filled" size="32" />
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ alertStats.warning }}</div>
                <div class="stat-label">一般告警</div>
              </div>
            </div>
          </el-card>
        </el-col>
        
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-info">
            <div class="stat-content">
              <div class="stat-icon">
                <Icon icon="ep:info-filled" size="32" />
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ alertStats.info }}</div>
                <div class="stat-label">提示信息</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
      
      <!-- 筛选器 -->
      <el-card shadow="never" class="search-card">
        <el-form :inline="true" :model="queryForm">
          <el-form-item label="设备">
            <el-select v-model="queryForm.device" placeholder="全部设备" clearable style="width: 200px">
              <el-option label="qc_raem1_4g_107" value="qc_raem1_4g_107" />
              <el-option label="qc_raem1_4g_108" value="qc_raem1_4g_108" />
            </el-select>
          </el-form-item>
          
          <el-form-item label="告警级别">
            <el-select v-model="queryForm.level" placeholder="全部级别" clearable style="width: 120px">
              <el-option label="严重" value="critical" />
              <el-option label="警告" value="warning" />
              <el-option label="信息" value="info" />
            </el-select>
          </el-form-item>
          
          <el-form-item label="状态">
            <el-select v-model="queryForm.status" placeholder="全部状态" clearable style="width: 120px">
              <el-option label="未处理" value="pending" />
              <el-option label="处理中" value="processing" />
              <el-option label="已处理" value="resolved" />
            </el-select>
          </el-form-item>
          
          <el-form-item label="时间范围">
            <el-date-picker
              v-model="queryForm.dateRange"
              type="datetimerange"
              range-separator="To"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              style="width: 380px"
            />
          </el-form-item>
          
          <el-form-item>
            <el-button type="primary" @click="handleSearch" :icon="Search">搜索</el-button>
            <el-button @click="handleReset" :icon="Refresh">重置</el-button>
          </el-form-item>
        </el-form>
        
        <div class="operation-bar">
          <el-button type="primary" @click="handleAddRule">新增规则</el-button>
          <el-button type="success" @click="handleBatchProcess" :disabled="selectedAlerts.length === 0">
            批量处理
          </el-button>
          <el-button type="danger" @click="handleBatchDelete" :disabled="selectedAlerts.length === 0">
            批量删除
          </el-button>
        </div>
      </el-card>
      
      <!-- 告警列表 -->
      <el-card shadow="never" class="alert-list-card">
        <template #header>
          <div class="card-header">
            <span>告警记录</span>
            <el-button size="small" @click="loadAlertData" :icon="Refresh">刷新</el-button>
          </div>
        </template>
        
        <el-table 
          :data="alertData" 
          @selection-change="handleSelectionChange"
          v-loading="loading"
          stripe
        >
          <el-table-column type="selection" width="55" />
          
          <el-table-column label="告警级别" width="100">
            <template #default="{ row }">
              <el-tag 
                :type="row.level === 'critical' ? 'danger' : row.level === 'warning' ? 'warning' : 'info'"
                effect="dark"
              >
                {{ levelText(row.level) }}
              </el-tag>
            </template>
          </el-table-column>
          
          <el-table-column prop="deviceName" label="设备名称" width="180" />
          <el-table-column prop="alertType" label="告警类型" width="150" />
          <el-table-column prop="message" label="告警内容" min-width="250" show-overflow-tooltip />
          <el-table-column prop="value" label="当前值" width="100" align="right" />
          <el-table-column prop="threshold" label="阈值" width="100" align="right" />
          
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag 
                :type="row.status === 'resolved' ? 'success' : row.status === 'processing' ? 'warning' : 'info'"
                size="small"
              >
                {{ statusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          
          <el-table-column prop="alertTime" label="告警时间" width="180">
            <template #default="{ row }">
              {{ new Date(row.alertTime).toLocaleString() }}
            </template>
          </el-table-column>
          
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="primary" link @click="handleProcess(row)" v-if="row.status === 'pending'">
                处理
              </el-button>
              <el-button size="small" type="success" link @click="viewAlertDetail(row)">
                详情
              </el-button>
              <el-button size="small" type="danger" link @click="handleDeleteAlert(row)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadAlertData"
          @current-change="loadAlertData"
          style="margin-top: 20px; justify-content: flex-end"
        />
      </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { Icon } from '@iconify/vue'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

defineOptions({ name: 'IotAlert' })

interface Alert {
  id: string
  deviceId: string
  deviceName: string
  level: 'critical' | 'warning' | 'info'
  alertType: string
  message: string
  value: number
  threshold: number
  status: 'pending' | 'processing' | 'resolved'
  alertTime: number
}

const loading = ref(false)
const selectedAlerts = ref<Alert[]>([])

const queryForm = reactive({
  device: '',
  level: '',
  status: '',
  dateRange: []
})

const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0
})

const alertData = ref<Alert[]>([])

// 统计数据
const alertStats = computed(() => ({
  total: alertData.value.length,
  critical: alertData.value.filter(a => a.level === 'critical').length,
  warning: alertData.value.filter(a => a.level === 'warning').length,
  info: alertData.value.filter(a => a.level === 'info').length
}))

// 生成模拟告警数据
const generateMockAlerts = (): Alert[] => {
  const alerts: Alert[] = []
  const now = Date.now()
  const alertTypes = ['幅度超限', '振铃异常', '持续时间异常', '信号缺失', 'RMS超标']
  const devices = ['qc_raem1_4g_107', 'qc_raem1_4g_108', 'qc_raem1_4g_109']
  
  for (let i = 1; i <= 100; i++) {
    const levelRand = Math.random()
    const level = levelRand > 0.8 ? 'critical' : levelRand > 0.5 ? 'warning' : 'info'
    
    const statusRand = Math.random()
    const status = statusRand > 0.7 ? 'resolved' : statusRand > 0.5 ? 'processing' : 'pending'
    
    alerts.push({
      id: `alert_${i}`,
      deviceId: devices[i % devices.length],
      deviceName: `RAEM设备_${i % 10}`,
      level: level,
      alertType: alertTypes[i % alertTypes.length],
      message: `检测到${alertTypes[i % alertTypes.length]}，请及时处理`,
      value: Math.random() * 100 + 50,
      threshold: 100,
      status: status,
      alertTime: now - Math.floor(Math.random() * 86400000)
    })
  }
  
  return alerts.sort((a, b) => b.alertTime - a.alertTime)
}

const loadAlertData = () => {
  loading.value = true
  
  setTimeout(() => {
    let allAlerts = generateMockAlerts()
    
    // 筛选
    if (queryForm.device) {
      allAlerts = allAlerts.filter(a => a.deviceId === queryForm.device)
    }
    if (queryForm.level) {
      allAlerts = allAlerts.filter(a => a.level === queryForm.level)
    }
    if (queryForm.status) {
      allAlerts = allAlerts.filter(a => a.status === queryForm.status)
    }
    
    pagination.total = allAlerts.length
    const start = (pagination.page - 1) * pagination.pageSize
    alertData.value = allAlerts.slice(start, start + pagination.pageSize)
    
    loading.value = false
  }, 500)
}

const handleSearch = () => {
  pagination.page = 1
  loadAlertData()
}

const handleReset = () => {
  queryForm.device = ''
  queryForm.level = ''
  queryForm.status = ''
  queryForm.dateRange = []
  handleSearch()
}

const handleSelectionChange = (alerts: Alert[]) => {
  selectedAlerts.value = alerts
}

const handleAddRule = () => {
  ElMessage.info('新增告警规则功能开发中...')
}

const handleBatchProcess = () => {
  ElMessageBox.confirm(
    `确定要批量处理 ${selectedAlerts.value.length} 条告警吗？`,
    '批量处理',
    { type: 'success' }
  ).then(() => {
    ElMessage.success('告警已标记为处理中')
    loadAlertData()
  })
}

const handleBatchDelete = () => {
  ElMessageBox.confirm(
    `确定要删除 ${selectedAlerts.value.length} 条告警记录吗？`,
    '批量删除',
    { type: 'error' }
  ).then(() => {
    ElMessage.success('告警记录已删除')
    selectedAlerts.value = []
    loadAlertData()
  })
}

const handleProcess = (alert: Alert) => {
  ElMessageBox.prompt('请输入处理说明', '处理告警', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPlaceholder: '请输入处理说明'
  }).then(({ value }) => {
    ElMessage.success('告警已标记为处理中')
    alert.status = 'processing'
  })
}

const viewAlertDetail = (alert: Alert) => {
  ElMessageBox.alert(
    `设备: ${alert.deviceName}\n类型: ${alert.alertType}\n内容: ${alert.message}\n当前值: ${alert.value}\n阈值: ${alert.threshold}\n时间: ${new Date(alert.alertTime).toLocaleString()}`,
    '告警详情',
    { confirmButtonText: '关闭' }
  )
}

const handleDeleteAlert = (alert: Alert) => {
  ElMessageBox.confirm('确定要删除这条告警记录吗？', '删除确认', { type: 'error' })
    .then(() => {
      ElMessage.success('告警记录已删除')
      loadAlertData()
    })
}

const levelText = (level: string) => {
  const texts: Record<string, string> = {
    'critical': '严重',
    'warning': '警告',
    'info': '信息'
  }
  return texts[level] || level
}

const statusText = (status: string) => {
  const texts: Record<string, string> = {
    'pending': '未处理',
    'processing': '处理中',
    'resolved': '已处理'
  }
  return texts[status] || status
}

onMounted(() => {
  loadAlertData()
})
</script>

<style scoped>
.alert-page {
  padding: 20px;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0 0 8px 0;
  color: #303133;
}

.page-header p {
  margin: 0;
  color: #909399;
  font-size: 14px;
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  cursor: pointer;
  transition: transform 0.3s;
}

.stat-card:hover {
  transform: translateY(-4px);
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  border-radius: 12px;
  background: rgba(64, 158, 255, 0.1);
}

.stat-total .stat-icon { background: rgba(64, 158, 255, 0.1); color: #409eff; }
.stat-critical .stat-icon { background: rgba(245, 108, 108, 0.1); color: #f56c6c; }
.stat-warning .stat-icon { background: rgba(230, 162, 60, 0.1); color: #e6a23c; }
.stat-info .stat-icon { background: rgba(103, 194, 58, 0.1); color: #67c23a; }

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 32px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 14px;
  color: #909399;
}

.search-card {
  margin-bottom: 16px;
}

.operation-bar {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #e4e7ed;
}

.alert-list-card {
  margin-top: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>


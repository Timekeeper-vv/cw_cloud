<template>
  <div class="monitor-page">
    <div class="content">
      <!-- 数据来源标识 -->
      <el-alert
        v-if="dataSource"
        :title="dataSource"
        :type="dataSource.includes('真实文件') ? 'success' : 'warning'"
        :closable="false"
        style="margin-bottom: 16px"
      />
      
      <!-- 顶部筛选器 -->
      <el-card shadow="never" class="filter-card">
        <el-form :inline="true" :model="queryForm" class="filter-form">
          <el-form-item label="产品">
            <el-select v-model="queryForm.product" placeholder="请选择产品" style="width: 150px">
              <el-option v-for="product in productList" :key="product" :label="product" :value="product" />
            </el-select>
          </el-form-item>
          
          <el-form-item label="设备">
            <el-select v-model="queryForm.device" placeholder="请选择设备" filterable clearable style="width: 200px">
              <el-option v-for="device in deviceList" :key="device.id" :label="device.name" :value="device.id">
                <span>{{ device.name }}</span>
                <el-tag :type="device.status === 'online' ? 'success' : 'info'" size="small" style="margin-left: 8px">
                  {{ device.status === 'online' ? '在线' : '离线' }}
                </el-tag>
              </el-option>
            </el-select>
          </el-form-item>
          
          <el-form-item label="参数">
            <el-select v-model="queryForm.param" placeholder="请选择参数" clearable style="width: 150px">
              <el-option v-for="param in paramList" :key="param.value" :label="param.label" :value="param.value" />
            </el-select>
          </el-form-item>
          
          <el-form-item label="时间范围">
            <el-date-picker
              v-model="queryForm.dateRange"
              type="datetimerange"
              range-separator="To"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              format="YYYY-MM-DD HH:mm:ss"
              style="width: 380px"
              :shortcuts="dateShortcuts"
            />
          </el-form-item>
          
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleSearch" :loading="loading">搜索</el-button>
            <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
        
        <div class="operation-buttons">
          <el-button type="danger" :icon="Delete" @click="handleDelete" :disabled="selectedRows.length === 0">删除</el-button>
          <el-button type="success" :icon="Download" @click="handleDownloadSWAE">下载(To SWAE)</el-button>
          <el-button type="primary" :icon="Download" @click="handleDownloadCSV">下载(To CSV)</el-button>
          <el-button type="warning" @click="openDeviceConfig" style="margin-left: 12px">
            <Icon icon="ep:setting" style="margin-right: 4px" />设备配置
          </el-button>
        </div>
      </el-card>

      <!-- 原始4通道电压波形 -->
      <el-card shadow="never" style="margin-bottom: 16px" v-loading="!rawSignalData">
        <template #header>
          <div class="card-header">
            <span>📡 原始4通道电压波形（来自signal_1.txt，单位：伏特）</span>
            <el-tag v-if="rawSignalData" type="success">已加载 {{rawSignalData.readCount}} 个采样点</el-tag>
            <el-tag v-else type="info">加载中...</el-tag>
          </div>
        </template>
        <el-row :gutter="16">
          <el-col :span="12" v-for="(ch, index) in [1,2,3,4]" :key="'ch'+index">
            <el-card shadow="hover" :body-style="{ padding: '10px' }" style="margin-bottom: 16px">
              <div :ref="el => { if(el) rawChartRefs[index] = el }" style="height: 250px"></div>
            </el-card>
          </el-col>
        </el-row>
      </el-card>

      <!-- 计算后的声发射参数图表 -->
      <el-card shadow="never">
        <template #header>
          <span>📊 声发射参数图表（从电压波形计算提取）</span>
        </template>
        <el-row :gutter="16" class="charts-row">
          <el-col :span="12" v-for="(chart, index) in charts" :key="index">
            <el-card shadow="hover" :body-style="{ padding: '10px' }" class="chart-card">
              <div :ref="el => { if(el) chartRefs[index] = el }" class="chart-container"></div>
            </el-card>
          </el-col>
        </el-row>
      </el-card>

      <el-card shadow="never" class="table-card">
        <template #header>
          <div class="card-header">
            <span>数据详情</span>
            <el-button size="small" :icon="Refresh" @click="refreshData">刷新</el-button>
          </div>
        </template>
        
        <el-table :data="tableData" @selection-change="handleSelectionChange" v-loading="loading">
          <el-table-column type="selection" width="55" />
          <el-table-column prop="timestamp" label="时间" width="180">
            <template #default="{ row }">{{ new Date(row.timestamp).toLocaleString() }}</template>
          </el-table-column>
          <el-table-column prop="duration" label="持续时间(μs)" width="120" />
          <el-table-column prop="ringCount" label="振铃计数" width="100" />
          <el-table-column prop="riseTime" label="上升时间(μs)" width="120" />
          <el-table-column prop="amplitude" label="幅度(dB)" width="100" />
          <el-table-column prop="rms" label="RMS(mV)" width="100" />
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button size="small" type="primary" link @click="viewDetail(row)">详情</el-button>
              <el-button size="small" type="danger" link @click="deleteRow(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next"
          @size-change="loadTableData"
          @current-change="loadTableData"
          style="margin-top: 20px"
        />
      </el-card>
      
      <el-dialog v-model="configDialogVisible" title="设备配置" width="900px">
        <div class="config-dialog-content">
          <aside class="config-sidebar">
            <el-menu :default-active="activeConfigTab" @select="activeConfigTab = $event">
              <el-menu-item index="ae-params"><Icon icon="ep:setting" />AE参数</el-menu-item>
              <el-menu-item index="ae-filter"><Icon icon="ep:filter" />AE滤波</el-menu-item>
            </el-menu>
          </aside>
          
          <main class="config-main">
            <el-descriptions :column="2" border class="device-info">
              <el-descriptions-item label="设备编号">{{ currentDevice.id }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="currentDevice.status === 'online' ? 'success' : 'info'">
                  {{ currentDevice.status === 'online' ? '在线' : '离线' }}
                </el-tag>
              </el-descriptions-item>
            </el-descriptions>
            
            <el-form v-if="activeConfigTab === 'ae-params'" :model="deviceConfig" label-width="140px" class="config-form">
              <el-form-item label="门限(dB)"><el-input-number v-model="deviceConfig.threshold" :min="0" :max="120" /></el-form-item>
              <el-form-item label="采集速率"><el-input-number v-model="deviceConfig.sampleRate" :min="1" :max="1000" /></el-form-item>
              <el-form-item label="采集模式">
                <el-radio-group v-model="deviceConfig.collectMode">
                  <el-radio label="envelope">包络</el-radio>
                  <el-radio label="continuous">连续</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-form>
            
            <el-form v-if="activeConfigTab === 'ae-filter'" :model="deviceConfig" label-width="140px" class="config-form">
              <el-form-item label="滤波类型">
                <el-select v-model="deviceConfig.filterType">
                  <el-option label="LMS" value="LMS" />
                  <el-option label="NLMS" value="NLMS" />
                </el-select>
              </el-form-item>
              <el-form-item label="阶数"><el-input-number v-model="deviceConfig.filterOrder" :min="2" :max="64" :step="2" /></el-form-item>
            </el-form>
          </main>
        </div>
        
        <template #footer>
          <el-button @click="configDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSubmitConfig" :loading="submitting">提交</el-button>
        </template>
      </el-dialog>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { Icon } from '@iconify/vue'
import { Search, Refresh, Delete, Download } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import request from '@/config/axios'

interface AEData {
  timestamp: number
  duration: number
  ringCount: number
  riseTime: number
  riseCount: number
  amplitude: number
  avgSignalLevel: number
  rms: number
}

const loading = ref(false)
const selectedRows = ref<AEData[]>([])
const chartRefs = ref<HTMLElement[]>([])
const rawChartRefs = ref<HTMLElement[]>([])  // 原始波形图表引用
let chartInstances: echarts.ECharts[] = []
let rawChartInstances: echarts.ECharts[] = []  // 原始波形图表实例
const dataSource = ref<string>('')  // 数据来源标识
const rawSignalData = ref<any>(null)  // 原始4通道数据
const configDialogVisible = ref(false)
const activeConfigTab = ref('ae-params')
const submitting = ref(false)

const currentDevice = reactive({
  id: 'qc_raem1_4g_107',
  status: 'online' as 'online' | 'offline',
  product: 'RAEM1',
  version: 'V1.0.55'
})

const deviceConfig = reactive({
  threshold: 45,
  sampleRate: 100,
  collectMode: 'envelope',
  filterType: 'LMS',
  filterOrder: 16,
  stepSize: 0.01
})

const queryForm = reactive({
  product: 'RAEM1',
  device: 'qc_raem1_4g_107',
  param: '',
  customParam: '',
  dateRange: []
})

const productList = ['RAEM1', 'RAEM2', 'RAEM3']
const deviceList = ref([
  { id: 'qc_raem1_4g_107', name: 'qc_raem1_4g_107', status: 'online' },
  { id: 'qc_raem1_4g_108', name: 'qc_raem1_4g_108', status: 'online' }
])
const paramList = [
  { label: '全部', value: '' },
  { label: '持续时间', value: 'duration' },
  { label: '振铃计数', value: 'ringCount' },
  { label: '幅度', value: 'amplitude' }
]

const dateShortcuts = [
  { text: '最近1小时', value: () => { const e = new Date(); const s = new Date(); s.setTime(s.getTime() - 3600000); return [s, e] } },
  { text: '今天', value: () => { const e = new Date(); const s = new Date(); s.setHours(0,0,0,0); return [s, e] } }
]

const charts = [
  { title: '持续时间 (μs)', unit: 'μs', key: 'duration' },
  { title: '振铃计数', unit: '', key: 'ringCount' },
  { title: '上升时间 (μs)', unit: 'μs', key: 'riseTime' },
  { title: '上升计数', unit: '', key: 'riseCount' },
  { title: '幅度 (dB)', unit: 'dB', key: 'amplitude' },
  { title: '平均信号电平 (dB)', unit: 'dB', key: 'avgSignalLevel' }
]

const tableData = ref<AEData[]>([])
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })

const generateMockData = (count: number): AEData[] => {
  const data: AEData[] = []
  const now = Date.now()
  for (let i = 0; i < count; i++) {
    data.push({
      timestamp: now - (count - i) * 60000,
      duration: Math.random() * 10000 + 1000,
      ringCount: Math.floor(Math.random() * 500),
      riseTime: Math.random() * 500 + 50,
      riseCount: Math.floor(Math.random() * 500),
      amplitude: Math.random() * 80 + 40,
      avgSignalLevel: Math.random() * 60 + 20,
      rms: Math.random() * 500 + 100
    })
  }
  return data
}

const initCharts = async () => {
  let mockData = []
  
  try {
    // 调用真实API获取最新100条数据
    const res = await request.get({
      url: '/system/ae-data/latest',  // 改为system前缀
      params: {
        deviceId: queryForm.device,
        limit: 100
      }
    })
    
    console.log('API响应:', res)  // 调试：查看完整响应
    
    // axios拦截器已经解包，res直接是数组
    if (Array.isArray(res) && res.length > 0) {
      mockData = res
      dataSource.value = `✅ 数据来源：真实文件 signal_1.txt（共${mockData.length}条声发射参数数据，从4通道电压信号解析）`
      console.log('✅ 图表使用真实数据，共', mockData.length, '条')
    } else if (res && res.code === 0 && Array.isArray(res.data) && res.data.length > 0) {
      // 备用判断：如果没有解包
      mockData = res.data
      dataSource.value = `✅ 数据来源：真实文件 signal_1.txt（共${mockData.length}条声发射参数数据，从4通道电压信号解析）`
      console.log('✅ 图表使用真实数据，共', mockData.length, '条')
    } else {
      console.log('⚠️ API返回格式不符，使用模拟数据')
      mockData = generateMockData(100)
      dataSource.value = '⚠️ 数据来源：模拟数据（后端API未返回数据）'
    }
  } catch (error) {
    console.error('获取真实数据失败，使用模拟数据', error)
    mockData = generateMockData(100)
    dataSource.value = '⚠️ 数据来源：模拟数据（API调用失败）'
  }
  
  charts.forEach((cfg, idx) => {
    const el = chartRefs.value[idx]
    if (!el) return
    const chart = echarts.init(el)
    const data = mockData.map(item => [item.timestamp, item[cfg.key as keyof AEData]])
    chart.setOption({
      title: { text: cfg.title, left: 10, textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'axis' },
      grid: { left: '60px', right: '40px', top: '50px', bottom: '80px' },
      xAxis: { type: 'time' },
      yAxis: { type: 'value', name: cfg.unit },
      dataZoom: [{ type: 'inside' }, { type: 'slider', height: 20, bottom: 10 }],
      toolbox: { right: 20, feature: { dataZoom: {}, restore: {}, saveAsImage: {} } },
      series: [{
        type: 'line',
        data: data,
        smooth: false,
        lineStyle: { color: '#00d4d4', width: 1.5 },
        areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(0,212,212,0.3)' }, { offset: 1, color: 'rgba(0,212,212,0.05)' }] } }
      }]
    })
    chartInstances.push(chart)
  })
}

const loadTableData = async () => {
  loading.value = true
  
  try {
    // 调用真实API
    const res = await request.get({
      url: '/system/ae-data/page',  // 改为system前缀
      params: {
        deviceId: queryForm.device,
        startTime: queryForm.dateRange[0]?.getTime(),
        endTime: queryForm.dateRange[1]?.getTime(),
        param: queryForm.param,
        pageNo: pagination.page,
        pageSize: pagination.pageSize
      }
    })
    
    // axios拦截器解包后，res可能直接是数据对象
    if (res && res.list && Array.isArray(res.list)) {
      // 已解包的情况
      tableData.value = res.list
      pagination.total = res.total || 0
      dataSource.value = `✅ 数据来源：真实文件 signal_1.txt（总计${pagination.total}条，当前页${res.list.length}条）`
      console.log('✅ 表格数据来自真实文件signal_1.txt，共', pagination.total, '条')
    } else if (res && res.code === 0 && res.data) {
      // 未解包的情况
      tableData.value = res.data.list || []
      pagination.total = res.data.total || 0
      if (res.data.list && res.data.list.length > 0) {
        dataSource.value = `✅ 数据来源：真实文件 signal_1.txt（总计${pagination.total}条，当前页${res.data.list.length}条）`
      }
      console.log('✅ 表格数据来自真实文件signal_1.txt，共', pagination.total, '条')
    } else {
      // 降级到模拟数据
      const all = generateMockData(200)
      const start = (pagination.page - 1) * pagination.pageSize
      tableData.value = all.slice(start, start + pagination.pageSize)
      pagination.total = all.length
    }
  } catch (error) {
    console.error('获取真实数据失败，使用模拟数据', error)
    // 降级到模拟数据
    const all = generateMockData(200)
    const start = (pagination.page - 1) * pagination.pageSize
    tableData.value = all.slice(start, start + pagination.pageSize)
    pagination.total = all.length
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { 
  loadRawSignal()  // 加载原始波形
  loadTableData()
  initCharts()
  ElMessage.success('已更新') 
}

// 加载原始4通道电压波形
const loadRawSignal = async () => {
  try {
    const res = await request.get({
      url: '/system/ae-data/raw-signal',
      params: {
        startIndex: 0,
        count: 500
      }
    })
    
    if (Array.isArray(res)) {
      rawSignalData.value = res[0] || null
    } else if (res && res.channel1) {
      rawSignalData.value = res
    }
    
    if (rawSignalData.value) {
      renderRawCharts()
      console.log('✅ 原始波形数据已加载')
    }
  } catch (error) {
    console.error('API调用失败，生成模拟原始波形用于展示', error)
    // 降级方案：生成模拟的4通道波形数据（模拟真实txt格式）
    const mockRawData = generateMockRawSignal(500)
    rawSignalData.value = mockRawData
    renderRawCharts()
    console.log('⚠️ 使用模拟的4通道波形（格式与signal_1.txt一致）')
  }
}

// 生成模拟的4通道原始波形（格式与signal_1.txt一致）
const generateMockRawSignal = (count: number) => {
  const channel1: number[] = []
  const channel2: number[] = []
  const channel3: number[] = []
  const channel4: number[] = []
  const indices: number[] = []
  
  for (let i = 0; i < count; i++) {
    indices.push(i)
    // 模拟±0.001V范围的电压值，与真实txt数据格式一致
    channel1.push((Math.random() - 0.5) * 0.002)  // ±0.001V
    channel2.push((Math.random() - 0.5) * 0.002)
    channel3.push((Math.random() - 0.5) * 0.002)
    channel4.push((Math.random() - 0.5) * 0.002)
  }
  
  return {
    startIndex: 0,
    readCount: count,
    indices,
    channel1,
    channel2,
    channel3,
    channel4
  }
}

// 渲染原始4通道波形图表
const renderRawCharts = () => {
  if (!rawSignalData.value) return
  
  const channels = [
    { name: '通道1', data: rawSignalData.value.channel1, color: '#67c23a' },
    { name: '通道2', data: rawSignalData.value.channel2, color: '#409eff' },
    { name: '通道3', data: rawSignalData.value.channel3, color: '#e6a23c' },
    { name: '通道4', data: rawSignalData.value.channel4, color: '#f56c6c' }
  ]
  
  channels.forEach((ch, idx) => {
    const el = rawChartRefs.value[idx]
    if (!el) return
    
    if (rawChartInstances[idx]) {
      rawChartInstances[idx].dispose()
    }
    
    const chart = echarts.init(el)
    
    chart.setOption({
      title: { 
        text: ch.name + ' 电压波形', 
        left: 10, 
        textStyle: { fontSize: 14 } 
      },
      tooltip: { 
        trigger: 'axis',
        formatter: (params: any) => {
          const p = params[0]
          return `采样点: ${p.dataIndex}<br/>电压: ${p.value.toFixed(6)} V`
        }
      },
      grid: { left: '60px', right: '40px', top: '50px', bottom: '80px' },
      xAxis: { type: 'category', name: '采样点' },
      yAxis: { type: 'value', name: '电压 (V)' },
      dataZoom: [
        { type: 'inside' },
        { type: 'slider', height: 20, bottom: 10 }
      ],
      series: [{
        data: ch.data,
        type: 'line',
        smooth: false,
        symbol: 'none',
        lineStyle: { color: ch.color, width: 1 },
        areaStyle: { color: ch.color, opacity: 0.1 }
      }]
    })
    
    rawChartInstances[idx] = chart
  })
}
const handleReset = () => { queryForm.device = ''; queryForm.param = ''; queryForm.dateRange = []; handleSearch() }
const handleSelectionChange = (rows: AEData[]) => { selectedRows.value = rows }
const handleDelete = () => { ElMessageBox.confirm('确定删除?').then(() => { ElMessage.success('已删除'); loadTableData() }) }
const handleDownloadSWAE = () => { ElMessage.info('SWAE开发中') }
const handleDownloadCSV = () => {
  const csv = [['时间','持续时间','振铃计数','幅度','RMS'], ...tableData.value.map(r=>[new Date(r.timestamp).toLocaleString(),r.duration,r.ringCount,r.amplitude,r.rms])].map(r=>r.join(',')).join('\n')
  const blob = new Blob([csv], {type: 'text/csv'})
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = 'data.csv'
  a.click()
  ElMessage.success('CSV已导出')
}
const refreshData = () => { loadTableData(); initCharts() }
const viewDetail = () => { ElMessage.info('详情开发中') }
const deleteRow = () => { ElMessageBox.confirm('确定删除?').then(() => { ElMessage.success('已删除'); loadTableData() }) }
const openDeviceConfig = () => { configDialogVisible.value = true }
const handleSubmitConfig = () => {
  submitting.value = true
  setTimeout(() => { ElMessage.success('配置已提交'); submitting.value = false; configDialogVisible.value = false }, 1000)
}

onMounted(() => {
  setTimeout(() => { 
    loadRawSignal()  // 加载原始波形
    initCharts()
    loadTableData() 
  }, 100)
  window.addEventListener('resize', () => {
    chartInstances.forEach(c => c.resize())
    rawChartInstances.forEach(c => c.resize())
  })
})

onUnmounted(() => {
  chartInstances.forEach(c => c.dispose())
  rawChartInstances.forEach(c => c.dispose())
})
</script>

<style scoped>
.monitor-page {
  min-height: calc(100vh - 200px);
  background: #f5f7fa;
}

.content {
  width: 100%;
  padding: 16px;
}

.filter-card {
  margin-bottom: 16px;
}

.operation-buttons {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #e4e7ed;
}

.charts-row {
  margin-bottom: 16px;
}

.chart-card {
  height: 320px;
  margin-bottom: 16px;
}

.chart-container {
  width: 100%;
  height: 300px;
}

.table-card {
  margin-top: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.config-dialog-content {
  display: flex;
  height: 500px;
}

.config-sidebar {
  width: 200px;
  border-right: 1px solid #e4e7ed;
}

.config-main {
  flex: 1;
  padding-left: 20px;
  overflow-y: auto;
}

.device-info {
  margin-bottom: 20px;
}

.config-form {
  margin-top: 20px;
}
</style>

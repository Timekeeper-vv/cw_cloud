<template>
  <ContentWrap title="分布式滤波监控">
    <el-row :gutter="20">
      <!-- 左侧控制面板 -->
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>🚀 分布式控制面板</span>
            </div>
          </template>

          <el-form label-width="90px">
            <el-form-item label="连接状态">
              <el-tag :type="wsConnected ? 'success' : 'danger'" size="large">
                {{ wsConnected ? '已连接' : '未连接' }}
              </el-tag>
            </el-form-item>

            <el-form-item label="Worker数量">
              <el-input-number v-model="numWorkers" :min="2" :max="8" :disabled="wsConnected" />
            </el-form-item>

            <el-form-item label="数据源">
              <el-select v-model="selectedSource" placeholder="选择数据源" :disabled="wsConnected">
                <el-option label="📁 Signal-1 (TDMS)" value="signal-1" />
                <el-option label="📁 Signal-2 (TDMS)" value="signal-2" />
              </el-select>
            </el-form-item>

            <el-form-item>
              <el-button 
                type="primary" 
                :icon="wsConnected ? 'Close' : 'Connection'" 
                @click="toggleConnection"
                style="width: 100%"
              >
                {{ wsConnected ? '断开连接' : '开始监控' }}
              </el-button>
            </el-form-item>
          </el-form>

          <el-divider />

          <!-- 分布式处理统计 -->
          <div class="stats-panel">
            <h4>📊 分布式处理统计</h4>
            
            <!-- 总体性能 -->
            <div class="performance-card distributed">
              <div class="perf-item">
                <div class="perf-label">⚡ 总处理速度</div>
                <div class="perf-value" :style="{ color: stats.totalThroughput > 1000000 ? '#67C23A' : '#E6A23C' }">
                  {{ formatThroughput(stats.totalThroughput) }}
                </div>
              </div>
              <div class="perf-item">
                <div class="perf-label">⏱️ 端到端延迟</div>
                <div class="perf-value">{{ stats.e2eLatency.toFixed(1) }} ms</div>
              </div>
            </div>

            <!-- Worker状态 -->
            <div class="worker-status">
              <div class="worker-header">🔧 Worker 状态</div>
              <div v-for="(worker, id) in workerStats" :key="id" class="worker-item">
                <span class="worker-name">{{ id }}</span>
                <el-progress 
                  :percentage="worker.load" 
                  :color="getLoadColor(worker.load)"
                  :stroke-width="8"
                  style="flex: 1; margin: 0 10px;"
                />
                <span class="worker-rate">{{ formatThroughput(worker.rate) }}</span>
              </div>
            </div>

            <!-- 重排统计 -->
            <div class="reorder-stats">
              <div class="reorder-header">🔄 重排统计</div>
              <el-row :gutter="10">
                <el-col :span="12">
                  <div class="stat-item">
                    <div class="stat-label">缓冲区大小</div>
                    <div class="stat-value">{{ stats.bufferSize }}</div>
                  </div>
                </el-col>
                <el-col :span="12">
                  <div class="stat-item">
                    <div class="stat-label">乱序包数</div>
                    <div class="stat-value" style="color: #E6A23C;">{{ stats.outOfOrder }}</div>
                  </div>
                </el-col>
              </el-row>
              <el-row :gutter="10">
                <el-col :span="12">
                  <div class="stat-item">
                    <div class="stat-label">跳过包数</div>
                    <div class="stat-value" style="color: #F56C6C;">{{ stats.skipped }}</div>
                  </div>
                </el-col>
                <el-col :span="12">
                  <div class="stat-item">
                    <div class="stat-label">当前序号</div>
                    <div class="stat-value">{{ stats.currentSequence }}</div>
                  </div>
                </el-col>
              </el-row>
            </div>

            <!-- SNR统计 -->
            <div class="snr-card">
              <div class="snr-header">📈 信噪比分析</div>
              <el-row :gutter="10">
                <el-col :span="12">
                  <div class="snr-item">
                    <div class="snr-label">滤波前 SNR</div>
                    <div class="snr-value" style="color: #F56C6C;">{{ stats.snrBefore.toFixed(2) }} dB</div>
                  </div>
                </el-col>
                <el-col :span="12">
                  <div class="snr-item">
                    <div class="snr-label">滤波后 SNR</div>
                    <div class="snr-value" style="color: #67C23A;">{{ stats.snrAfter.toFixed(2) }} dB</div>
                  </div>
                </el-col>
              </el-row>
              <div class="snr-improvement">
                <span>SNR 改善:</span>
                <span class="improvement-value" :style="{ color: stats.snrImprovement > 0 ? '#67C23A' : '#F56C6C' }">
                  {{ stats.snrImprovement > 0 ? '+' : '' }}{{ stats.snrImprovement.toFixed(2) }} dB
                </span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧图表区域 -->
      <el-col :span="18">
        <!-- 实时波形图 -->
        <el-card shadow="hover" style="margin-bottom: 20px">
          <template #header>
            <div class="card-header">
              <span>实时波形对比 (分布式处理)</span>
              <el-tag type="success" size="small" v-if="wsConnected">
                {{ numWorkers }} Workers
              </el-tag>
            </div>
          </template>
          <div id="distributed-waveform-chart" style="height: 400px"></div>
        </el-card>

        <!-- Worker负载分布 -->
        <el-card shadow="hover" style="margin-bottom: 20px">
          <template #header>
            <div class="card-header">
              <span>🔧 Worker 负载分布</span>
            </div>
          </template>
          <div id="worker-load-chart" style="height: 250px"></div>
        </el-card>

        <!-- 处理延迟分布 -->
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>⏱️ 处理延迟分布</span>
            </div>
          </template>
          <div id="latency-chart" style="height: 250px"></div>
        </el-card>
      </el-col>
    </el-row>
  </ContentWrap>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import * as echarts from 'echarts'

const wsConnected = ref(false)
const numWorkers = ref(3)
const selectedSource = ref('signal-1')

let ws: WebSocket | null = null
let waveformChart: echarts.ECharts | null = null
let workerLoadChart: echarts.ECharts | null = null
let latencyChart: echarts.ECharts | null = null

const stats = ref({
  totalThroughput: 0,
  e2eLatency: 0,
  bufferSize: 0,
  outOfOrder: 0,
  skipped: 0,
  currentSequence: 0,
  snrBefore: 0,
  snrAfter: 0,
  snrImprovement: 0,
  packetCount: 0,
})

const workerStats = ref<Record<string, { rate: number; load: number; lastUpdate: number }>>({})

// 延迟历史
const latencyHistory = ref<number[]>([])
const MAX_LATENCY_HISTORY = 100

// 格式化处理速度
const formatThroughput = (value: number): string => {
  if (value >= 1000000) return `${(value / 1000000).toFixed(2)} M/s`
  if (value >= 1000) return `${(value / 1000).toFixed(1)} K/s`
  return `${value.toFixed(0)} /s`
}

// 获取负载颜色
const getLoadColor = (load: number): string => {
  if (load > 80) return '#F56C6C'
  if (load > 50) return '#E6A23C'
  return '#67C23A'
}

// 切换连接
const toggleConnection = () => {
  if (wsConnected.value) {
    disconnect()
  } else {
    connect()
  }
}

// 连接WebSocket
const connect = async () => {
  try {
    // 先启动数据源
    const apiBase = `${window.location.origin}/api/signal-producer`
    const response = await fetch(`${apiBase}/start`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ source: selectedSource.value })
    })
    
    if (!response.ok) {
      throw new Error('启动数据源失败')
    }
    
    // 连接分布式聚合服务的WebSocket（走Nginx代理）
    const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const wsUrl = `${wsProtocol}//${window.location.host}/distributed`
    console.log('连接分布式WebSocket:', wsUrl)
    
    ws = new WebSocket(wsUrl)
    
    ws.onopen = () => {
      wsConnected.value = true
      ElMessage.success('已连接到分布式滤波服务')
      
      // 初始化Worker状态
      for (let i = 0; i < numWorkers.value; i++) {
        workerStats.value[`filter-worker-${i}`] = { rate: 0, load: 0, lastUpdate: Date.now() }
      }
    }
    
    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        if (data.type === 'signal-data') {
          handleData(data)
        } else if (data.type === 'welcome') {
          console.log('分布式服务欢迎消息:', data)
        }
      } catch (e) {
        console.error('解析消息失败:', e)
      }
    }
    
    ws.onerror = (error) => {
      console.error('WebSocket错误:', error)
      ElMessage.error('连接错误')
    }
    
    ws.onclose = () => {
      wsConnected.value = false
      ElMessage.warning('连接已断开')
    }
    
  } catch (error) {
    console.error('连接失败:', error)
    ElMessage.error('连接失败: ' + (error as Error).message)
  }
}

// 断开连接
const disconnect = async () => {
  try {
    const apiBase = `${window.location.origin}/api/signal-producer`
    await fetch(`${apiBase}/stop`, { method: 'POST' })
  } catch (e) {
    console.error('停止数据源失败:', e)
  }
  
  if (ws) {
    ws.close(1000, 'User disconnected')
    ws = null
  }
  wsConnected.value = false
  ElMessage.info('已断开连接')
}

// 处理数据
const handleData = (data: any) => {
  stats.value.packetCount++
  stats.value.currentSequence = data.sequence_id || 0
  
  // 更新SNR
  stats.value.snrBefore = data.snrBefore || data.snr_before || 0
  stats.value.snrAfter = data.snrAfter || data.snr_after || 0
  stats.value.snrImprovement = data.snrImprovement || data.snr_improvement || 0
  
  // 计算端到端延迟（处理秒/毫秒格式）
  const now = Date.now()
  let dispatchTime = data.dispatch_time || data.timestamp || now
  // 如果时间戳是秒格式（小于 1e12），转换为毫秒
  if (dispatchTime < 1e12) {
    dispatchTime = dispatchTime * 1000
  }
  const e2eLatency = Math.max(0, now - dispatchTime)
  stats.value.e2eLatency = Math.min(e2eLatency, 10000) // 限制最大10秒
  
  // 记录延迟历史
  if (e2eLatency < 10000) {
    latencyHistory.value.push(e2eLatency)
    if (latencyHistory.value.length > MAX_LATENCY_HISTORY) {
      latencyHistory.value.shift()
    }
  }
  
  // 更新Worker统计（兼容 worker-0 和 filter-worker-0 格式）
  let workerId = data.worker_id || ''
  // 标准化 worker ID
  if (workerId && !workerId.startsWith('filter-')) {
    workerId = `filter-${workerId}`
  }
  if (workerId && workerStats.value[workerId]) {
    const worker = workerStats.value[workerId]
    const timeDelta = (now - worker.lastUpdate) / 1000
    if (timeDelta > 0.1) {
      const sampleCount = data.sampleCount || data.sample_count || (data.filteredSamples?.length || 0)
      worker.rate = sampleCount / timeDelta
      worker.load = Math.min(100, (worker.rate / 500000) * 100) // 假设单Worker最大50万/s
      worker.lastUpdate = now
    }
  }
  
  // 计算总吞吐量
  let totalRate = 0
  Object.values(workerStats.value).forEach(w => {
    totalRate += w.rate
  })
  stats.value.totalThroughput = totalRate
  
  // 更新图表
  updateWaveformChart(data)
  updateWorkerLoadChart()
  updateLatencyChart()
}

// 更新波形图
const updateWaveformChart = (data: any) => {
  if (!waveformChart) return
  
  // 兼容驼峰和下划线命名
  const original = data.originalSamples || data.original_samples || data.samples || []
  const filtered = data.filteredSamples || data.filtered_samples || data.filtered || []
  
  if (original.length === 0 && filtered.length === 0) return
  
  // 降采样
  const maxPoints = 200
  const factor = Math.max(1, Math.floor(original.length / maxPoints))
  const downsampledOriginal = original.filter((_: any, i: number) => i % factor === 0)
  const downsampledFiltered = filtered.filter((_: any, i: number) => i % factor === 0)
  const timeLabels = downsampledOriginal.map((_: any, i: number) => (i * factor / 100).toFixed(1))
  
  const option = {
    title: {
      text: `分布式处理 | Worker: ${data.worker_id || 'N/A'} | 序号: ${data.sequence_id || 0}`,
      subtext: `SNR改善: ${stats.value.snrImprovement > 0 ? '+' : ''}${stats.value.snrImprovement.toFixed(1)}dB | 总速度: ${formatThroughput(stats.value.totalThroughput)}`,
      left: 'center',
      subtextStyle: { color: '#67C23A', fontSize: 13, fontWeight: 'bold' }
    },
    tooltip: { trigger: 'axis' },
    legend: { 
      data: ['原始信号', '滤波后信号'], 
      bottom: 5,
      selected: { '原始信号': true, '滤波后信号': true }
    },
    grid: { left: '8%', right: '5%', bottom: '15%', top: '18%' },
    xAxis: { type: 'category', data: timeLabels, name: '时间 (ms)' },
    yAxis: { type: 'value', name: '幅值' },
    series: [
      { 
        name: '原始信号', 
        type: 'line', 
        data: downsampledOriginal, 
        z: 1,
        lineStyle: { color: '#5470c6', width: 1.5, opacity: 0.8 }, 
        showSymbol: false 
      },
      { 
        name: '滤波后信号', 
        type: 'line', 
        data: downsampledFiltered, 
        z: 2,
        lineStyle: { color: '#91cc75', width: 2, opacity: 0.8 }, 
        showSymbol: false 
      }
    ],
    animation: false
  }
  
  waveformChart.setOption(option)
}

// 更新Worker负载图
const updateWorkerLoadChart = () => {
  if (!workerLoadChart) return
  
  const workers = Object.keys(workerStats.value)
  const loads = workers.map(w => workerStats.value[w].load)
  const rates = workers.map(w => workerStats.value[w].rate)
  
  const option = {
    tooltip: { trigger: 'axis' },
    legend: { data: ['负载 %', '速率'], bottom: 5 },
    grid: { left: '10%', right: '10%', bottom: '15%', top: '10%' },
    xAxis: { type: 'category', data: workers },
    yAxis: [
      { type: 'value', name: '负载 %', max: 100 },
      { type: 'value', name: '速率', position: 'right' }
    ],
    series: [
      {
        name: '负载 %',
        type: 'bar',
        data: loads,
        itemStyle: {
          color: (params: any) => getLoadColor(params.value)
        }
      },
      {
        name: '速率',
        type: 'line',
        yAxisIndex: 1,
        data: rates,
        lineStyle: { color: '#409EFF', width: 2 },
        symbol: 'circle',
        symbolSize: 8
      }
    ],
    animation: false
  }
  
  workerLoadChart.setOption(option)
}

// 更新延迟图
const updateLatencyChart = () => {
  if (!latencyChart) return
  
  const option = {
    tooltip: { trigger: 'axis' },
    grid: { left: '10%', right: '5%', bottom: '10%', top: '10%' },
    xAxis: { type: 'category', data: latencyHistory.value.map((_, i) => i), show: false },
    yAxis: { type: 'value', name: '延迟 (ms)' },
    series: [{
      type: 'line',
      data: latencyHistory.value,
      areaStyle: { color: 'rgba(64, 158, 255, 0.3)' },
      lineStyle: { color: '#409EFF', width: 1 },
      showSymbol: false
    }],
    animation: false
  }
  
  latencyChart.setOption(option)
}

// 初始化图表
onMounted(async () => {
  await nextTick()
  
  const waveformEl = document.getElementById('distributed-waveform-chart')
  if (waveformEl) waveformChart = echarts.init(waveformEl)
  
  const workerEl = document.getElementById('worker-load-chart')
  if (workerEl) workerLoadChart = echarts.init(workerEl)
  
  const latencyEl = document.getElementById('latency-chart')
  if (latencyEl) latencyChart = echarts.init(latencyEl)
  
  window.addEventListener('resize', () => {
    waveformChart?.resize()
    workerLoadChart?.resize()
    latencyChart?.resize()
  })
})

onUnmounted(() => {
  disconnect()
  waveformChart?.dispose()
  workerLoadChart?.dispose()
  latencyChart?.dispose()
})
</script>

<style scoped lang="scss">
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stats-panel {
  margin-top: 20px;
  
  h4 {
    margin-bottom: 15px;
    color: #303133;
  }
}

.performance-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 8px;
  padding: 15px;
  margin-bottom: 15px;
  display: flex;
  justify-content: space-around;
  
  &.distributed {
    background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%);
  }
  
  .perf-item {
    text-align: center;
    
    .perf-label {
      font-size: 12px;
      color: rgba(255, 255, 255, 0.8);
      margin-bottom: 5px;
    }
    
    .perf-value {
      font-size: 18px;
      font-weight: bold;
      color: #fff;
    }
  }
}

.worker-status {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 15px;
  
  .worker-header {
    font-size: 13px;
    font-weight: bold;
    color: #606266;
    margin-bottom: 10px;
  }
  
  .worker-item {
    display: flex;
    align-items: center;
    margin-bottom: 8px;
    
    .worker-name {
      font-size: 11px;
      color: #909399;
      width: 80px;
    }
    
    .worker-rate {
      font-size: 11px;
      color: #606266;
      width: 60px;
      text-align: right;
    }
  }
}

.reorder-stats {
  background: #fef0f0;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 15px;
  border: 1px solid #fde2e2;
  
  .reorder-header {
    font-size: 13px;
    font-weight: bold;
    color: #f56c6c;
    margin-bottom: 10px;
  }
}

.snr-card {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 15px;
  border: 1px solid #e4e7ed;
  
  .snr-header {
    font-size: 13px;
    font-weight: bold;
    color: #606266;
    margin-bottom: 10px;
  }
  
  .snr-item {
    text-align: center;
    padding: 8px 0;
    
    .snr-label { font-size: 11px; color: #909399; }
    .snr-value { font-size: 16px; font-weight: bold; margin-top: 3px; }
  }
  
  .snr-improvement {
    text-align: center;
    padding-top: 10px;
    border-top: 1px dashed #dcdfe6;
    margin-top: 8px;
    font-size: 13px;
    color: #606266;
    
    .improvement-value {
      font-size: 18px;
      font-weight: bold;
      margin-left: 8px;
    }
  }
}

.stat-item {
  background: #fff;
  border-radius: 6px;
  padding: 10px;
  text-align: center;
  margin-bottom: 10px;
  
  .stat-label { font-size: 11px; color: #909399; }
  .stat-value { font-size: 14px; font-weight: 600; color: #303133; margin-top: 3px; }
}
</style>

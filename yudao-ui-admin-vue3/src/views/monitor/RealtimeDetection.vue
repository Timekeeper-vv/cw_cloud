<template>
  <ContentWrap title="实时检测">
    <div class="toolbar">
      <el-upload
        :http-request="handleUpload"
        :show-file-list="false"
        accept=".tdms"
        :before-upload="() => true"
      >
        <el-button type="primary" :loading="uploading">上传 TDMS 文件</el-button>
      </el-upload>
      
      <!-- 播放控制 -->
      <div class="playback-controls" v-if="uploadResult">
        <el-button 
          :type="isPlaying ? 'success' : 'info'" 
          :disabled="!uploadResult"
        >
          <el-icon style="margin-right: 4px;">
            <VideoPlay v-if="isPlaying" />
          </el-icon>
          {{ isPlaying ? 'Flink 流式处理中...' : '等待连接...' }}
        </el-button>
        <span class="playback-info">
          数据点数: {{ currentPlaybackIndex }} |
          处理速度: <span style="color: var(--el-color-primary); font-weight: 600;">{{ processingSpeed.toFixed(1) }} k/s</span>
        </span>
      </div>

      <!-- 异常检测区域暂时不用，直接整体隐藏；若以后需要，再把 v-if 改回 jobInfo 即可 -->
      <div class="anomaly-control" v-if="false">
        <el-switch v-model="anomalyEnabled" active-text="异常检测" @change="updateAnomaly" />
        <el-slider
          v-model="anomalyThreshold"
          :min="1"
          :max="thresholdMax"
          :step="thresholdMax / 100"
          style="width: 240px"
          @change="updateAnomaly"
          :disabled="!anomalyEnabled"
        />
        <span class="threshold-value">阈值: {{ anomalyThreshold.toFixed(2) }}</span>
      </div>
    </div>

    <el-row :gutter="12">
      <el-col :span="16">
        <!-- 控制面板 -->
        <el-card class="control-card" shadow="hover">
          <template #header>
            <div class="card-header">
              控制面板
            </div>
          </template>
          <el-form label-width="100px" size="small">
            <el-form-item label="设备选择">
              <el-select v-model="selectedDevice" placeholder="请选择设备">
                <el-option label="设备 001" value="device-001" />
                <el-option label="设备 002" value="device-002" />
              </el-select>
            </el-form-item>
            <el-form-item label="滤波器类型">
              <el-select v-model="filterType">
                <el-option label="卡尔曼滤波 (Kalman)" value="KALMAN" />
                <el-option label="LMS 自适应滤波" value="LMS" />
              </el-select>
            </el-form-item>
            <template v-if="filterType === 'KALMAN'">
              <el-form-item label="Q">
                <el-input-number v-model="kalmanParams.kalmanQ" :min="0" :step="1e-5" :controls="true" style="width: 100%" />
              </el-form-item>
              <el-form-item label="R">
                <el-input-number v-model="kalmanParams.kalmanR" :min="0" :step="0.01" :controls="true" style="width: 100%" />
              </el-form-item>
              <el-form-item label="P0">
                <el-input-number v-model="kalmanParams.kalmanP0" :min="0" :step="0.1" :controls="true" style="width: 100%" />
              </el-form-item>
              <el-form-item label="x0-N">
                <el-input-number v-model="kalmanParams.kalmanX0N" :min="1" :max="100" :step="1" :controls="true" style="width: 100%" />
              </el-form-item>
            </template>
          </el-form>
        </el-card>

        <div v-if="!uploadResult" class="placeholder">
          请先上传有效 TDMS 文件
        </div>
        <div v-else ref="chartContainer" id="realtime-chart" style="height: 420px" v-loading="analyzing"></div>
      </el-col>

      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              实时统计
            </div>
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="通道名称">
              {{ metrics.channelName || 'N/A' }}
            </el-descriptions-item>
            <el-descriptions-item label="数据点数">
              {{ metrics.totalPoints }}
            </el-descriptions-item>
            <el-descriptions-item label="异常点数">
              <span style="color: var(--el-color-danger); font-weight: 600;">
                {{ metrics.anomalyCount }}
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="单位">
              {{ metrics.channelUnit }}
            </el-descriptions-item>
            <el-descriptions-item label="实时处理速度" v-if="isPlaying">
              <span style="color: var(--el-color-primary); font-weight: 600;">
                {{ processingSpeed.toFixed(1) }} k/s
              </span>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </ContentWrap>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { uploadTdms, type MonitorUploadResp, type MonitorStreamMessage, type FilterType, type KalmanParams } from '@/api/monitor'
import { ElMessage } from 'element-plus'
import { VideoPlay, VideoPause } from '@element-plus/icons-vue'

const uploading = ref(false)
const analyzing = ref(false)
const uploadResult = ref<MonitorUploadResp | null>(null)
const chartRef = ref<echarts.ECharts | null>(null)
const chartContainer = ref<HTMLDivElement | null>(null)

// WebSocket 连接
const websocket = ref<WebSocket | null>(null)
const jobId = ref<string | null>(null)

// 波形数据
const rawSeries: [number, number][] = []
const filteredSeries: [number, number][] = []
const anomalySeries: [number, number][] = []

// 播放控制（Flink 流式数据，不需要手动播放控制）
const isPlaying = ref(false) // Flink 自动推送，这个状态用于显示连接状态
const playbackSpeed = ref(5) // 保留用于显示，实际由 Flink 控制
const currentPlaybackIndex = ref(0)
const totalDataPoints = ref(0)

// 实时处理速度统计（从 WebSocket 消息中获取）
const processingSpeed = ref(0) // 处理速度（k/s，千点/秒）
const lastUpdateTime = ref<number | null>(null) // 上次更新时间
const lastUpdateIndex = ref(0) // 上次更新的索引

// 时间戳基准（用于计算相对时间）
const startTimestamp = ref<number | null>(null)

// Y 轴范围（根据数据自动调整）
const yMin = ref(-0.6)
const yMax = ref(0.6)

// 统计信息
const metrics = reactive({
  totalPoints: 0,
  anomalyCount: 0,
  channelName: '',
  channelUnit: ''
})

// 控制面板相关状态
const selectedDevice = ref('device-001')
const filterType = ref<FilterType>('KALMAN')
const kalmanParams = reactive<KalmanParams>({
  kalmanQ: 1e-5,
  kalmanR: 0.1,
  kalmanP0: 1.0,
  kalmanX0N: 10
})

const initChart = () => {
  if (!chartContainer.value) return
  // 避免重复 init，先销毁旧实例
  if (chartRef.value) {
    chartRef.value.dispose()
  }
  chartRef.value = echarts.init(chartContainer.value)
  
  // 初始化时清空数据
  rawSeries.length = 0
  filteredSeries.length = 0
  anomalySeries.length = 0
  
  chartRef.value.setOption({
    // 实时检测需要动画效果，但为了性能可以禁用
    animation: false,
    tooltip: { trigger: 'axis' },
    legend: {
      data: ['原始信号', '滤波信号', '异常点']
    },
    grid: { left: 50, right: 20, top: 20, bottom: 30 },
    xAxis: {
      type: 'value',
      boundaryGap: false,
      name: '时间 (s)',
      min: 0,
      max: 10 // 初始范围，后续会根据数据动态调整
    },
    yAxis: {
      type: 'value',
      scale: true,
      name: metrics.channelUnit || 'Amplitude'
    },
    // 注意：初始时不设置 dataZoom，等有数据后再设置，避免 ECharts 错误
    // 实时检测波形：每个样本点以点显示，然后用平滑曲线连接
    series: [
      {
        name: '原始信号',
        type: 'line',
        showSymbol: true,
        symbol: 'circle',
        symbolSize: 4,
        smooth: true,
        data: rawSeries,
        lineStyle: { 
          width: 1.4,
          color: '#409EFF'
        },
        itemStyle: {
          color: '#409EFF'
        }
      },
      {
        name: '滤波信号',
        type: 'line',
        showSymbol: true,
        symbol: 'circle',
        symbolSize: 4,
        smooth: true,
        data: filteredSeries,
        lineStyle: { 
          width: 1.4,
          color: '#67C23A'
        },
        itemStyle: {
          color: '#67C23A'
        }
      },
      {
        name: '异常点',
        type: 'scatter',
        symbol: 'diamond',
        symbolSize: 8,
        data: anomalySeries,
        itemStyle: { color: '#F56C6C' }
      }
    ]
  })
}

const updateChart = () => {
  if (!chartRef.value || !analysisResult.value) return
  const unit = metrics.channelUnit || 'Amplitude'
  
  // 计算Y轴范围（基于当前显示的数据）
  const currentValues = rawSeries.length > 0 
    ? rawSeries.map(p => p[1])
    : allRawPoints.slice(0, currentPlaybackIndex.value).map(p => p[1])
  
  if (currentValues.length > 0) {
    const minVal = Math.min(...currentValues)
    const maxVal = Math.max(...currentValues)
    const padding = (maxVal - minVal) * 0.1 || 0.1
    yMin.value = minVal - padding
    yMax.value = maxVal + padding
  }
  
  chartRef.value.setOption({
    yAxis: { 
      name: unit, 
      min: yMin.value, 
      max: yMax.value 
    },
    series: [
      { data: rawSeries },
      { data: filteredSeries },
      { data: anomalySeries }
    ],
    animation: false // 和历史分析保持一致：禁用动画
  })
}

// Flink 流式处理不需要手动播放控制，数据自动推送
// 保留此函数以防将来需要
const togglePlayback = () => {
  // Flink 自动推送，不需要手动控制
  ElMessage.info('Flink 流式处理自动运行，无需手动控制')
}

// Flink 流式处理不需要手动播放逻辑，数据通过 WebSocket 自动推送

// 更新图表，实现流动效果（使用 dataZoom 自动滚动）
const updateChartWithFlow = () => {
  if (!chartRef.value || rawSeries.length === 0) return
  const unit = metrics.channelUnit || 'Amplitude'
  
  // 计算Y轴范围（基于当前显示的数据）
  const currentValues = rawSeries.map(p => p[1])
  if (currentValues.length > 0) {
    const minVal = Math.min(...currentValues)
    const maxVal = Math.max(...currentValues)
    const padding = (maxVal - minVal) * 0.1 || 0.1
    yMin.value = minVal - padding
    yMax.value = maxVal + padding
  }
  
  // 计算 dataZoom 的 start 和 end，实现自动滚动
  // 显示最后 5% 的数据窗口，随着数据增加自动向右滚动
  let dataZoomStart = 95
  let dataZoomEnd = 100
  
  // 计算当前数据的总范围
  const timestamps = rawSeries.map(p => p[0])
  const firstTimestamp = Math.min(...timestamps)
  const lastTimestamp = Math.max(...timestamps)
  const timeRange = lastTimestamp - firstTimestamp
  
  // 如果时间范围太小或为0，使用索引作为X轴
  if (timeRange <= 0 || timeRange < 0.0001) {
    console.warn('⚠️ 时间戳范围太小，使用索引作为X轴')
    // 使用索引作为X轴
    const indexedRawSeries = rawSeries.map((p, i) => [i, p[1]])
    const indexedFilteredSeries = filteredSeries.map((p, i) => [i, p[1]])
    const indexedAnomalySeries = anomalySeries.map((p, i) => {
      const originalIndex = rawSeries.findIndex(r => r[0] === p[0])
      return originalIndex >= 0 ? [originalIndex, p[1]] : null
    }).filter(p => p !== null) as [number, number][]
    
    // 对于索引模式，计算 dataZoom 范围
    const totalPoints = rawSeries.length
    if (totalPoints > 0) {
      // 显示最后 5% 的数据
      const windowSize = Math.max(1, Math.floor(totalPoints * 0.05))
      const startIndex = Math.max(0, totalPoints - windowSize)
      dataZoomStart = totalPoints > 1 ? (startIndex / (totalPoints - 1)) * 100 : 0
      dataZoomEnd = 100
    }
    
    chartRef.value.setOption({
      xAxis: {
        type: 'value',
        name: '数据点索引',
        min: 0,
        max: Math.max(10, totalPoints)
      },
      yAxis: { 
        name: unit, 
        min: yMin.value, 
        max: yMax.value 
      },
      dataZoom: [
        {
          type: 'slider',
          show: true,
          xAxisIndex: [0],
          start: dataZoomStart,
          end: dataZoomEnd,
          realtime: true,
          throttle: 100
        },
        {
          type: 'inside',
          xAxisIndex: [0],
          start: dataZoomStart,
          end: dataZoomEnd,
          realtime: true,
          throttle: 100
        }
      ],
      series: [
        { data: indexedRawSeries },
        { data: indexedFilteredSeries },
        { data: indexedAnomalySeries }
      ],
      animation: false
    }, { notMerge: false })
    return
  }
  
  // 对于时间戳模式，计算 dataZoom 范围
  // 显示最后 5% 的时间窗口
  const windowSize = timeRange * 0.05
  const windowStart = lastTimestamp - windowSize
  const windowStartPercent = timeRange > 0 ? ((windowStart - firstTimestamp) / timeRange) * 100 : 0
  dataZoomStart = Math.max(0, Math.min(95, windowStartPercent))
  dataZoomEnd = 100
  
  // 更新图表：只更新数据和 dataZoom，让 ECharts 自动处理滚动
  chartRef.value.setOption({
    xAxis: {
      type: 'value',
      name: '时间 (s)',
      min: firstTimestamp,
      max: lastTimestamp
    },
    yAxis: { 
      name: unit, 
      min: yMin.value, 
      max: yMax.value 
    },
    dataZoom: [
      {
        type: 'slider',
        show: true,
        xAxisIndex: [0],
        start: dataZoomStart,
        end: dataZoomEnd,
        realtime: true,
        throttle: 100
      },
      {
        type: 'inside',
        xAxisIndex: [0],
        start: dataZoomStart,
        end: dataZoomEnd,
        realtime: true,
        throttle: 100
      }
    ],
    series: [
      { data: rawSeries },
      { data: filteredSeries },
      { data: anomalySeries }
    ],
    animation: false
  }, { notMerge: false })
}

// 建立 WebSocket 连接
const connectWebSocket = (websocketPath: string) => {
  // 关闭旧连接
  if (websocket.value) {
    websocket.value.close()
    websocket.value = null
  }
  
  // 构建 WebSocket URL
  // websocketPath 已经是完整路径，如 /admin-api/monitor/ws?jobId=xxx
  // 需要根据当前环境构建完整 URL
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = window.location.host
  // 如果 websocketPath 以 / 开头，直接拼接
  const wsUrl = websocketPath.startsWith('/') 
    ? `${protocol}//${host}${websocketPath}`
    : `${protocol}//${host}/${websocketPath}`
  
  console.log('🔌 连接 WebSocket:', wsUrl)
  
  const ws = new WebSocket(wsUrl)
  
  ws.onopen = () => {
    console.log('✅ WebSocket 连接已建立')
    isPlaying.value = true
    ElMessage.success('Flink 流式处理已启动')
  }
  
  ws.onmessage = (event) => {
    try {
      const message: MonitorStreamMessage = JSON.parse(event.data)
      handleWebSocketMessage(message)
    } catch (err) {
      console.error('解析 WebSocket 消息失败:', err, event.data)
    }
  }
  
  ws.onerror = (error) => {
    console.error('WebSocket 错误:', error)
    ElMessage.error('WebSocket 连接错误')
    isPlaying.value = false
  }
  
  ws.onclose = (event) => {
    console.log('WebSocket 连接已关闭', {
      code: event.code,
      reason: event.reason,
      wasClean: event.wasClean
    })
    isPlaying.value = false
    websocket.value = null
    
    // 如果连接异常关闭，显示错误信息
    if (!event.wasClean) {
      ElMessage.warning('WebSocket 连接异常关闭，请检查 Flink 作业状态')
    }
  }
  
  websocket.value = ws
}

// 处理 WebSocket 消息
const handleWebSocketMessage = (message: MonitorStreamMessage) => {
  // 初始化时间戳基准（使用第一个消息的时间戳）
  if (startTimestamp.value === null) {
    startTimestamp.value = message.timestamp
  }
  
  // 计算相对时间（秒）
  const relativeTime = (message.timestamp - startTimestamp.value) / 1000.0
  
  // 添加数据点到图表
  rawSeries.push([relativeTime, message.originalValue])
  filteredSeries.push([relativeTime, message.filteredValue])
  
  // 如果是异常点，添加到异常序列
  if (message.anomaly) {
    anomalySeries.push([relativeTime, message.originalValue])
  }
  
  // 保持窗口大小（500个点）
  const windowSize = 500
  if (rawSeries.length > windowSize) {
    rawSeries.shift()
    filteredSeries.shift()
    // 清理超出窗口的异常点
    const firstTime = rawSeries.length > 0 ? rawSeries[0][0] : 0
    for (let i = anomalySeries.length - 1; i >= 0; i--) {
      if (anomalySeries[i][0] < firstTime) {
        anomalySeries.splice(i, 1)
      }
    }
  }
  
  // 更新统计信息
  currentPlaybackIndex.value++
  totalDataPoints.value = message.channel?.sampleCount || currentPlaybackIndex.value
  metrics.anomalyCount = message.anomalyCount
  metrics.channelName = message.channel?.name || ''
  metrics.channelUnit = message.channel?.unit || 'Amplitude'
  processingSpeed.value = message.throughputKps || 0
  
  // 更新图表
  updateChartWithFlow()
}

const handleUpload = async (options: any) => {
  uploading.value = true
  analyzing.value = true
  
  // 关闭旧连接
  if (websocket.value) {
    websocket.value.close()
    websocket.value = null
  }
  
  // 清空数据
  rawSeries.length = 0
  filteredSeries.length = 0
  anomalySeries.length = 0
  currentPlaybackIndex.value = 0
  totalDataPoints.value = 0
  startTimestamp.value = null
  processingSpeed.value = 0
  metrics.totalPoints = 0
  metrics.anomalyCount = 0
  metrics.channelName = ''
  metrics.channelUnit = ''
  
  const form = new FormData()
  form.append('file', options.file as File)
  
  try {
    // 调用 Flink 上传接口
    const resp = await uploadTdms(form, { 
      filterType: filterType.value, 
      ...kalmanParams 
    })
    
    uploadResult.value = resp
    jobId.value = resp.jobId
    
    // 更新统计信息
    metrics.channelName = resp.channel?.name || ''
    metrics.channelUnit = resp.channel?.unit || 'Amplitude'
    totalDataPoints.value = resp.channel?.sampleCount || 0
    
    // 等待 DOM 更新，初始化图表
    await nextTick()
    initChart()
    
    // 建立 WebSocket 连接
    connectWebSocket(resp.websocketPath)
    
    ElMessage.success('文件上传成功，Flink 流式处理已启动')
  } catch (err: any) {
    ElMessage.error(err?.message || '上传失败')
    console.error('上传失败:', err)
  } finally {
    uploading.value = false
    analyzing.value = false
  }
}

// 切换滤波器：更新 Flink 作业配置
watch(
  () => [filterType.value, kalmanParams.kalmanQ, kalmanParams.kalmanR, kalmanParams.kalmanP0, kalmanParams.kalmanX0N] as const,
  async () => {
    // 如果 Flink 作业正在运行，更新滤波器配置
    if (jobId.value && websocket.value && websocket.value.readyState === WebSocket.OPEN) {
      try {
        const { updateFilterConfig } = await import('@/api/monitor')
        await updateFilterConfig(jobId.value, {
          filterType: filterType.value,
          ...kalmanParams
        })
        ElMessage.success('滤波器配置已更新，Flink 作业将重启')
      } catch (err: any) {
        ElMessage.error('更新滤波器配置失败: ' + (err?.message || '未知错误'))
      }
    }
  }
)


onMounted(() => {
  initChart()
})

onBeforeUnmount(() => {
  // 关闭 WebSocket 连接
  if (websocket.value) {
    websocket.value.close()
    websocket.value = null
  }
  
  // 停止 Flink 作业
  if (jobId.value) {
    import('@/api/monitor').then(({ stopMonitorJob }) => {
      stopMonitorJob(jobId.value!).catch((err: any) => {
        console.error('停止 Flink 作业失败:', err)
      })
    })
  }
  
  // 清理图表
  if (chartRef.value) {
    chartRef.value.dispose()
  }
})
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.playback-controls {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.playback-info {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  white-space: nowrap;
}
.control-card {
  margin-bottom: 12px;
}
.placeholder {
  height: 420px;
  border: 1px dashed var(--el-border-color);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-secondary);
}
.anomaly-control {
  display: flex;
  align-items: center;
  gap: 8px;
}
.threshold-value {
  color: var(--el-text-color-secondary);
}
.card-header {
  font-weight: 600;
}
</style>

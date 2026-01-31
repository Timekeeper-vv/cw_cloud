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
      <div class="playback-controls" v-if="analysisResult">
        <el-button 
          :type="isPlaying ? 'warning' : 'success'" 
          @click="togglePlayback"
          :disabled="!analysisResult"
        >
          <el-icon style="margin-right: 4px;">
            <VideoPause v-if="isPlaying" />
            <VideoPlay v-else />
          </el-icon>
          {{ isPlaying ? '暂停' : '播放' }}
        </el-button>
        <el-slider
          v-model="playbackSpeed"
          :min="1"
          :max="10"
          :step="1"
          :show-tooltip="true"
          :format-tooltip="(val) => `${val}x`"
          style="width: 150px; margin: 0 12px;"
          :disabled="isPlaying"
        />
        <span class="playback-info">
          速度: {{ playbackSpeed }}x | 
          进度: {{ currentPlaybackIndex }} / {{ totalDataPoints }}
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

        <div v-if="!analysisResult" class="placeholder">
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
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </ContentWrap>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { analyzeRealtime, analyzeRealtimeStream, type HistoryAnalysisResult, type FilterType, type KalmanParams } from '@/api/monitor'
import { ElMessage } from 'element-plus'
import { VideoPlay, VideoPause } from '@element-plus/icons-vue'

const uploading = ref(false)
const analyzing = ref(false)
const analysisResult = ref<HistoryAnalysisResult | null>(null)
const chartRef = ref<echarts.ECharts | null>(null)
const chartContainer = ref<HTMLDivElement | null>(null)

// 波形数据
const rawSeries: [number, number][] = []
const filteredSeries: [number, number][] = []
const anomalySeries: [number, number][] = []

// 播放控制
const isPlaying = ref(false)
const playbackSpeed = ref(5) // 播放速度倍数
const currentPlaybackIndex = ref(0)
const totalDataPoints = ref(0)
const playbackTimer = ref<number | null>(null)
const allRawPoints: [number, number][] = [] // 存储所有原始数据点
const allFilteredPoints: [number, number][] = [] // 存储所有滤波数据点
const allAnomalyPoints: [number, number][] = [] // 存储所有异常点

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
      name: '时间 (s)'
      // 不设置 min/max，让 dataZoom 控制显示范围
    },
    yAxis: {
      type: 'value',
      scale: true,
      name: metrics.channelUnit || 'Amplitude'
    },
    // 实时滚动：使用 dataZoom 实现示波器效果
    dataZoom: [
      {
        type: 'slider',
        show: true,
        xAxisIndex: [0],
        start: 95, // 初始显示最后 5% 的数据
        end: 100,
        realtime: true,
        throttle: 100 // 防抖，避免频繁触发
      },
      {
        type: 'inside',
        xAxisIndex: [0],
        start: 95,
        end: 100,
        realtime: true,
        throttle: 100
      }
    ],
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

// 播放控制函数
const togglePlayback = () => {
  if (!analysisResult.value || totalDataPoints.value === 0) return
  
  if (isPlaying.value) {
    // 暂停
    if (playbackTimer.value) {
      clearInterval(playbackTimer.value)
      playbackTimer.value = null
    }
    isPlaying.value = false
  } else {
    // 播放
    isPlaying.value = true
    startPlayback()
  }
}

const startPlayback = () => {
  if (playbackTimer.value) {
    clearInterval(playbackTimer.value)
  }
  
  // 如果已经播放完，从头开始
  if (currentPlaybackIndex.value >= totalDataPoints.value) {
    currentPlaybackIndex.value = 0
    rawSeries.length = 0
    filteredSeries.length = 0
    anomalySeries.length = 0
  }
  
  // 计算每次更新的点数（根据播放速度）
  const pointsPerUpdate = Math.max(1, Math.floor(playbackSpeed.value))
  const interval = 100 / playbackSpeed.value // 基础间隔 100ms，速度越快间隔越短
  
  // 设置窗口大小（显示最近多少个数据点）
  const windowSize = 500 // 窗口大小，可以根据需要调整
  
  playbackTimer.value = window.setInterval(() => {
    if (currentPlaybackIndex.value >= totalDataPoints.value) {
      // 播放完成
      if (playbackTimer.value) {
        clearInterval(playbackTimer.value)
        playbackTimer.value = null
      }
      isPlaying.value = false
      return
    }
    
    // 添加新的数据点
    const endIndex = Math.min(
      currentPlaybackIndex.value + pointsPerUpdate,
      totalDataPoints.value
    )
    
    for (let i = currentPlaybackIndex.value; i < endIndex; i++) {
      if (allRawPoints[i]) {
        rawSeries.push(allRawPoints[i])
      }
      if (allFilteredPoints[i]) {
        filteredSeries.push(allFilteredPoints[i])
      }
    }
    
    // 添加异常点（只添加当前范围内的）
    allAnomalyPoints.forEach(point => {
      const timestamp = point[0]
      const lastTimestamp = rawSeries.length > 0 ? rawSeries[rawSeries.length - 1][0] : 0
      if (timestamp <= lastTimestamp && !anomalySeries.find(p => p[0] === timestamp)) {
        anomalySeries.push(point)
      }
    })
    
    currentPlaybackIndex.value = endIndex
    
    // 实现流动效果：保持窗口大小，让数据从右边进入，左边移出
    if (rawSeries.length > windowSize) {
      const removeCount = rawSeries.length - windowSize
      rawSeries.splice(0, removeCount)
      filteredSeries.splice(0, removeCount)
      // 清理超出窗口的异常点
      const firstTimestamp = rawSeries.length > 0 ? rawSeries[0][0] : 0
      for (let i = anomalySeries.length - 1; i >= 0; i--) {
        if (anomalySeries[i][0] < firstTimestamp) {
          anomalySeries.splice(i, 1)
        }
      }
    }
    
    updateChartWithFlow()
  }, interval)
}

// 更新图表，实现流动效果（使用 dataZoom 自动滚动）
const updateChartWithFlow = () => {
  if (!chartRef.value || !analysisResult.value) return
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
  
  if (rawSeries.length === 0) return
  
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
        name: '数据点索引'
        // 不设置 min/max，让 dataZoom 控制
      },
      yAxis: { 
        name: unit, 
        min: yMin.value, 
        max: yMax.value 
      },
      dataZoom: [
        {
          start: dataZoomStart,
          end: dataZoomEnd
        },
        {
          start: dataZoomStart,
          end: dataZoomEnd
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
    yAxis: { 
      name: unit, 
      min: yMin.value, 
      max: yMax.value 
    },
    dataZoom: [
      {
        start: dataZoomStart,
        end: dataZoomEnd
      },
      {
        start: dataZoomStart,
        end: dataZoomEnd
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

const handleUpload = async (options: any) => {
  uploading.value = true
  analyzing.value = true
  const form = new FormData()
  form.append('file', options.file as File)
  try {
    // 调用后端接口获取处理后的JSON数据
    const resp = await analyzeRealtime(form, { 
      filterType: filterType.value, 
      ...kalmanParams 
    })
    
    if (!resp.points || resp.points.length === 0) {
      ElMessage.warning('未解析到有效样本')
      return
    }
    
    analysisResult.value = resp
    
    // 更新统计信息
    metrics.totalPoints = resp.points.length
    metrics.anomalyCount = resp.anomalyCount
    metrics.channelName = resp.channel?.name || ''
    metrics.channelUnit = resp.channel?.unit || 'Amplitude'
    
    // 处理数据，转换为图表需要的格式
    rawSeries.length = 0
    filteredSeries.length = 0
    anomalySeries.length = 0
    
    // 先收集异常点的时间戳（用于后续标记）
    const anomalyTimestamps = new Set<number>()
    resp.points.forEach(point => {
      if (point.isAnomaly) {
        anomalyTimestamps.add(point.timestamp)
      }
    })
    
    // 对数据进行线性插值，增加数据点密度，使曲线更平滑
    const interpolatePoints = (points: Array<{ timestamp: number, rawValue: number, filteredValue: number, isAnomaly?: boolean }>, factor: number = 2) => {
      if (points.length < 2) return points
      const interpolated: typeof points = []
      for (let i = 0; i < points.length - 1; i++) {
        interpolated.push(points[i])
        // 在相邻两点之间插入插值点
        for (let j = 1; j < factor; j++) {
          const ratio = j / factor
          const t1 = points[i].timestamp
          const t2 = points[i + 1].timestamp
          const v1 = points[i].rawValue
          const v2 = points[i + 1].rawValue
          const f1 = points[i].filteredValue
          const f2 = points[i + 1].filteredValue
          interpolated.push({
            timestamp: t1 + (t2 - t1) * ratio,
            rawValue: v1 + (v2 - v1) * ratio,
            filteredValue: f1 + (f2 - f1) * ratio,
            isAnomaly: false // 插值点不标记为异常
          })
        }
      }
      interpolated.push(points[points.length - 1])
      return interpolated
    }
    
    // 检查时间戳范围和分布
    const timestamps = resp.points.map(p => p.timestamp)
    const minTimestamp = Math.min(...timestamps)
    const maxTimestamp = Math.max(...timestamps)
    const timeRange = maxTimestamp - minTimestamp
    
    console.log('📊 时间戳分析:', {
      totalPoints: resp.points.length,
      minTimestamp,
      maxTimestamp,
      timeRange,
      timeRangeSeconds: timeRange,
      avgInterval: timeRange / resp.points.length,
      firstFewTimestamps: timestamps.slice(0, 5),
      lastFewTimestamps: timestamps.slice(-5)
    })
    
    // 如果时间戳范围太小（可能是时间戳单位问题），尝试调整
    // 如果时间戳都是相同的值，说明数据有问题
    if (timeRange === 0) {
      console.warn('⚠️ 警告：所有时间戳相同，可能是数据问题')
      ElMessage.warning('时间戳数据异常，所有点的时间戳相同')
    }
    
    // 如果时间戳范围很小（小于0.001），可能是单位问题，尝试转换为秒
    let normalizedPoints = resp.points
    if (timeRange > 0 && timeRange < 0.001) {
      console.warn('⚠️ 时间戳范围太小，可能是毫秒单位，尝试转换')
      // 如果时间戳看起来像毫秒（很大），转换为秒
      if (minTimestamp > 1000000) {
        normalizedPoints = resp.points.map(p => ({
          ...p,
          timestamp: p.timestamp / 1000 // 毫秒转秒
        }))
        console.log('✅ 已将时间戳从毫秒转换为秒')
      }
    }
    
    // 如果数据点较少，进行插值处理
    const processedPoints = normalizedPoints.length < 1000 
      ? interpolatePoints(normalizedPoints, 2) // 数据点少于1000时，插值2倍
      : normalizedPoints // 数据点较多时，不插值（避免性能问题）
    
    // 存储所有数据点（用于播放）
    allRawPoints.length = 0
    allFilteredPoints.length = 0
    allAnomalyPoints.length = 0
    
    // 处理图表数据
    processedPoints.forEach(point => {
      allRawPoints.push([point.timestamp, point.rawValue])
      allFilteredPoints.push([point.timestamp, point.filteredValue])
    })
    
    // 处理异常点（只使用原始数据点，不使用插值点）
    resp.points.forEach(point => {
      if (point.isAnomaly) {
        allAnomalyPoints.push([point.timestamp, point.rawValue])
      }
    })
    
    // 初始化播放状态
    totalDataPoints.value = allRawPoints.length
    currentPlaybackIndex.value = 0
    rawSeries.length = 0
    filteredSeries.length = 0
    anomalySeries.length = 0
    
    // 等待 DOM 更新出 realtime-chart 容器，再初始化 ECharts
    await nextTick()
    initChart()
    
    // 默认不自动播放，用户点击播放按钮后才会播放
    // 如果需要自动播放，可以取消下面的注释
    // togglePlayback()
    
    ElMessage.success(`分析完成，共 ${metrics.totalPoints} 个数据点，检测到 ${metrics.anomalyCount} 个异常点。点击播放按钮查看动画效果。`)
  } catch (err: any) {
    ElMessage.error(err?.message || '上传和分析失败')
  } finally {
    uploading.value = false
    analyzing.value = false
  }
}

// 切换滤波器：重新分析数据
watch(
  () => [filterType.value, kalmanParams.kalmanQ, kalmanParams.kalmanR, kalmanParams.kalmanP0, kalmanParams.kalmanX0N] as const,
  async () => {
    // 如果有已上传的文件，重新分析
    // 这里需要保存上次上传的文件，暂时不自动重新分析，由用户手动重新上传
  }
)


onMounted(() => {
  initChart()
})

onBeforeUnmount(() => {
  // 清理资源
  if (playbackTimer.value) {
    clearInterval(playbackTimer.value)
    playbackTimer.value = null
  }
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

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
          <el-form label-width="80px" size="small">
            <el-form-item label="连接状态">
              <el-tag :type="connectionTagType">
                {{ connectionText }}
              </el-tag>
            </el-form-item>
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
                <el-input-number v-model="kalmanParams.kalmanQ" :min="0" :step="1e-5" :controls="true" />
              </el-form-item>
              <el-form-item label="R">
                <el-input-number v-model="kalmanParams.kalmanR" :min="0" :step="0.01" :controls="true" />
              </el-form-item>
              <el-form-item label="P0">
                <el-input-number v-model="kalmanParams.kalmanP0" :min="0" :step="0.1" :controls="true" />
              </el-form-item>
              <el-form-item label="x0-N">
                <el-input-number v-model="kalmanParams.kalmanX0N" :min="1" :max="100" :step="1" :controls="true" />
              </el-form-item>
            </template>
            <el-form-item>
              <el-button type="primary" @click="handleToggleConnection">
                {{ connected ? '断开连接' : '重新连接' }}
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <div v-if="!jobInfo" class="placeholder">
          请先上传有效 TDMS 文件
        </div>
        <div v-else ref="chartContainer" id="realtime-chart" style="height: 420px"></div>
      </el-col>

      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              实时统计
            </div>
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="处理速度 (k/s)">
              {{ metrics.throughputKps.toFixed(2) }}
            </el-descriptions-item>
            <el-descriptions-item label="处理延迟 (ms)">
              {{ metrics.processingDelayMs.toFixed(1) }}
            </el-descriptions-item>
            <el-descriptions-item label="原始 SNR (dB)">
              {{ metrics.snrBeforeDb.toFixed(2) }}
            </el-descriptions-item>
            <el-descriptions-item label="滤波后 SNR (dB)">
              {{ metrics.snrAfterDb.toFixed(2) }}
            </el-descriptions-item>
            <el-descriptions-item label="ΔSNR (dB)">
              {{ metrics.snrDeltaDb.toFixed(2) }}
            </el-descriptions-item>
            <!-- 累计异常暂时隐藏，保留结构方便以后恢复 -->
            <el-descriptions-item v-if="false" label="累计异常">
              {{ metrics.anomalyCount }}
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
import { uploadTdms, updateAnomalyConfig, updateFilterConfig, type MonitorUploadResp, type FilterType, type KalmanParams } from '@/api/monitor'
import { ElMessage } from 'element-plus'
import { getAccessToken } from '@/utils/auth'

interface StreamMessage {
  jobId: string
  timestamp: number
  originalValue: number
  filteredValue: number
  anomaly: boolean
  energy: number
  snrBeforeDb: number
  snrAfterDb: number
  snrDeltaDb: number
  throughputKps: number
  processingDelayMs: number
  anomalyCount: number
  channel: {
    name: string
    unit: string
  }
}

const uploading = ref(false)
const jobInfo = ref<MonitorUploadResp | null>(null)
const websocket = ref<WebSocket | null>(null)
const chartRef = ref<echarts.ECharts | null>(null)
const chartContainer = ref<HTMLDivElement | null>(null)
// 实时曲线窗口长度：只保留最近 5 秒的数据，让波形像流水一样向前滚动
const windowMs = 5000

const rawSeries: [number, number][] = []
const filteredSeries: [number, number][] = []

// 实时播放时间轴：第一次连接成功时记录一个基准时间，
// 后续每收到一条样本，就在这个基准时间上平滑递增，保证 X 轴严格单调递增
const baseTimeMs = ref<number | null>(null)
const sampleIndex = ref(0)
// 固定 Y 轴范围为 [-0.6, 0.6]，满足你指定的上下限
const yMin = ref(-0.6)
const yMax = ref(0.6)

// 右侧实时统计的展示控制：做一点平滑，让数值更稳定，但保持真实
const METRIC_SMOOTH_ALPHA = 0.3

const metrics = reactive({
  throughputKps: 0,
  processingDelayMs: 0,
  snrBeforeDb: 0,
  snrAfterDb: 0,
  snrDeltaDb: 0,
  anomalyCount: 0
})

const anomalyEnabled = ref(true)
const anomalyThreshold = ref(1)
const thresholdMax = ref(10)

// 控制面板相关状态
const connected = ref(false)
const connecting = ref(false)
const selectedDevice = ref('device-001')
const filterType = ref<FilterType>('KALMAN')
const kalmanParams = reactive<KalmanParams>({
  kalmanQ: 1e-5,
  kalmanR: 0.1,
  kalmanP0: 1.0,
  kalmanX0N: 10
})

const connectionText = computed(() => {
  if (connecting.value) return '连接中'
  return connected.value ? '已连接' : '未连接'
})

const connectionTagType = computed<'success' | 'info' | 'warning'>(() => {
  if (connecting.value) return 'warning'
  return connected.value ? 'success' : 'info'
})

const initChart = () => {
  if (!chartContainer.value) return
  // 避免重复 init，先销毁旧实例
  if (chartRef.value) {
    chartRef.value.dispose()
  }
  chartRef.value = echarts.init(chartContainer.value)
  chartRef.value.setOption({
    animation: false,
    tooltip: { trigger: 'axis' },
    legend: {
      data: ['原始信号', '滤波信号']
    },
    grid: { left: 50, right: 20, bottom: 40, top: 20 },
    xAxis: {
      type: 'time',
      boundaryGap: false
    },
    // Y 轴固定量程，不随窗口自动缩放，保证视觉稳定
    yAxis: {
      type: 'value',
      min: yMin.value,
      max: yMax.value,
      name: jobInfo.value?.channel.unit || 'Amplitude'
    },
    dataZoom: [],
    series: [
      {
        name: '原始信号',
        type: 'line',
        showSymbol: false,
        data: rawSeries,
        lineStyle: { color: '#409EFF' }
      },
      {
        name: '滤波信号',
        type: 'line',
        showSymbol: false,
        data: filteredSeries,
        lineStyle: { color: '#67C23A' }
      }
    ]
  })
}

const refreshSeries = () => {
  if (!chartRef.value) return
  const unit = jobInfo.value?.channel.unit || 'Amplitude'
  chartRef.value.setOption({
    yAxis: { name: unit, min: yMin.value, max: yMax.value },
    series: [{ data: rawSeries }, { data: filteredSeries }]
  })
}

const handleUpload = async (options: any) => {
  uploading.value = true
  const form = new FormData()
  form.append('file', options.file as File)
  try {
    const resp = await uploadTdms(form, { filterType: filterType.value, ...kalmanParams })
    // uploadTdms 已经过 Axios 拦截器解包，直接返回后端的 data（MonitorUploadResp）
    jobInfo.value = resp
    anomalyThreshold.value = resp.channel.sampleCount ? resp.channel.sampleCount * 0.001 : 1
    thresholdMax.value = anomalyThreshold.value * 4

    // 先建立 WebSocket 通道
    connectWebSocket(resp.websocketPath)

    // 等待 DOM 更新出 realtime-chart 容器，再初始化 ECharts
    await nextTick()
    initChart()
    ElMessage.success('上传成功，开始实时回放')
  } catch (err: any) {
    ElMessage.error(err?.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

// 切换滤波器：重启后端 Flink 作业（同 jobId），前端只需要清空曲线并继续用同一个 ws 通道接收
watch(
  () => [filterType.value, kalmanParams.kalmanQ, kalmanParams.kalmanR, kalmanParams.kalmanP0, kalmanParams.kalmanX0N] as const,
  async () => {
    if (!jobInfo.value?.jobId) return
    try {
      await updateFilterConfig(jobInfo.value.jobId,  { filterType: filterType.value, ...kalmanParams })
      baseTimeMs.value = Date.now()
      sampleIndex.value = 0
      rawSeries.length = 0
      filteredSeries.length = 0
      refreshSeries()
      ElMessage.success('已切换滤波器并重启实时任务')
    } catch (e: any) {
      ElMessage.error(e?.message || '切换滤波器失败')
    }
  }
)

const connectWebSocket = (path: string) => {
  if (websocket.value) {
    websocket.value.close()
  }
  connecting.value = true
  connected.value = false
  const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
  // 开发环境：直接连 monitor-server（48090），避免 Vite 的 ws 代理/路径匹配带来的不确定性
  // 生产环境：连当前 host（通常是网关域名）
  // 注意：不要擅自去掉 /admin-api 前缀（后端同时兼容 /api/monitor/ws 和 /admin-api/api/monitor/ws）
  const isDev = import.meta.env.DEV
  const host = isDev ? 'localhost:48090' : location.host
  // 实测：直连 monitor-server 时，用 /api/monitor/ws 更稳定（避免某些链路对 /admin-api 前缀的处理影响 Upgrade）
  const finalPath = isDev && path.startsWith('/admin-api') ? path.replace('/admin-api', '') : path
  // yudao 的 WebSocket 安全链路支持 ?token={accessToken} 方式鉴权
  // 这样后端 TokenAuthenticationFilter 能认证通过，避免握手被 401/302 拦截导致连接失败
  const token = getAccessToken()
  const urlObj = new URL(`${protocol}://${host}${finalPath}`)
  if (token && !urlObj.searchParams.get('token')) {
    urlObj.searchParams.set('token', token)
  }
  const url = urlObj.toString()
  console.log('连接实时 WebSocket:', url)
  websocket.value = new WebSocket(url)
  websocket.value.onopen = () => {
    connecting.value = false
    connected.value = true
    // 连接成功后清空旧数据，避免不同任务的数据混在一起
    baseTimeMs.value = Date.now()
    sampleIndex.value = 0
    rawSeries.length = 0
    filteredSeries.length = 0
    refreshSeries()
  }
  websocket.value.onmessage = (event) => {
    const payload = JSON.parse(event.data) as StreamMessage
    consumeMessage(payload)
  }
  websocket.value.onclose = () => {
    websocket.value = null
    connected.value = false
    connecting.value = false
  }
  websocket.value.onerror = (e) => {
    console.error('WebSocket error', e)
    ElMessage.error('实时通道连接失败，请检查网关和 monitor 服务')
    connected.value = false
    connecting.value = false
  }
}

const handleToggleConnection = () => {
  if (connected.value && websocket.value) {
    websocket.value.close()
    return
  }
  if (!jobInfo.value?.websocketPath) {
    ElMessage.warning('请先上传 TDMS 文件，获取实时任务通道')
    return
  }
  connectWebSocket(jobInfo.value.websocketPath)
}

const consumeMessage = (msg: StreamMessage) => {
  console.log('收到实时数据:', msg)
  // 为保证波形严格随时间向前“流动”，这里不再依赖后端 timestamp，
  // 而是以前端的基准时间 + 样本序号来生成单调递增的时间轴
  if (baseTimeMs.value == null) {
    baseTimeMs.value = Date.now()
    sampleIndex.value = 0
  }
  // 为了让 X 轴更“舒展”、数据不那么挤，这里把相邻样本在时间轴上的间隔拉大一些
  // 例如 20ms：在 5 秒窗口里大约保留 250 个点，曲线更容易看清细节
  const stepMs = 20
  const t = baseTimeMs.value + sampleIndex.value * stepMs
  sampleIndex.value += 1
  pushPoint(rawSeries, [t, msg.originalValue])
  pushPoint(filteredSeries, [t, msg.filteredValue])

  // 右侧统计只做平滑，不再强行设置下限，保证是真实值的平滑版本
  const rawThroughput = msg.throughputKps
  const smoothedThroughput =
    METRIC_SMOOTH_ALPHA * rawThroughput + (1 - METRIC_SMOOTH_ALPHA) * metrics.throughputKps
  metrics.throughputKps = smoothedThroughput

  const rawDelayMs = msg.processingDelayMs
  const smoothedDelay =
    METRIC_SMOOTH_ALPHA * rawDelayMs + (1 - METRIC_SMOOTH_ALPHA) * metrics.processingDelayMs
  metrics.processingDelayMs = smoothedDelay
  metrics.snrBeforeDb = msg.snrBeforeDb
  metrics.snrAfterDb = msg.snrAfterDb
  metrics.snrDeltaDb = msg.snrDeltaDb
  metrics.anomalyCount = msg.anomalyCount

  refreshSeries()
}

const pushPoint = (target: [number, number][], point: [number, number]) => {
  target.push(point)
  const boundary = point[0] - windowMs
  while (target.length && target[0][0] < boundary) {
    target.shift()
  }
}

const updateAnomaly = async () => {
  if (!jobInfo.value) return
  await updateAnomalyConfig(jobInfo.value.jobId, anomalyThreshold.value, anomalyEnabled.value)
}

onMounted(() => {
  initChart()
})

onBeforeUnmount(() => {
  websocket.value?.close()
  connected.value = false
  connecting.value = false
})
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
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

<template>
  <div class="real-signal-page">
    <el-card>
      <template #header>
        <h3>🔊 真实4通道声发射信号显示</h3>
      </template>

      <!-- 文件信息 -->
      <el-alert v-if="fileInfo" type="info" :closable="false" class="mb-4">
        <template #title>
          <div>
            文件：{{ fileInfo.filePath }} | 
            总行数：{{ fileInfo.totalLines }} | 
            通道数：{{ fileInfo.channels }}
          </div>
        </template>
      </el-alert>

      <!-- 参数配置 -->
      <el-form :inline="true" :model="params">
        <el-form-item label="起始索引">
          <el-input-number v-model="params.startIndex" :min="0" :max="fileInfo?.totalLines || 9000" />
        </el-form-item>
        <el-form-item label="读取数量">
          <el-input-number v-model="params.count" :min="100" :max="2000" :step="100" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadSignal" :loading="loading">
            读取信号
          </el-button>
          <el-button @click="loadFileInfo">刷新文件信息</el-button>
        </el-form-item>
      </el-form>

      <!-- 统计信息 -->
      <el-row :gutter="20" v-if="signalData" class="mt-4">
        <el-col :span="6" v-for="(stat, idx) in channelStatistics" :key="idx">
          <el-card shadow="hover">
            <template #header>
              <strong>{{ stat.name }}</strong>
            </template>
            <el-descriptions :column="1" size="small">
              <el-descriptions-item label="均值">{{ stat.mean?.toFixed(6) }}</el-descriptions-item>
              <el-descriptions-item label="最大">{{ stat.max?.toFixed(6) }}</el-descriptions-item>
              <el-descriptions-item label="最小">{{ stat.min?.toFixed(6) }}</el-descriptions-item>
              <el-descriptions-item label="RMS">{{ stat.rms?.toFixed(6) }}</el-descriptions-item>
              <el-descriptions-item label="标准差">{{ stat.std?.toFixed(6) }}</el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <!-- 4通道波形图表 -->
    <el-row :gutter="20" class="mt-4">
      <el-col :span="12">
        <el-card>
          <template #header>通道1波形</template>
          <div ref="chart1Ref" style="height: 300px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>通道2波形</template>
          <div ref="chart2Ref" style="height: 300px"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="mt-4">
      <el-col :span="12">
        <el-card>
          <template #header>通道3波形</template>
          <div ref="chart3Ref" style="height: 300px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>通道4波形</template>
          <div ref="chart4Ref" style="height: 300px"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 4通道对比图 -->
    <el-card class="mt-4">
      <template #header>4通道对比</template>
      <div ref="compareChartRef" style="height: 400px"></div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import request from '@/config/axios'

const loading = ref(false)
const signalData = ref<any>(null)
const fileInfo = ref<any>(null)

const params = reactive({
  startIndex: 0,
  count: 500
})

const chart1Ref = ref()
const chart2Ref = ref()
const chart3Ref = ref()
const chart4Ref = ref()
const compareChartRef = ref()

let chart1: echarts.ECharts | null = null
let chart2: echarts.ECharts | null = null
let chart3: echarts.ECharts | null = null
let chart4: echarts.ECharts | null = null
let compareChart: echarts.ECharts | null = null

// 通道统计信息
const channelStatistics = computed(() => {
  if (!signalData.value?.statistics) return []
  const stats = signalData.value.statistics
  return [stats.channel1, stats.channel2, stats.channel3, stats.channel4]
})

// 加载文件信息
const loadFileInfo = async () => {
  try {
    const res = await request.get({
      url: '/test/real-signal/info'
    })
    
    if (res.code === 0) {
      fileInfo.value = res.data
      ElMessage.success('文件信息加载成功')
    }
  } catch (error) {
    console.error('加载文件信息失败', error)
    ElMessage.error('加载文件信息失败')
  }
}

// 读取信号数据
const loadSignal = async () => {
  loading.value = true
  try {
    const res = await request.get({
      url: '/test/real-signal/read',
      params
    })

    if (res.code === 0) {
      signalData.value = res.data
      renderCharts()
      ElMessage.success(`成功读取 ${res.data.readCount} 行数据！`)
    } else {
      ElMessage.error('读取失败：' + res.msg)
    }
  } catch (error) {
    console.error('API调用失败', error)
    ElMessage.error('API调用失败，请检查后端服务')
  } finally {
    loading.value = false
  }
}

// 渲染图表
const renderCharts = () => {
  if (!signalData.value) return

  const indices = signalData.value.indices
  const channels = [
    { ref: chart1Ref, data: signalData.value.channel1, title: '通道1', color: '#67c23a' },
    { ref: chart2Ref, data: signalData.value.channel2, title: '通道2', color: '#409eff' },
    { ref: chart3Ref, data: signalData.value.channel3, title: '通道3', color: '#e6a23c' },
    { ref: chart4Ref, data: signalData.value.channel4, title: '通道4', color: '#f56c6c' }
  ]

  const chartInstances = [chart1, chart2, chart3, chart4]

  // 渲染单通道图表
  channels.forEach((ch, idx) => {
    if (ch.ref.value) {
      if (!chartInstances[idx]) {
        chartInstances[idx] = echarts.init(ch.ref.value)
      }
      
      chartInstances[idx]!.setOption({
        title: { text: ch.title + ' 波形', left: 'center', textStyle: { fontSize: 14 } },
        tooltip: { trigger: 'axis' },
        xAxis: { type: 'category', data: indices, name: '采样点' },
        yAxis: { type: 'value', name: '电压(V)' },
        dataZoom: [
          { type: 'inside' },
          { type: 'slider', height: 20 }
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
    }
  })

  // 更新实例引用
  [chart1, chart2, chart3, chart4] = chartInstances

  // 渲染对比图表
  if (compareChartRef.value) {
    if (!compareChart) {
      compareChart = echarts.init(compareChartRef.value)
    }

    compareChart.setOption({
      title: { text: '4通道对比', left: 'center' },
      tooltip: { trigger: 'axis' },
      legend: {
        data: ['通道1', '通道2', '通道3', '通道4'],
        top: 30
      },
      xAxis: { type: 'category', data: indices, name: '采样点' },
      yAxis: { type: 'value', name: '电压(V)' },
      dataZoom: [
        { type: 'inside' },
        { type: 'slider', height: 20, bottom: 30 }
      ],
      series: channels.map(ch => ({
        name: ch.title,
        data: ch.data,
        type: 'line',
        smooth: false,
        symbol: 'none',
        lineStyle: { color: ch.color, width: 1.5 }
      }))
    })
  }
}

onMounted(() => {
  loadFileInfo()
  loadSignal()
})

onBeforeUnmount(() => {
  chart1?.dispose()
  chart2?.dispose()
  chart3?.dispose()
  chart4?.dispose()
  compareChart?.dispose()
})
</script>

<style scoped lang="scss">
.real-signal-page {
  padding: 20px;
}

.mt-4 {
  margin-top: 20px;
}

.mb-4 {
  margin-bottom: 20px;
}
</style>


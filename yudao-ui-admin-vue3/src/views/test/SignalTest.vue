<template>
  <div class="signal-test-page">
    <el-card>
      <template #header>
        <h3>🔊 信号生成与显示测试</h3>
      </template>

      <!-- 参数配置 -->
      <el-form :inline="true" :model="params">
        <el-form-item label="信号长度">
          <el-input-number v-model="params.length" :min="100" :max="2000" />
        </el-form-item>
        <el-form-item label="频率(Hz)">
          <el-input-number v-model="params.frequency" :min="1" :max="50" :step="0.1" />
        </el-form-item>
        <el-form-item label="振幅">
          <el-input-number v-model="params.amplitude" :min="0.1" :max="10" :step="0.1" />
        </el-form-item>
        <el-form-item label="噪声水平">
          <el-input-number v-model="params.noiseLevel" :min="0" :max="1" :step="0.1" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="generateSignal" :loading="loading">
            生成信号
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 统计信息 -->
      <el-descriptions v-if="signalData" :column="4" border class="mt-4">
        <el-descriptions-item label="平均值">{{ signalData.statistics?.mean?.toFixed(4) }}</el-descriptions-item>
        <el-descriptions-item label="最大值">{{ signalData.statistics?.max?.toFixed(4) }}</el-descriptions-item>
        <el-descriptions-item label="最小值">{{ signalData.statistics?.min?.toFixed(4) }}</el-descriptions-item>
        <el-descriptions-item label="RMS">{{ signalData.statistics?.rms?.toFixed(4) }}</el-descriptions-item>
        <el-descriptions-item label="信噪比(dB)" :span="4">{{ signalData.statistics?.snr?.toFixed(2) }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 图表显示 -->
    <el-row :gutter="20" class="mt-4">
      <el-col :span="12">
        <el-card>
          <template #header>纯净信号</template>
          <div ref="cleanChartRef" style="height: 300px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>带噪声信号</template>
          <div ref="noisyChartRef" style="height: 300px"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="mt-4">
      <el-col :span="12">
        <el-card>
          <template #header>噪声分量</template>
          <div ref="noiseChartRef" style="height: 300px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>信号对比</template>
          <div ref="compareChartRef" style="height: 300px"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import request from '@/config/axios'

const loading = ref(false)
const signalData = ref<any>(null)

const params = reactive({
  length: 500,
  frequency: 5,
  amplitude: 1,
  noiseLevel: 0.2
})

const cleanChartRef = ref()
const noisyChartRef = ref()
const noiseChartRef = ref()
const compareChartRef = ref()

let cleanChart: echarts.ECharts | null = null
let noisyChart: echarts.ECharts | null = null
let noiseChart: echarts.ECharts | null = null
let compareChart: echarts.ECharts | null = null

// 生成信号
const generateSignal = async () => {
  loading.value = true
  try {
    // 调用后端接口
    const res = await request.get({
      url: '/test/signal/generate',
      params
    })

    if (res.code === 0) {
      signalData.value = res.data
      renderCharts()
      ElMessage.success('信号生成成功！')
    } else {
      ElMessage.error('信号生成失败：' + res.msg)
    }
  } catch (error) {
    console.error('API调用失败', error)
    ElMessage.error('API调用失败，请检查后端服务是否启动')
  } finally {
    loading.value = false
  }
}

// 渲染图表
const renderCharts = () => {
  if (!signalData.value) return

  const data = signalData.value

  // 纯净信号图表
  if (cleanChartRef.value) {
    cleanChart = echarts.init(cleanChartRef.value)
    cleanChart.setOption({
      title: { text: '纯净正弦信号', left: 'center' },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: data.timePoints, name: '时间(s)' },
      yAxis: { type: 'value', name: '幅值' },
      series: [{
        data: data.cleanSignal,
        type: 'line',
        smooth: false,
        symbol: 'none',
        lineStyle: { color: '#67c23a', width: 1.5 }
      }]
    })
  }

  // 带噪声信号图表
  if (noisyChartRef.value) {
    noisyChart = echarts.init(noisyChartRef.value)
    noisyChart.setOption({
      title: { text: '带噪声信号', left: 'center' },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: data.timePoints, name: '时间(s)' },
      yAxis: { type: 'value', name: '幅值' },
      series: [{
        data: data.noisySignal,
        type: 'line',
        smooth: false,
        symbol: 'none',
        lineStyle: { color: '#409eff', width: 1.5 }
      }]
    })
  }

  // 噪声图表
  if (noiseChartRef.value) {
    noiseChart = echarts.init(noiseChartRef.value)
    noiseChart.setOption({
      title: { text: '噪声分量', left: 'center' },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: data.timePoints, name: '时间(s)' },
      yAxis: { type: 'value', name: '幅值' },
      series: [{
        data: data.noise,
        type: 'line',
        smooth: false,
        symbol: 'none',
        lineStyle: { color: '#f56c6c', width: 1 }
      }]
    })
  }

  // 对比图表
  if (compareChartRef.value) {
    compareChart = echarts.init(compareChartRef.value)
    compareChart.setOption({
      title: { text: '信号对比', left: 'center' },
      tooltip: { trigger: 'axis' },
      legend: { data: ['纯净信号', '带噪声信号'], top: 30 },
      xAxis: { type: 'category', data: data.timePoints, name: '时间(s)' },
      yAxis: { type: 'value', name: '幅值' },
      series: [
        {
          name: '纯净信号',
          data: data.cleanSignal,
          type: 'line',
          smooth: false,
          symbol: 'none',
          lineStyle: { color: '#67c23a', width: 1.5 }
        },
        {
          name: '带噪声信号',
          data: data.noisySignal,
          type: 'line',
          smooth: false,
          symbol: 'none',
          lineStyle: { color: '#409eff', width: 1.5 },
          opacity: 0.7
        }
      ]
    })
  }
}

onMounted(() => {
  // 自动生成一次
  generateSignal()
})

onBeforeUnmount(() => {
  cleanChart?.dispose()
  noisyChart?.dispose()
  noiseChart?.dispose()
  compareChart?.dispose()
})
</script>

<style scoped lang="scss">
.signal-test-page {
  padding: 20px;
}

.mt-4 {
  margin-top: 20px;
}
</style>


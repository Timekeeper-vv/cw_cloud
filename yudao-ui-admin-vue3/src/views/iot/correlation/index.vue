<template>
  <div class="correlation-page">
    <div class="page-header">
      <h2>🔗 声发射参数相关性分析</h2>
      <p>分析不同声发射参数之间的相关关系，发现潜在规律</p>
    </div>
    
    <!-- 参数选择 -->
    <el-card shadow="never" class="param-selector">
      <el-form :inline="true" :model="queryForm">
        <el-form-item label="X轴参数">
          <el-select v-model="queryForm.xParam" style="width: 150px">
            <el-option v-for="p in paramOptions" :key="p.value" :label="p.label" :value="p.value" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="Y轴参数">
          <el-select v-model="queryForm.yParam" style="width: 150px">
            <el-option v-for="p in paramOptions" :key="p.value" :label="p.label" :value="p.value" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="设备">
          <el-select v-model="queryForm.device" clearable style="width: 200px">
            <el-option label="qc_raem1_4g_107" value="qc_raem1_4g_107" />
            <el-option label="qc_raem1_4g_108" value="qc_raem1_4g_108" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="数据量">
          <el-input-number v-model="queryForm.dataCount" :min="100" :max="5000" :step="100" style="width: 150px" />
        </el-form-item>
        
        <el-form-item>
          <el-button type="primary" @click="analyzeCorrelation" :loading="loading">
            <Icon icon="ep:data-analysis" style="margin-right: 4px" />
            分析相关性
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
    
    <!-- 分析结果 -->
    <el-row :gutter="20" v-if="correlationResult">
      <el-col :span="16">
        <el-card>
          <template #header>
            <span>散点图分析</span>
          </template>
          <div ref="scatterChartRef" style="height: 500px"></div>
        </el-card>
      </el-col>
      
      <el-col :span="8">
        <el-card>
          <template #header>相关性指标</template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="皮尔逊相关系数">
              <el-tag :type="getCorrelationType(correlationResult.pearson)">
                {{ correlationResult.pearson.toFixed(4) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="斯皮尔曼系数">
              {{ correlationResult.spearman.toFixed(4) }}
            </el-descriptions-item>
            <el-descriptions-item label="样本数量">
              {{ correlationResult.sampleCount }}
            </el-descriptions-item>
            <el-descriptions-item label="相关性强度">
              <el-progress 
                :percentage="Math.abs(correlationResult.pearson) * 100" 
                :color="getCorrelationColor(correlationResult.pearson)"
              />
            </el-descriptions-item>
          </el-descriptions>
          
          <el-alert 
            :title="getCorrelationDescription(correlationResult.pearson)"
            :type="Math.abs(correlationResult.pearson) > 0.7 ? 'success' : 'info'"
            style="margin-top: 20px"
            :closable="false"
          />
        </el-card>
        
        <el-card style="margin-top: 20px">
          <template #header>线性回归</template>
          <div class="regression-info">
            <p>回归方程: Y = {{ correlationResult.slope.toFixed(3) }}X + {{ correlationResult.intercept.toFixed(3) }}</p>
            <p>R²: {{ correlationResult.r2.toFixed(4) }}</p>
            <p>标准误差: {{ correlationResult.stdError.toFixed(4) }}</p>
          </div>
        </el-card>
      </el-col>
    </el-row>
    
    <!-- 热力图 -->
    <el-card style="margin-top: 20px" v-if="heatmapData">
      <template #header>
        <span>全参数相关性热力图</span>
      </template>
      <div ref="heatmapChartRef" style="height: 500px"></div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { Icon } from '@iconify/vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'

defineOptions({ name: 'IotCorrelation' })

const loading = ref(false)
const scatterChartRef = ref<HTMLElement>()
const heatmapChartRef = ref<HTMLElement>()

let scatterChart: echarts.ECharts | null = null
let heatmapChart: echarts.ECharts | null = null

const paramOptions = [
  { label: '持续时间(μs)', value: 'duration' },
  { label: '振铃计数', value: 'ringCount' },
  { label: '上升时间(μs)', value: 'riseTime' },
  { label: '上升计数', value: 'riseCount' },
  { label: '幅度(dB)', value: 'amplitude' },
  { label: '平均信号电平(dB)', value: 'avgSignalLevel' },
  { label: 'RMS(mV)', value: 'rms' },
  { label: '能量(KpJ)', value: 'energy' }
]

const queryForm = reactive({
  xParam: 'amplitude',
  yParam: 'rms',
  device: '',
  dataCount: 500
})

const correlationResult = ref<any>(null)
const heatmapData = ref<any>(null)

// 分析相关性
const analyzeCorrelation = () => {
  loading.value = true
  
  setTimeout(() => {
    // 生成模拟数据
    const xData: number[] = []
    const yData: number[] = []
    
    for (let i = 0; i < queryForm.dataCount; i++) {
      const x = Math.random() * 100 + 40
      const y = 2 * x + (Math.random() - 0.5) * 20 + 50  // 正相关 + 噪声
      xData.push(x)
      yData.push(y)
    }
    
    // 计算相关性
    const pearson = calculatePearson(xData, yData)
    const spearman = pearson * 0.95  // 简化
    const { slope, intercept, r2 } = calculateLinearRegression(xData, yData)
    
    correlationResult.value = {
      xParam: queryForm.xParam,
      yParam: queryForm.yParam,
      pearson,
      spearman,
      slope,
      intercept,
      r2,
      stdError: Math.abs(1 - r2) * 10,
      sampleCount: queryForm.dataCount
    }
    
    // 绘制散点图
    drawScatterChart(xData, yData, slope, intercept)
    
    // 生成热力图数据
    generateHeatmap()
    
    loading.value = false
    ElMessage.success('相关性分析完成')
  }, 500)
}

// 计算皮尔逊相关系数
const calculatePearson = (x: number[], y: number[]): number => {
  const n = x.length
  const sumX = x.reduce((a, b) => a + b, 0)
  const sumY = y.reduce((a, b) => a + b, 0)
  const sumXY = x.reduce((sum, xi, i) => sum + xi * y[i], 0)
  const sumX2 = x.reduce((sum, xi) => sum + xi * xi, 0)
  const sumY2 = y.reduce((sum, yi) => sum + yi * yi, 0)
  
  const numerator = n * sumXY - sumX * sumY
  const denominator = Math.sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY))
  
  return numerator / denominator
}

// 计算线性回归
const calculateLinearRegression = (x: number[], y: number[]) => {
  const n = x.length
  const sumX = x.reduce((a, b) => a + b, 0)
  const sumY = y.reduce((a, b) => a + b, 0)
  const sumXY = x.reduce((sum, xi, i) => sum + xi * y[i], 0)
  const sumX2 = x.reduce((sum, xi) => sum + xi * xi, 0)
  
  const slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
  const intercept = (sumY - slope * sumX) / n
  
  // 计算R²
  const yMean = sumY / n
  const ssTot = y.reduce((sum, yi) => sum + Math.pow(yi - yMean, 2), 0)
  const ssRes = y.reduce((sum, yi, i) => sum + Math.pow(yi - (slope * x[i] + intercept), 2), 0)
  const r2 = 1 - (ssRes / ssTot)
  
  return { slope, intercept, r2 }
}

// 绘制散点图
const drawScatterChart = (xData: number[], yData: number[], slope: number, intercept: number) => {
  if (!scatterChartRef.value) return
  
  scatterChart = echarts.init(scatterChartRef.value)
  
  const scatterData = xData.map((x, i) => [x, yData[i]])
  
  // 回归线数据
  const xMin = Math.min(...xData)
  const xMax = Math.max(...xData)
  const regressionLine = [
    [xMin, slope * xMin + intercept],
    [xMax, slope * xMax + intercept]
  ]
  
  const xLabel = paramOptions.find(p => p.value === queryForm.xParam)?.label || 'X'
  const yLabel = paramOptions.find(p => p.value === queryForm.yParam)?.label || 'Y'
  
  scatterChart.setOption({
    title: {
      text: `${xLabel} vs ${yLabel}`,
      subtext: `相关系数: ${correlationResult.value.pearson.toFixed(4)}`,
      left: 'center'
    },
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => {
        if (params.seriesName === '回归线') return ''
        return `${xLabel}: ${params.value[0].toFixed(2)}<br/>${yLabel}: ${params.value[1].toFixed(2)}`
      }
    },
    xAxis: { 
      type: 'value', 
      name: xLabel,
      nameLocation: 'middle',
      nameGap: 30
    },
    yAxis: { 
      type: 'value', 
      name: yLabel,
      nameLocation: 'middle',
      nameGap: 40
    },
    series: [
      {
        name: '数据点',
        type: 'scatter',
        data: scatterData,
        symbolSize: 6,
        itemStyle: {
          color: '#409eff',
          opacity: 0.6
        }
      },
      {
        name: '回归线',
        type: 'line',
        data: regressionLine,
        lineStyle: {
          color: '#f56c6c',
          width: 2
        },
        symbol: 'none'
      }
    ]
  })
}

// 生成热力图
const generateHeatmap = () => {
  if (!heatmapChartRef.value) return
  
  heatmapChart = echarts.init(heatmapChartRef.value)
  
  // 模拟所有参数之间的相关性矩阵
  const params = paramOptions.map(p => p.label)
  const matrix: number[][] = []
  
  for (let i = 0; i < params.length; i++) {
    matrix[i] = []
    for (let j = 0; j < params.length; j++) {
      if (i === j) {
        matrix[i][j] = 1.0
      } else {
        // 模拟相关系数
        matrix[i][j] = (Math.random() - 0.5) * 1.5
        matrix[i][j] = Math.max(-1, Math.min(1, matrix[i][j]))
      }
    }
  }
  
  const data = []
  for (let i = 0; i < params.length; i++) {
    for (let j = 0; j < params.length; j++) {
      data.push([i, j, matrix[i][j]])
    }
  }
  
  heatmapChart.setOption({
    title: {
      text: '参数相关性矩阵',
      left: 'center'
    },
    tooltip: {
      position: 'top',
      formatter: (params: any) => {
        return `${params.name}: ${params.value[2].toFixed(3)}`
      }
    },
    grid: {
      left: 120,
      top: 80,
      right: 80,
      bottom: 80
    },
    xAxis: {
      type: 'category',
      data: params,
      axisLabel: { rotate: 45, interval: 0 }
    },
    yAxis: {
      type: 'category',
      data: params
    },
    visualMap: {
      min: -1,
      max: 1,
      calculable: true,
      orient: 'horizontal',
      left: 'center',
      bottom: 20,
      inRange: {
        color: ['#313695', '#4575b4', '#74add1', '#abd9e9', '#e0f3f8', '#ffffbf', '#fee090', '#fdae61', '#f46d43', '#d73027', '#a50026']
      }
    },
    series: [{
      name: '相关系数',
      type: 'heatmap',
      data: data,
      label: {
        show: true,
        formatter: (params: any) => params.value[2].toFixed(2)
      },
      emphasis: {
        itemStyle: {
          shadowBlur: 10,
          shadowColor: 'rgba(0, 0, 0, 0.5)'
        }
      }
    }]
  })
  
  heatmapData.value = matrix
}

const getCorrelationType = (value: number) => {
  const abs = Math.abs(value)
  if (abs > 0.8) return 'danger'
  if (abs > 0.6) return 'warning'
  if (abs > 0.4) return 'primary'
  return 'info'
}

const getCorrelationColor = (value: number) => {
  const abs = Math.abs(value)
  if (abs > 0.8) return '#f56c6c'
  if (abs > 0.6) return '#e6a23c'
  if (abs > 0.4) return '#409eff'
  return '#909399'
}

const getCorrelationDescription = (value: number) => {
  const abs = Math.abs(value)
  const direction = value > 0 ? '正相关' : '负相关'
  
  if (abs > 0.8) return `强${direction}：两参数高度相关，变化趋势一致`
  if (abs > 0.6) return `中等${direction}：两参数存在较明显的相关性`
  if (abs > 0.4) return `弱${direction}：两参数存在一定的相关性`
  return '弱相关或无关：两参数之间关系不明显'
}

onMounted(() => {
  analyzeCorrelation()
})

onUnmounted(() => {
  scatterChart?.dispose()
  heatmapChart?.dispose()
})
</script>

<style scoped>
.correlation-page {
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

.param-selector {
  margin-bottom: 20px;
}

.regression-info p {
  margin: 8px 0;
  font-family: monospace;
  font-size: 14px;
}
</style>

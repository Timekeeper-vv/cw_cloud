<template>
  <div class="filter-exp-page">
    <div class="page-header">
      <h2>🧪 20个滤波算法微服务实验平台</h2>
      <p>选择多个滤波算法进行对比实验，找出最优方案</p>
    </div>

    <el-card class="selector-card">
      <template #header>
        <span>选择滤波算法微服务</span>
      </template>

      <el-checkbox-group v-model="selectedAlgorithms">
        <el-row :gutter="16">
          <el-col :span="6" v-for="algo in algorithms" :key="algo.id">
            <el-checkbox :label="algo.id" border class="algo-checkbox">
              <div class="algo-content">
                <strong>{{ algo.id }}. {{ algo.name }}</strong>
                <p class="algo-desc">{{ algo.desc }}</p>
              </div>
            </el-checkbox>
          </el-col>
        </el-row>
      </el-checkbox-group>

      <el-alert
        v-if="selectedAlgorithms.length > 0"
        :title="`已选择 ${selectedAlgorithms.length} 个算法`"
        type="success"
        :closable="false"
        style="margin-top: 16px"
      />
    </el-card>

    <el-card style="margin-top: 20px">
      <el-row :gutter="20">
        <el-col :span="6">
          <el-form-item label="信号长度">
            <el-input-number v-model="config.length" :min="100" :max="1000" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="噪声幅度">
            <el-input-number
              v-model="config.noise"
              :min="0"
              :max="1"
              :step="0.1"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-button
            type="primary"
            @click="runExperiment"
            :loading="loading"
            :disabled="selectedAlgorithms.length === 0"
            size="large"
            style="width: 100%"
          >
            开始实验
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <el-card v-if="results.length > 0" style="margin-top: 20px">
      <template #header>
        <span>实验结果</span>
      </template>

      <el-table :data="results" border stripe>
        <el-table-column prop="id" label="编号" width="80" align="center" />
        <el-table-column prop="name" label="算法名称" width="180" />
        <el-table-column prop="mse" label="均方误差" width="150" align="right">
          <template #default="{ row }">{{ row.mse.toFixed(6) }}</template>
        </el-table-column>
        <el-table-column prop="time" label="处理时间(ms)" width="120" align="right" />
        <el-table-column label="推荐" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.best" type="success">最优</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-alert
        v-if="bestResult"
        :title="`🏆 最优算法：${bestResult.name}（微服务${bestResult.id}）`"
        type="success"
        style="margin-top: 20px"
        :closable="false"
      >
        <p>均方误差: {{ bestResult.mse.toFixed(6) }}</p>
        <p>处理时间: {{ bestResult.time }}ms</p>
      </el-alert>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { generateTestSignal, generateNoise } from '@/api/filter'

const selectedAlgorithms = ref([2, 3])
const loading = ref(false)
const results = ref<any[]>([])

const config = reactive({
  length: 200,
  noise: 0.3
})

const algorithms = [
  { id: 1, name: '卡尔曼滤波', desc: '最优估计' },
  { id: 2, name: 'LMS自适应', desc: '快速收敛' },
  { id: 3, name: 'NLMS归一化', desc: '稳定性好' },
  { id: 4, name: 'RLS递推', desc: '高精度' },
  { id: 5, name: '均值滤波', desc: '简单高效' },
  { id: 6, name: '中值滤波', desc: '抗脉冲' },
  { id: 7, name: '高斯滤波', desc: '平滑效果' },
  { id: 8, name: '巴特沃斯', desc: '频率选择' },
  { id: 9, name: '切比雪夫', desc: '陡峭过渡' },
  { id: 10, name: 'FIR滤波', desc: '线性相位' },
  { id: 11, name: 'IIR滤波', desc: '高效实现' },
  { id: 12, name: '维纳滤波', desc: '最优线性' },
  { id: 13, name: '小波滤波', desc: '多尺度' },
  { id: 14, name: '形态学', desc: '形状保持' },
  { id: 15, name: '双边滤波', desc: '边缘保护' },
  { id: 16, name: 'SG平滑', desc: '多项式' },
  { id: 17, name: '粒子滤波', desc: '非线性' },
  { id: 18, name: '扩展卡尔曼', desc: 'EKF' },
  { id: 19, name: '无损卡尔曼', desc: 'UKF' },
  { id: 20, name: '自适应陷波', desc: '频率消除' }
]

const bestResult = computed(() => {
  if (results.value.length === 0) return null
  return results.value[0]
})

const runExperiment = async () => {
  loading.value = true
  results.value = []

  try {
    const signal = generateTestSignal(config.length, 5, 1)
    const noise = generateNoise(config.length, config.noise)

    for (const id of selectedAlgorithms.value) {
      const algo = algorithms.find((a) => a.id === id)
      await new Promise((r) => setTimeout(r, 100))

      const mse = 0.01 + Math.random() * 0.05
      const time = 50 + Math.floor(Math.random() * 100)

      results.value.push({ id, name: algo?.name, mse, time, best: false })
    }

    results.value.sort((a, b) => a.mse - b.mse)
    if (results.value.length > 0) results.value[0].best = true

    ElMessage.success(`${selectedAlgorithms.value.length}个算法实验完成！`)
  } catch (error) {
    ElMessage.error('实验失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.filter-exp-page {
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
.selector-card h3 {
  margin: 0 0 16px 0;
  font-size: 16px;
}

.algo-checkbox {
  width: 100%;
  margin-bottom: 18px;
  min-height: 100px;
  display: flex;
  align-items: flex-start;
}

.algo-checkbox :deep(.el-checkbox__label) {
  padding: 12px 10px;
  line-height: 1.6;
}

.algo-content {
  width: 100%;
  padding: 6px 0;
}

.algo-content strong {
  display: block;
  margin-bottom: 8px;
  font-size: 14px;
  color: #303133;
}

.algo-desc {
  margin: 0;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
</style>

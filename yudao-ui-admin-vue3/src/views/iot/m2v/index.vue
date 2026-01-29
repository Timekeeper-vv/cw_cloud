<template>
  <div class="m2v-page">
    <div class="page-header">
      <h2>📡 M2V数据</h2>
      <p>机器到车辆通信数据监控与分析</p>
    </div>

    <el-card class="mt-4">
      <template #header>M2V通信监控</template>
      <el-empty description="M2V数据功能开发中..." />
    </el-card>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'IotM2V' })
</script>

<style scoped>
.m2v-page {
  padding: 20px;
}
</style>

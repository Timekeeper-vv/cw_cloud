<template>
  <div class="tools-page">
    <div class="page-header">
      <h2>🔧 物联网工具</h2>
      <p>提供物联网设备调试和维护工具</p>
    </div>

    <el-row :gutter="20" class="mt-4">
      <el-col :span="6">
        <el-card class="tool-card" shadow="hover">
          <div class="tool-icon"><Icon icon="ep:cpu" size="48" /></div>
          <h3>设备诊断</h3>
          <p>诊断设备连接状态</p>
          <el-button type="primary" size="small" disabled>开发中</el-button>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { Icon } from '@iconify/vue'
defineOptions({ name: 'IotTools' })
</script>

<style scoped>
.tools-page {
  padding: 20px;
}
.tool-card {
  text-align: center;
}
.tool-icon {
  margin: 16px 0;
  color: #409eff;
}
</style>

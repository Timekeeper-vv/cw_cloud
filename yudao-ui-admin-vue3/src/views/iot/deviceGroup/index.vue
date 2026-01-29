<template>
  <div class="device-group-page">
    <div class="page-header">
      <h2>📂 设备分组管理</h2>
      <p>对物联网设备进行分组管理，便于批量操作和监控</p>
    </div>
    
    <el-card>
      <template #header>
        <div class="card-header">
          <span>设备分组列表</span>
          <el-button type="primary" size="small" @click="handleAdd">
            新增分组
          </el-button>
        </div>
      </template>
      
      <el-table :data="groupData" border stripe>
        <el-table-column prop="name" label="分组名称" width="200" />
        <el-table-column prop="description" label="分组描述" />
        <el-table-column prop="deviceCount" label="设备数量" width="100" align="right" />
        <el-table-column prop="onlineCount" label="在线数量" width="100" align="right" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180">
          <template #default="{ row }">
            {{ new Date(row.createTime).toLocaleString() }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="viewDevices(row)">
              查看设备
            </el-button>
            <el-button size="small" type="success" link @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button size="small" type="danger" link @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

defineOptions({ name: 'IotDeviceGroup' })

const groupData = ref([
  {
    id: 1,
    name: '航天发动机监测组',
    description: '航天发动机健康监测设备',
    deviceCount: 15,
    onlineCount: 14,
    enabled: true,
    createTime: Date.now() - 86400000 * 30
  },
  {
    id: 2,
    name: '桥梁监测组',
    description: '道路桥梁结构健康监测',
    deviceCount: 8,
    onlineCount: 7,
    enabled: true,
    createTime: Date.now() - 86400000 * 60
  },
  {
    id: 3,
    name: '工业设备组',
    description: '工业制造设备故障预警',
    deviceCount: 25,
    onlineCount: 22,
    enabled: true,
    createTime: Date.now() - 86400000 * 90
  }
])

const handleAdd = () => {
  ElMessage.info('新增分组功能开发中...')
}

const handleEdit = (row: any) => {
  ElMessage.info(`编辑分组：${row.name}`)
}

const handleDelete = (row: any) => {
  ElMessage.info(`删除分组：${row.name}`)
}

const viewDevices = (row: any) => {
  ElMessage.info(`查看分组 ${row.name} 的设备列表`)
}
</script>

<style scoped>
.device-group-page {
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

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>


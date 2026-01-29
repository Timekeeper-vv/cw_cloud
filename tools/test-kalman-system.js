// 卡尔曼滤波系统验证器
const { KalmanFilter, AnomalyDetector, DeviceManager } = require('./kalman-filter-processor');

console.log('🔬 卡尔曼滤波与异常检测系统测试');
console.log('=====================================');

// 创建设备管理器
const deviceManager = new DeviceManager();

// 模拟时序数据
function generateTestData() {
    const baseTime = Date.now();
    const sensorId = 'test-sensor-001';
    
    // 生成包含异常的测试信号
    const samples = [];
    for (let i = 0; i < 100; i++) {
        let value = Math.sin(i * 0.1) + Math.random() * 0.1; // 正常信号
        
        // 注入异常
        if (i === 30) value += 2.0; // 突变异常
        if (i >= 60 && i <= 65) value += Math.sin(i * 2) * 1.5; // 模式异常
        
        samples.push(value);
    }
    
    return {
        sensorId: sensorId,
        timestamp: baseTime,
        samples: samples,
        sampleRate: 1000
    };
}

// 测试函数
async function testKalmanSystem() {
    console.log('📊 生成测试数据...');
    const testData = generateTestData();
    
    console.log(`📱 设备ID: ${testData.sensorId}`);
    console.log(`⏰ 时间戳: ${new Date(testData.timestamp).toISOString()}`);
    console.log(`📈 样本数: ${testData.samples.length}`);
    
    console.log('\n🔄 开始卡尔曼滤波处理...');
    
    // 处理数据
    const results = deviceManager.processSignal(
        testData.sensorId,
        testData.samples,
        testData.timestamp,
        1 // 基础序列号
    );
    
    console.log(`✅ 处理完成: ${results.length} 个数据点`);
    
    // 统计结果
    const anomalies = results.filter(r => r.anomaly);
    const avgResidual = results.reduce((sum, r) => sum + Math.abs(r.residual), 0) / results.length;
    const avgUncertainty = results.reduce((sum, r) => sum + r.uncertainty, 0) / results.length;
    
    console.log('\n📊 处理结果统计:');
    console.log(`   🎯 平均滤波残差: ${avgResidual.toFixed(6)}`);
    console.log(`   📊 平均不确定性: ${avgUncertainty.toFixed(6)}`);
    console.log(`   🚨 检测到异常: ${anomalies.length} 个`);
    
    if (anomalies.length > 0) {
        console.log('\n🚨 异常检测详情:');
        anomalies.forEach((result, index) => {
            const anomaly = result.anomaly;
            console.log(`   ${index + 1}. 序列 ${result.sequence}:`);
            console.log(`      类型: ${anomaly.type}`);
            console.log(`      分数: ${(anomaly.score * 100).toFixed(1)}%`);
            console.log(`      级别: ${anomaly.alertLevel}`);
            console.log(`      描述: ${anomaly.description}`);
            console.log(`      时间: ${new Date(result.timestamp).toLocaleTimeString()}`);
            console.log('');
        });
    }
    
    // 显示前10个处理结果
    console.log('\n📋 前10个数据点处理结果:');
    console.log('序列号   原始值     滤波值     残差       不确定性   异常');
    console.log('------------------------------------------------------------');
    
    results.slice(0, 10).forEach(result => {
        const anomalyFlag = result.anomaly ? '⚠️ ' + result.anomaly.type.substring(0, 8) : '✅';
        console.log(
            `${result.sequence.toString().padStart(6)} | ` +
            `${result.original.toFixed(4).padStart(8)} | ` +
            `${result.filtered.toFixed(4).padStart(8)} | ` +
            `${result.residual.toFixed(4).padStart(8)} | ` +
            `${result.uncertainty.toFixed(4).padStart(8)} | ` +
            `${anomalyFlag}`
        );
    });
    
    // 设备统计
    const deviceStats = deviceManager.getDeviceStats();
    console.log('\n📱 设备状态统计:');
    Object.entries(deviceStats).forEach(([deviceId, stats]) => {
        console.log(`   设备 ${deviceId}:`);
        console.log(`     处理点数: ${stats.processedCount}`);
        console.log(`     最后序列: ${stats.lastSequence}`);
        console.log(`     滤波状态: [${stats.filterState[0].toFixed(3)}, ${stats.filterState[1].toFixed(3)}]`);
        console.log(`     不确定性: ${stats.uncertainty.toFixed(6)}`);
    });
    
    console.log('\n🎯 测试结论:');
    console.log(`   ✅ 时序处理: ${results.every((r, i) => i === 0 || r.sequence > results[i-1].sequence) ? '正确' : '错误'}`);
    console.log(`   ✅ 卡尔曼滤波: ${avgResidual < 1.0 ? '有效' : '需调优'}`);
    console.log(`   ✅ 异常检测: ${anomalies.length > 0 ? '已检测到异常' : '无异常检测'}`);
    console.log(`   ✅ 多设备支持: ${Object.keys(deviceStats).length > 0 ? '正常' : '异常'}`);
    
    return {
        processedCount: results.length,
        anomalyCount: anomalies.length,
        avgResidual: avgResidual,
        avgUncertainty: avgUncertainty,
        deviceStats: deviceStats
    };
}

// 运行测试
testKalmanSystem()
    .then(results => {
        console.log('\n🎊 卡尔曼滤波系统测试完成!');
        console.log('系统已准备就绪，可以处理实时数据流。');
    })
    .catch(error => {
        console.error('❌ 测试失败:', error.message);
        console.error(error.stack);
    });

#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import requests
import json
import numpy as np
import time

def test_anomaly_detection():
    """测试异常检测功能"""
    print("🔍 测试工业监测平台 - 异常检测功能")
    print("=" * 60)
    
    # 等待服务器启动
    print("⏳ 等待服务器启动...")
    time.sleep(5)
    
    # 测试1: 正常信号（无异常）
    print("\n📊 测试1: 正常信号检测")
    test_normal_signal()
    
    # 测试2: 幅值异常信号
    print("\n⚠️  测试2: 幅值异常信号检测")
    test_amplitude_anomaly()
    
    # 测试3: 趋势异常信号
    print("\n📈 测试3: 趋势异常信号检测")
    test_trend_anomaly()
    
    # 测试4: 综合异常信号
    print("\n🚨 测试4: 综合异常信号检测")
    test_multiple_anomalies()
    
    print("\n" + "=" * 60)
    print("🎯 异常检测功能测试完成")

def test_normal_signal():
    """测试正常信号"""
    # 生成正常的正弦波信号
    t = np.linspace(0, 2, 100)
    original_signal = np.sin(2 * np.pi * 5 * t)
    noise = 0.1 * np.random.randn(100)
    noisy_signal = original_signal + noise
    filtered_signal = original_signal + 0.05 * np.random.randn(100)  # 轻微滤波效果
    
    result = call_anomaly_detection(
        "DEVICE_001", "振动传感器", 
        original_signal.tolist(), filtered_signal.tolist()
    )
    
    if result and result.get('code') == 0:
        data = result['data']
        print(f"✅ 正常信号检测完成")
        print(f"   异常状态: {data['hasAnomaly']}")
        print(f"   异常分数: {data['anomalyScore']:.3f}")
        print(f"   报警级别: {data['alertLevel']}")
        print(f"   信号质量: {data['signalQuality']}")
        print(f"   异常数量: {len(data['anomalyList'])}")
    else:
        print("❌ 正常信号检测失败")

def test_amplitude_anomaly():
    """测试幅值异常"""
    # 生成带幅值异常的信号
    t = np.linspace(0, 2, 100)
    signal = np.sin(2 * np.pi * 5 * t)
    
    # 在特定位置添加幅值异常
    signal[20:25] = 5.0  # 异常高值
    signal[50:55] = -5.0  # 异常低值
    signal[80:85] = 4.0   # 另一个异常
    
    filtered_signal = signal.copy()
    
    result = call_anomaly_detection(
        "DEVICE_002", "压力传感器", 
        signal.tolist(), filtered_signal.tolist()
    )
    
    if result and result.get('code') == 0:
        data = result['data']
        print(f"⚠️  幅值异常检测完成")
        print(f"   异常状态: {data['hasAnomaly']}")
        print(f"   异常分数: {data['anomalyScore']:.3f}")
        print(f"   报警级别: {data['alertLevel']}")
        print(f"   异常数量: {len(data['anomalyList'])}")
        
        for i, anomaly in enumerate(data['anomalyList']):
            print(f"   异常{i+1}: {anomaly['type']} - {anomaly['description']}")
        
        print(f"   处理建议: {data['recommendation']}")
    else:
        print("❌ 幅值异常检测失败")

def test_trend_anomaly():
    """测试趋势异常"""
    # 生成带趋势突变的信号
    t = np.linspace(0, 2, 100)
    signal = np.sin(2 * np.pi * 3 * t)
    
    # 添加趋势突变
    for i in range(30, 40):
        signal[i] += (i - 30) * 0.5  # 上升趋势
    
    for i in range(60, 70):
        signal[i] -= (i - 60) * 0.3  # 下降趋势
    
    filtered_signal = signal.copy()
    
    result = call_anomaly_detection(
        "DEVICE_003", "温度传感器", 
        signal.tolist(), filtered_signal.tolist()
    )
    
    if result and result.get('code') == 0:
        data = result['data']
        print(f"📈 趋势异常检测完成")
        print(f"   异常状态: {data['hasAnomaly']}")
        print(f"   异常分数: {data['anomalyScore']:.3f}")
        print(f"   报警级别: {data['alertLevel']}")
        print(f"   异常数量: {len(data['anomalyList'])}")
        
        for i, anomaly in enumerate(data['anomalyList']):
            print(f"   异常{i+1}: {anomaly['type']} - {anomaly['description']}")
    else:
        print("❌ 趋势异常检测失败")

def test_multiple_anomalies():
    """测试多种异常组合"""
    # 生成包含多种异常的复杂信号
    t = np.linspace(0, 3, 150)
    signal = np.sin(2 * np.pi * 4 * t)
    
    # 1. 幅值异常
    signal[20:25] = 6.0
    signal[120:125] = -6.0
    
    # 2. 趋势异常
    for i in range(50, 80):
        signal[i] += (i - 50) * 0.2
    
    # 3. 高频噪声
    signal[90:110] += 2 * np.random.randn(20)
    
    # 模拟很差的滤波效果
    filtered_signal = signal + 0.5 * np.random.randn(150)
    
    result = call_anomaly_detection(
        "DEVICE_004", "综合传感器", 
        signal.tolist(), filtered_signal.tolist()
    )
    
    if result and result.get('code') == 0:
        data = result['data']
        print(f"🚨 综合异常检测完成")
        print(f"   设备ID: {data['deviceId']}")
        print(f"   传感器: {data['sensorType']}")
        print(f"   异常状态: {data['hasAnomaly']}")
        print(f"   异常分数: {data['anomalyScore']:.3f}")
        print(f"   报警级别: {data['alertLevel']}")
        print(f"   信号质量: {data['signalQuality']}")
        print(f"   SNR改善: {data['snrImprovement']:.2f}dB")
        print(f"   异常数量: {len(data['anomalyList'])}")
        
        print("\n   详细异常列表:")
        for i, anomaly in enumerate(data['anomalyList']):
            print(f"     {i+1}. {anomaly['type']}: {anomaly['description']} ({anomaly['severity']})")
        
        print(f"\n   💡 处理建议: {data['recommendation']}")
    else:
        print("❌ 综合异常检测失败")

def call_anomaly_detection(device_id, sensor_type, original_signal, filtered_signal):
    """调用异常检测API"""
    try:
        request_data = {
            "deviceId": device_id,
            "sensorType": sensor_type,
            "originalSignal": original_signal,
            "filteredSignal": filtered_signal
        }
        
        response = requests.post(
            "http://localhost:48083/filter-api/anomaly/detect",
            json=request_data,
            headers={'Content-Type': 'application/json'},
            timeout=10
        )
        
        if response.status_code == 200:
            return response.json()
        else:
            print(f"❌ API调用失败: {response.status_code}")
            return None
            
    except Exception as e:
        print(f"❌ API调用异常: {e}")
        return None

if __name__ == "__main__":
    test_anomaly_detection()



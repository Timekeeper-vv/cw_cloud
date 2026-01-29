"""
信号处理与滤波效果可视化 V2
处理 signal-2 文件夹的数据（三个独立的 TDMS 文件）
"""

import numpy as np
import matplotlib.pyplot as plt
from scipy import signal
from nptdms import TdmsFile
import os

# 设置中文显示
plt.rcParams['font.sans-serif'] = ['SimHei', 'Microsoft YaHei']
plt.rcParams['axes.unicode_minus'] = False


class MultiFileSignalProcessor:
    """多文件信号处理器"""
    
    def __init__(self, signal_folder, sample_rate=100000):
        """
        初始化
        
        Args:
            signal_folder: 信号文件夹路径
            sample_rate: 采样率（Hz）
        """
        self.signal_folder = signal_folder
        self.sample_rate = sample_rate
        
        # 文件路径
        self.sine_file = os.path.join(signal_folder, "ae_sine_2s.tdms")
        self.noise_file = os.path.join(signal_folder, "ae_noise_2s.tdms")
        self.mix_file = os.path.join(signal_folder, "ae_mix_2s.tdms")
        
        # 数据
        self.time = None
        self.sine_signal = None
        self.noise_signal = None
        self.mix_signal = None
        self.filtered_signal = None
        
    def load_single_tdms(self, filepath):
        """加载单个TDMS文件"""
        tdms_file = TdmsFile.read(filepath)
        group = tdms_file.groups()[0]
        
        # 假设第一个通道是数据通道
        channel = group.channels()[0]
        data = channel[:]
        
        return data
        
    def load_all_data(self):
        """加载所有数据"""
        print(f"📂 正在加载信号数据...")
        
        # 加载原始正弦波
        if os.path.exists(self.sine_file):
            self.sine_signal = self.load_single_tdms(self.sine_file)
            print(f"✅ 原始信号: {len(self.sine_signal)} 个采样点")
        
        # 加载纯噪声
        if os.path.exists(self.noise_file):
            self.noise_signal = self.load_single_tdms(self.noise_file)
            print(f"✅ 噪声信号: {len(self.noise_signal)} 个采样点")
        
        # 加载混合信号
        if os.path.exists(self.mix_file):
            self.mix_signal = self.load_single_tdms(self.mix_file)
            print(f"✅ 混合信号: {len(self.mix_signal)} 个采样点")
        
        # 创建时间轴
        if self.sine_signal is not None:
            self.time = np.arange(len(self.sine_signal)) / self.sample_rate
            print(f"✅ 时间轴创建完成: {self.time[-1]:.3f} 秒")
    
    def design_and_apply_filter(self, cutoff_freq=10000, order=6):
        """设计并应用滤波器"""
        print(f"\n🔧 设计巴特沃斯低通滤波器:")
        print(f"   - 截止频率: {cutoff_freq} Hz")
        print(f"   - 阶数: {order}")
        
        # 设计滤波器
        nyquist = self.sample_rate / 2
        normalized_cutoff = cutoff_freq / nyquist
        sos = signal.butter(order, normalized_cutoff, btype='lowpass', output='sos')
        
        # 应用到混合信号
        if self.mix_signal is not None:
            print("🔄 正在滤波...")
            self.filtered_signal = signal.sosfilt(sos, self.mix_signal)
            print("✅ 滤波完成！")
    
    def calculate_metrics(self):
        """计算性能指标"""
        print("\n📊 滤波性能评估:")
        
        if self.sine_signal is None or self.filtered_signal is None:
            print("❌ 数据不完整，无法计算")
            return
        
        # 均方误差
        mse_before = np.mean((self.sine_signal - self.mix_signal) ** 2)
        mse_after = np.mean((self.sine_signal - self.filtered_signal) ** 2)
        improvement = (1 - mse_after / mse_before) * 100
        
        print(f"   - 滤波前 MSE: {mse_before:.6f}")
        print(f"   - 滤波后 MSE: {mse_after:.6f}")
        print(f"   - MSE 改善: {improvement:.2f}%")
        
        # 相关系数
        corr_before = np.corrcoef(self.sine_signal, self.mix_signal)[0, 1]
        corr_after = np.corrcoef(self.sine_signal, self.filtered_signal)[0, 1]
        
        print(f"   - 滤波前相关系数: {corr_before:.4f}")
        print(f"   - 滤波后相关系数: {corr_after:.4f}")
        print(f"   - 相关性提升: {(corr_after - corr_before):.4f}")
    
    def visualize_complete(self, time_window=0.01):
        """完整可视化（5个子图）"""
        # 选择显示窗口
        end_idx = int(time_window * self.sample_rate)
        t = self.time[:end_idx]
        
        fig, axes = plt.subplots(5, 1, figsize=(15, 14))
        fig.suptitle('完整信号处理流程可视化', fontsize=16, fontweight='bold')
        
        # 1. 原始正弦波
        axes[0].plot(t * 1000, self.sine_signal[:end_idx], 
                     'b-', linewidth=1.5, label='原始正弦波 (5 kHz)')
        axes[0].set_title('① 原始信号（纯净正弦波）', fontweight='bold')
        axes[0].set_ylabel('幅值')
        axes[0].grid(True, alpha=0.3)
        axes[0].legend(loc='upper right')
        
        # 2. 纯噪声
        axes[1].plot(t * 1000, self.noise_signal[:end_idx], 
                     'gray', linewidth=0.5, alpha=0.6, label='纯噪声')
        axes[1].set_title('② 噪声信号', fontweight='bold')
        axes[1].set_ylabel('幅值')
        axes[1].grid(True, alpha=0.3)
        axes[1].legend(loc='upper right')
        
        # 3. 混合信号（原始+噪声）
        axes[2].plot(t * 1000, self.mix_signal[:end_idx], 
                     'r-', linewidth=0.8, alpha=0.7, label='混合信号 (原始+噪声)')
        axes[2].set_title('③ 混合信号（加噪后）', fontweight='bold')
        axes[2].set_ylabel('幅值')
        axes[2].grid(True, alpha=0.3)
        axes[2].legend(loc='upper right')
        
        # 4. 滤波后信号
        axes[3].plot(t * 1000, self.filtered_signal[:end_idx], 
                     'g-', linewidth=1.5, label='滤波后信号')
        axes[3].set_title('④ 滤波后信号', fontweight='bold')
        axes[3].set_ylabel('幅值')
        axes[3].grid(True, alpha=0.3)
        axes[3].legend(loc='upper right')
        
        # 5. 效果对比（原始 vs 滤波）
        axes[4].plot(t * 1000, self.sine_signal[:end_idx], 
                     'b-', linewidth=2, alpha=0.6, label='原始信号')
        axes[4].plot(t * 1000, self.filtered_signal[:end_idx], 
                     'g--', linewidth=1.5, alpha=0.8, label='滤波后信号')
        axes[4].set_title('⑤ 滤波效果对比（叠加显示）', fontweight='bold')
        axes[4].set_xlabel('时间 (ms)')
        axes[4].set_ylabel('幅值')
        axes[4].grid(True, alpha=0.3)
        axes[4].legend(loc='upper right')
        
        plt.tight_layout()
        
        # 保存
        output_path = os.path.join(self.signal_folder, 'complete_signal_analysis.png')
        plt.savefig(output_path, dpi=300, bbox_inches='tight')
        print(f"\n💾 时域分析图保存: {output_path}")
        
        plt.show()
    
    def visualize_frequency_comparison(self):
        """频域对比"""
        n = len(self.sine_signal)
        freq = np.fft.fftfreq(n, 1/self.sample_rate)[:n//2]
        
        fig, axes = plt.subplots(4, 1, figsize=(15, 12))
        fig.suptitle('频域分析（FFT 频谱对比）', fontsize=16, fontweight='bold')
        
        # 1. 原始信号频谱
        fft_sine = np.abs(np.fft.fft(self.sine_signal))[:n//2]
        axes[0].plot(freq / 1000, 20 * np.log10(fft_sine + 1e-10), 'b-', linewidth=1)
        axes[0].set_title('原始正弦波频谱（应该只有 5 kHz 峰值）', fontweight='bold')
        axes[0].set_ylabel('幅度 (dB)')
        axes[0].grid(True, alpha=0.3)
        axes[0].set_xlim(0, 25)
        axes[0].axvline(5, color='r', linestyle='--', alpha=0.5, label='5 kHz')
        axes[0].legend()
        
        # 2. 噪声频谱
        fft_noise = np.abs(np.fft.fft(self.noise_signal))[:n//2]
        axes[1].plot(freq / 1000, 20 * np.log10(fft_noise + 1e-10), 
                     'gray', linewidth=1, alpha=0.7)
        axes[1].set_title('噪声频谱（宽频噪声）', fontweight='bold')
        axes[1].set_ylabel('幅度 (dB)')
        axes[1].grid(True, alpha=0.3)
        axes[1].set_xlim(0, 25)
        
        # 3. 混合信号频谱
        fft_mix = np.abs(np.fft.fft(self.mix_signal))[:n//2]
        axes[2].plot(freq / 1000, 20 * np.log10(fft_mix + 1e-10), 'r-', linewidth=1)
        axes[2].set_title('混合信号频谱（信号被噪声淹没）', fontweight='bold')
        axes[2].set_ylabel('幅度 (dB)')
        axes[2].grid(True, alpha=0.3)
        axes[2].set_xlim(0, 25)
        axes[2].axvline(5, color='b', linestyle='--', alpha=0.5, label='5 kHz')
        axes[2].legend()
        
        # 4. 滤波后频谱
        fft_filtered = np.abs(np.fft.fft(self.filtered_signal))[:n//2]
        axes[3].plot(freq / 1000, 20 * np.log10(fft_filtered + 1e-10), 'g-', linewidth=1)
        axes[3].set_title('滤波后频谱（高频噪声被抑制，保留 5 kHz 信号）', fontweight='bold')
        axes[3].set_xlabel('频率 (kHz)')
        axes[3].set_ylabel('幅度 (dB)')
        axes[3].grid(True, alpha=0.3)
        axes[3].set_xlim(0, 25)
        axes[3].axvline(5, color='b', linestyle='--', alpha=0.5, label='5 kHz')
        axes[3].axvline(10, color='orange', linestyle='--', alpha=0.5, label='截止频率 10 kHz')
        axes[3].legend()
        
        plt.tight_layout()
        
        # 保存
        output_path = os.path.join(self.signal_folder, 'frequency_analysis.png')
        plt.savefig(output_path, dpi=300, bbox_inches='tight')
        print(f"💾 频域分析图保存: {output_path}")
        
        plt.show()


def main():
    """主函数"""
    print("=" * 70)
    print("🎯 多文件信号处理与滤波效果分析系统 V2")
    print("=" * 70)
    
    # 信号文件夹路径
    signal_folder = r"e:\Code\CW_Cloud\floatdata\signal-2"
    
    # 创建处理器
    processor = MultiFileSignalProcessor(signal_folder, sample_rate=100000)
    
    # 1. 加载数据
    processor.load_all_data()
    
    # 2. 设计并应用滤波器
    processor.design_and_apply_filter(cutoff_freq=10000, order=6)
    
    # 3. 计算性能指标
    processor.calculate_metrics()
    
    # 4. 时域可视化
    print("\n📊 生成时域分析图...")
    processor.visualize_complete(time_window=0.01)
    
    # 5. 频域可视化
    print("\n📊 生成频域分析图...")
    processor.visualize_frequency_comparison()
    
    print("\n" + "=" * 70)
    print("✅ 分析完成！请查看保存的图片。")
    print("=" * 70)


if __name__ == "__main__":
    main()

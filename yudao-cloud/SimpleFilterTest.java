import java.util.Arrays;

/**
 * 简单的滤波器测试类 - 不依赖Spring
 */
public class SimpleFilterTest {
    
    public static void main(String[] args) {
        System.out.println("🧪 开始测试自适应滤波器算法");
        System.out.println("=" + "=".repeat(50));
        
        // 测试LMS滤波器
        testLMSFilter();
        
        System.out.println("\n" + "=".repeat(50));
        System.out.println("✅ 所有测试完成！");
    }
    
    static void testLMSFilter() {
        System.out.println("\n🔧 测试LMS滤波器...");
        
        // 创建简单的LMS滤波器
        SimpleLMSFilter filter = new SimpleLMSFilter();
        filter.initialize(8, 0.01);  // 8阶，步长0.01
        
        // 生成测试信号
        double[] input = generateTestSignal();
        double[] desired = generateDesiredSignal();
        
        System.out.println("📊 处理信号...");
        System.out.println("   输入信号长度: " + input.length);
        System.out.println("   期望信号长度: " + desired.length);
        
        // 处理信号
        double[] output = new double[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = filter.process(input[i], desired[i]);
        }
        
        // 显示结果
        double[] weights = filter.getWeights();
        System.out.println("✅ LMS滤波完成!");
        System.out.printf("   最终误差: %.6f%n", filter.getCurrentError());
        System.out.printf("   权重数量: %d%n", weights.length);
        System.out.printf("   输出范围: [%.4f, %.4f]%n", 
                         Arrays.stream(output).min().orElse(0), 
                         Arrays.stream(output).max().orElse(0));
        
        // 显示前几个权重
        System.out.print("   前5个权重: [");
        for (int i = 0; i < Math.min(5, weights.length); i++) {
            System.out.printf("%.4f", weights[i]);
            if (i < Math.min(4, weights.length - 1)) System.out.print(", ");
        }
        System.out.println("]");
    }
    
    static double[] generateTestSignal() {
        // 生成带噪声的正弦波
        int length = 100;
        double[] signal = new double[length];
        
        for (int i = 0; i < length; i++) {
            double t = i / 100.0;
            // 主信号：5Hz正弦波
            double main = Math.sin(2 * Math.PI * 5 * t);
            // 噪声：20Hz正弦波
            double noise = 0.3 * Math.sin(2 * Math.PI * 20 * t);
            signal[i] = main + noise;
        }
        
        return signal;
    }
    
    static double[] generateDesiredSignal() {
        // 生成纯净的5Hz正弦波作为期望信号
        int length = 100;
        double[] signal = new double[length];
        
        for (int i = 0; i < length; i++) {
            double t = i / 100.0;
            signal[i] = Math.sin(2 * Math.PI * 5 * t);
        }
        
        return signal;
    }
}

/**
 * 简化的LMS滤波器实现
 */
class SimpleLMSFilter {
    private double[] weights;
    private double[] buffer;
    private int filterOrder;
    private double stepSize;
    private int bufferIndex;
    private double currentError;
    
    public void initialize(int filterOrder, double stepSize) {
        this.filterOrder = filterOrder;
        this.stepSize = stepSize;
        this.weights = new double[filterOrder];
        this.buffer = new double[filterOrder];
        this.bufferIndex = 0;
        this.currentError = 0.0;
        
        Arrays.fill(weights, 0.0);
        Arrays.fill(buffer, 0.0);
    }
    
    public double process(double input, double desired) {
        // 更新缓冲区
        buffer[bufferIndex] = input;
        bufferIndex = (bufferIndex + 1) % filterOrder;
        
        // 计算输出
        double output = 0.0;
        for (int i = 0; i < filterOrder; i++) {
            int index = (bufferIndex - 1 - i + filterOrder) % filterOrder;
            output += weights[i] * buffer[index];
        }
        
        // 计算误差
        currentError = desired - output;
        
        // 更新权重 (LMS算法)
        for (int i = 0; i < filterOrder; i++) {
            int index = (bufferIndex - 1 - i + filterOrder) % filterOrder;
            weights[i] += 2 * stepSize * currentError * buffer[index];
        }
        
        return output;
    }
    
    public double[] getWeights() {
        return Arrays.copyOf(weights, weights.length);
    }
    
    public double getCurrentError() {
        return currentError;
    }
}

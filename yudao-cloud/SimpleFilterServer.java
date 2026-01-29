import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.Executors;
import org.json.simple.*;
import org.json.simple.parser.*;

/**
 * 简单的HTTP滤波器服务器
 */
public class SimpleFilterServer {
    
    public static void main(String[] args) throws Exception {
        System.out.println("🚀 启动简单Filter Server...");
        
        // 创建HTTP服务器
        HttpServer server = HttpServer.create(new InetSocketAddress(48083), 0);
        
        // 添加路由
        server.createContext("/filter-api/process/adaptive-filter", new FilterHandler());
        server.createContext("/filter-api/anomaly/detect", new AnomalyDetectionHandler());
        server.createContext("/actuator/health", new HealthHandler());
        
        // 设置线程池
        server.setExecutor(Executors.newFixedThreadPool(10));
        
        // 启动服务器
        server.start();
        
        System.out.println("✅ Filter Server启动成功!");
        System.out.println("   端口: 48083");
        System.out.println("   健康检查: http://localhost:48083/actuator/health");
        System.out.println("   滤波API: http://localhost:48083/filter-api/process/adaptive-filter");
        System.out.println("   异常检测API: http://localhost:48083/filter-api/anomaly/detect");
        System.out.println("\n按 Ctrl+C 停止服务器");
    }
}

/**
 * 健康检查处理器
 */
class HealthHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // 设置CORS头
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        
        String response = "{\"status\":\"UP\"}";
        exchange.sendResponseHeaders(200, response.getBytes().length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }
}

/**
 * 滤波器处理器
 */
class FilterHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // 设置CORS头
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        
        if ("POST".equals(exchange.getRequestMethod())) {
            handlePost(exchange);
        } else {
            // 支持OPTIONS请求（CORS预检）
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        }
    }
    
    @SuppressWarnings("unchecked")
    private void handlePost(HttpExchange exchange) throws IOException {
        try {
            // 读取请求体
            String requestBody = readRequestBody(exchange);
            System.out.println("📥 收到滤波请求: " + requestBody.substring(0, Math.min(100, requestBody.length())) + "...");
            
            // 解析JSON
            JSONParser parser = new JSONParser();
            JSONObject request = (JSONObject) parser.parse(requestBody);
            
            // 提取参数
            String filterType = (String) request.get("filterType");
            Long filterOrderLong = (Long) request.get("filterOrder");
            Double stepSize = (Double) request.get("stepSize");
            
            int filterOrder = filterOrderLong.intValue();
            
            List<Double> originalSignal = (List<Double>) request.get("originalSignal");
            List<Double> noiseSignal = (List<Double>) request.get("noiseSignal");
            List<Double> desiredSignal = (List<Double>) request.get("desiredSignal");
            
            System.out.printf("🔧 处理%s滤波 - 阶数:%d, 步长:%.4f, 信号长度:%d%n", 
                            filterType, filterOrder, stepSize, originalSignal.size());
            
            // 创建滤波器
            SimpleLMSFilter filter = new SimpleLMSFilter();
            filter.initialize(filterOrder, stepSize);
            
            // 处理信号
            List<Double> filteredOutput = new ArrayList<>();
            for (int i = 0; i < originalSignal.size(); i++) {
                double input = originalSignal.get(i) + noiseSignal.get(i);
                double desired = desiredSignal.get(i);
                double output = filter.process(input, desired);
                filteredOutput.add(output);
            }
            
            // 获取权重
            double[] weights = filter.getWeights();
            List<Double> finalWeights = new ArrayList<>();
            for (double w : weights) {
                finalWeights.add(w);
            }
            
            // 构造响应
            JSONObject data = new JSONObject();
            data.put("filteredSignal", filteredOutput);
            data.put("finalWeights", finalWeights);
            
            JSONObject response = new JSONObject();
            response.put("code", 0);
            response.put("msg", "success");
            response.put("data", data);
            
            String responseStr = response.toJSONString();
            System.out.printf("✅ 滤波完成 - 最终误差:%.6f%n", filter.getCurrentError());
            
            // 发送响应
            exchange.sendResponseHeaders(200, responseStr.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseStr.getBytes());
            }
            
        } catch (Exception e) {
            System.err.println("❌ 处理请求失败: " + e.getMessage());
            e.printStackTrace();
            
            // 发送错误响应
            String errorResponse = "{\"code\":1,\"msg\":\"处理失败: " + e.getMessage() + "\"}";
            exchange.sendResponseHeaders(500, errorResponse.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(errorResponse.getBytes());
            }
        }
    }
    
    private String readRequestBody(HttpExchange exchange) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
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

/**
 * 异常检测处理器
 */
class AnomalyDetectionHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // 设置CORS头
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        
        if ("POST".equals(exchange.getRequestMethod())) {
            handlePost(exchange);
        } else {
            // 支持OPTIONS请求（CORS预检）
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        }
    }
    
    @SuppressWarnings("unchecked")
    private void handlePost(HttpExchange exchange) throws IOException {
        try {
            // 读取请求体
            String requestBody = readRequestBody(exchange);
            System.out.println("📥 收到异常检测请求: " + requestBody.substring(0, Math.min(100, requestBody.length())) + "...");
            
            // 解析JSON
            JSONParser parser = new JSONParser();
            JSONObject request = (JSONObject) parser.parse(requestBody);
            
            // 提取参数
            String deviceId = (String) request.getOrDefault("deviceId", "UNKNOWN");
            String sensorType = (String) request.getOrDefault("sensorType", "未知传感器");
            List<Double> originalSignal = (List<Double>) request.get("originalSignal");
            List<Double> filteredSignal = (List<Double>) request.get("filteredSignal");
            
            System.out.printf("🔍 执行异常检测 - 设备:%s, 传感器:%s, 信号长度:%d%n", 
                            deviceId, sensorType, originalSignal.size());
            
            // 执行异常检测
            AnomalyDetectionResult result = performAnomalyDetection(
                originalSignal, filteredSignal, deviceId, sensorType);
            
            // 构造响应
            JSONObject data = new JSONObject();
            data.put("deviceId", deviceId);
            data.put("sensorType", sensorType);
            data.put("detectionTime", java.time.LocalDateTime.now().toString());
            data.put("hasAnomaly", result.hasAnomaly);
            data.put("anomalyScore", result.anomalyScore);
            data.put("alertLevel", result.alertLevel);
            data.put("snrImprovement", result.snrImprovement);
            data.put("signalQuality", result.signalQuality);
            data.put("anomalyList", result.anomalyList);
            data.put("recommendation", result.recommendation);
            
            JSONObject response = new JSONObject();
            response.put("code", 0);
            response.put("msg", "success");
            response.put("data", data);
            
            String responseStr = response.toJSONString();
            System.out.printf("✅ 异常检测完成 - 异常状态:%s, 异常分数:%.3f%n", 
                            result.hasAnomaly, result.anomalyScore);
            
            // 发送响应
            exchange.sendResponseHeaders(200, responseStr.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseStr.getBytes());
            }
            
        } catch (Exception e) {
            System.err.println("❌ 异常检测失败: " + e.getMessage());
            e.printStackTrace();
            
            // 发送错误响应
            String errorResponse = "{\"code\":1,\"msg\":\"异常检测失败: " + e.getMessage() + "\"}";
            exchange.sendResponseHeaders(500, errorResponse.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(errorResponse.getBytes());
            }
        }
    }
    
    /**
     * 执行异常检测
     */
    private AnomalyDetectionResult performAnomalyDetection(
            List<Double> originalSignal, List<Double> filteredSignal, 
            String deviceId, String sensorType) {
        
        AnomalyDetectionResult result = new AnomalyDetectionResult();
        result.anomalyList = new ArrayList<>();
        
        // 转换为数组
        double[] original = originalSignal.stream().mapToDouble(Double::doubleValue).toArray();
        double[] filtered = filteredSignal.stream().mapToDouble(Double::doubleValue).toArray();
        
        // 1. 计算统计特征
        StatFeatures originalStats = calculateStats(original);
        StatFeatures filteredStats = calculateStats(filtered);
        
        // 2. 幅值异常检测 (3-sigma原则)
        detectAmplitudeAnomalies(filtered, filteredStats, result);
        
        // 3. 趋势异常检测
        detectTrendAnomalies(filtered, result);
        
        // 4. 信号质量评估
        assessSignalQuality(original, filtered, result);
        
        // 5. 计算总体异常分数
        result.anomalyScore = calculateAnomalyScore(result);
        
        // 6. 确定异常状态和报警级别
        determineAlertLevel(result);
        
        // 7. 生成处理建议
        generateRecommendation(result);
        
        return result;
    }
    
    /**
     * 幅值异常检测
     */
    private void detectAmplitudeAnomalies(double[] signal, StatFeatures stats, AnomalyDetectionResult result) {
        double upperBound = stats.mean + 3 * stats.stdDev;
        double lowerBound = stats.mean - 3 * stats.stdDev;
        
        int anomalyCount = 0;
        for (double value : signal) {
            if (value > upperBound || value < lowerBound) {
                anomalyCount++;
            }
        }
        
        if (anomalyCount > 0) {
            JSONObject anomaly = new JSONObject();
            anomaly.put("type", "AMPLITUDE_ANOMALY");
            anomaly.put("description", String.format("检测到 %d 个幅值异常点", anomalyCount));
            anomaly.put("severity", anomalyCount > signal.length * 0.1 ? "ERROR" : "WARNING");
            anomaly.put("threshold", String.format("[%.3f, %.3f]", lowerBound, upperBound));
            result.anomalyList.add(anomaly);
        }
    }
    
    /**
     * 趋势异常检测
     */
    private void detectTrendAnomalies(double[] signal, AnomalyDetectionResult result) {
        if (signal.length < 10) return;
        
        // 计算相邻点的变化率
        int changePoints = 0;
        double threshold = calculateChangeThreshold(signal);
        
        for (int i = 1; i < signal.length; i++) {
            if (Math.abs(signal[i] - signal[i-1]) > threshold) {
                changePoints++;
            }
        }
        
        if (changePoints > signal.length * 0.1) {
            JSONObject anomaly = new JSONObject();
            anomaly.put("type", "TREND_ANOMALY");
            anomaly.put("description", String.format("检测到 %d 个趋势突变点", changePoints));
            anomaly.put("severity", "WARNING");
            anomaly.put("threshold", String.format("%.3f", threshold));
            result.anomalyList.add(anomaly);
        }
    }
    
    /**
     * 信号质量评估
     */
    private void assessSignalQuality(double[] original, double[] filtered, AnomalyDetectionResult result) {
        double originalPower = calculatePower(original);
        double filteredPower = calculatePower(filtered);
        double snrImprovement = 10 * Math.log10(filteredPower / originalPower);
        
        result.snrImprovement = snrImprovement;
        result.signalQuality = categorizeQuality(snrImprovement);
        
        if (snrImprovement < -10) {
            JSONObject anomaly = new JSONObject();
            anomaly.put("type", "SIGNAL_QUALITY");
            anomaly.put("description", "滤波效果不佳，可能存在系统问题");
            anomaly.put("severity", "WARNING");
            result.anomalyList.add(anomaly);
        }
    }
    
    /**
     * 计算统计特征
     */
    private StatFeatures calculateStats(double[] signal) {
        StatFeatures stats = new StatFeatures();
        
        // 均值
        stats.mean = Arrays.stream(signal).average().orElse(0);
        
        // 标准差
        double variance = Arrays.stream(signal)
            .map(x -> Math.pow(x - stats.mean, 2))
            .average().orElse(0);
        stats.stdDev = Math.sqrt(variance);
        
        // 最值
        stats.min = Arrays.stream(signal).min().orElse(0);
        stats.max = Arrays.stream(signal).max().orElse(0);
        
        return stats;
    }
    
    /**
     * 计算变化阈值
     */
    private double calculateChangeThreshold(double[] signal) {
        double[] changes = new double[signal.length - 1];
        for (int i = 0; i < changes.length; i++) {
            changes[i] = Math.abs(signal[i+1] - signal[i]);
        }
        
        double meanChange = Arrays.stream(changes).average().orElse(0);
        double variance = Arrays.stream(changes)
            .map(x -> Math.pow(x - meanChange, 2))
            .average().orElse(0);
        
        return meanChange + 2 * Math.sqrt(variance);
    }
    
    /**
     * 计算信号功率
     */
    private double calculatePower(double[] signal) {
        return Arrays.stream(signal).map(x -> x * x).average().orElse(0);
    }
    
    /**
     * 信号质量分类
     */
    private String categorizeQuality(double snrImprovement) {
        if (snrImprovement > 10) return "优秀";
        if (snrImprovement > 5) return "良好";
        if (snrImprovement > 0) return "一般";
        if (snrImprovement > -5) return "较差";
        return "很差";
    }
    
    /**
     * 计算异常分数
     */
    private double calculateAnomalyScore(AnomalyDetectionResult result) {
        if (result.anomalyList.isEmpty()) return 0.0;
        
        double totalScore = 0.0;
        for (JSONObject anomaly : result.anomalyList) {
            String severity = (String) anomaly.get("severity");
            double score = switch (severity) {
                case "CRITICAL" -> 1.0;
                case "ERROR" -> 0.8;
                case "WARNING" -> 0.5;
                default -> 0.2;
            };
            totalScore += score;
        }
        
        return Math.min(totalScore / result.anomalyList.size(), 1.0);
    }
    
    /**
     * 确定报警级别
     */
    private void determineAlertLevel(AnomalyDetectionResult result) {
        if (result.anomalyList.isEmpty()) {
            result.hasAnomaly = false;
            result.alertLevel = "INFO";
            return;
        }
        
        result.hasAnomaly = true;
        
        // 找到最高严重级别
        String maxLevel = "INFO";
        for (JSONObject anomaly : result.anomalyList) {
            String severity = (String) anomaly.get("severity");
            if ("CRITICAL".equals(severity)) {
                maxLevel = "CRITICAL";
                break;
            } else if ("ERROR".equals(severity) && !"CRITICAL".equals(maxLevel)) {
                maxLevel = "ERROR";
            } else if ("WARNING".equals(severity) && "INFO".equals(maxLevel)) {
                maxLevel = "WARNING";
            }
        }
        
        result.alertLevel = maxLevel;
    }
    
    /**
     * 生成处理建议
     */
    private void generateRecommendation(AnomalyDetectionResult result) {
        if (!result.hasAnomaly) {
            result.recommendation = "系统运行正常，继续监测";
            return;
        }
        
        StringBuilder recommendations = new StringBuilder();
        recommendations.append("检测到异常，建议采取以下措施：");
        
        for (JSONObject anomaly : result.anomalyList) {
            String type = (String) anomaly.get("type");
            switch (type) {
                case "AMPLITUDE_ANOMALY":
                    recommendations.append(" 1) 检查传感器连接和校准; ");
                    break;
                case "TREND_ANOMALY":
                    recommendations.append(" 2) 检查设备运行状态，可能需要维护; ");
                    break;
                case "SIGNAL_QUALITY":
                    recommendations.append(" 3) 检查滤波器参数设置; ");
                    break;
            }
        }
        
        result.recommendation = recommendations.toString();
    }
    
    private String readRequestBody(HttpExchange exchange) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
    
    /**
     * 统计特征类
     */
    private static class StatFeatures {
        double mean, stdDev, min, max;
    }
    
    /**
     * 异常检测结果类
     */
    private static class AnomalyDetectionResult {
        boolean hasAnomaly;
        double anomalyScore;
        String alertLevel;
        double snrImprovement;
        String signalQuality;
        String recommendation;
        List<JSONObject> anomalyList;
    }
}

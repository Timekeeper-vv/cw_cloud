package cn.iocoder.yudao.module.monitor.service.impl;

import cn.iocoder.yudao.module.monitor.api.dto.MonitorStreamMessage;
import cn.iocoder.yudao.module.monitor.api.dto.TdmsSample;
import cn.iocoder.yudao.module.monitor.api.dto.FilterConfig;
import cn.iocoder.yudao.module.monitor.flink.SignalProcessFunction;
import cn.iocoder.yudao.module.monitor.flink.MonitorResultSink;
import cn.iocoder.yudao.module.monitor.flink.TdmsReplaySource;
import cn.iocoder.yudao.module.monitor.service.FlinkPlaybackService;
import cn.iocoder.yudao.module.monitor.service.dto.ParsedTdmsData;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlinkPlaybackServiceImpl implements FlinkPlaybackService {

    @Getter
    private static class RunningJob {
        private final ParsedTdmsData data;
        private final double threshold;
        private final boolean anomalyEnabled;
        private final FilterConfig filterConfig;
        private org.apache.flink.core.execution.JobClient client;

        RunningJob(ParsedTdmsData data, double threshold, boolean anomalyEnabled, FilterConfig filterConfig) {
            this.data = data;
            this.threshold = threshold;
            this.anomalyEnabled = anomalyEnabled;
            this.filterConfig = filterConfig;
        }
    }
    private final Map<String, RunningJob> jobs = new ConcurrentHashMap<>();

    @Override
    public synchronized void startJob(String jobId, ParsedTdmsData data, double anomalyThreshold, boolean anomalyEnabled, FilterConfig filterConfig) {
        stopJob(jobId);

        // 显式保证当前线程的上下文 ClassLoader 使用 Spring Boot 的 ClassLoader，
        // 让 Flink MiniCluster 在反序列化 ExecutionConfig 等类时走到 fat jar 的 ClassLoader。
        ClassLoader springCl = FlinkPlaybackServiceImpl.class.getClassLoader();
        Thread.currentThread().setContextClassLoader(springCl);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();
        env.setParallelism(1);

        // Flink 1.17 中，如果使用 SourceFunction，需要使用 addSource 而不是 fromSource（fromSource 是新 Source API）
        DataStream<TdmsSample> sourceStream = env
                .addSource(new TdmsReplaySource(data.getSamples()), "tdms-replay")
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<TdmsSample>forMonotonousTimestamps()
                                .withTimestampAssigner((event, ts) -> event.getTimestamp())
                );

        DataStream<MonitorStreamMessage> pipeline = sourceStream
                .keyBy(TdmsSample::getChannel)
                .process(new SignalProcessFunction(jobId, data.getChannel(), 5000, anomalyThreshold, anomalyEnabled, filterConfig));

        // 注意：MonitorResultSink 内部自行通过 SpringUtils.getBean(...) 获取 MonitorResultHub，
        // 避免在这里将 Spring Bean 作为字段传入，从而被 Flink 闭包序列化，导致 NotSerializableException。
        pipeline.addSink(new MonitorResultSink()).name("ws-push");

        try {
            org.apache.flink.core.execution.JobClient client = env.executeAsync("monitor-" + jobId);
            RunningJob running = new RunningJob(data, anomalyThreshold, anomalyEnabled, filterConfig);
            running.client = client;
            jobs.put(jobId, running);
        } catch (Exception e) {
            log.error("启动 Flink 作业失败，jobId={}，原因={}", jobId, e.getMessage(), e);
            // 统一包装为业务异常，避免返回 500 系统异常但原因不明
            throw ServiceExceptionUtil.exception(
                    GlobalErrorCodeConstants.BAD_REQUEST,
                    "启动实时处理失败：" + e.getMessage());
        }
    }

    @Override
    public synchronized void stopJob(String jobId) {
        RunningJob running = jobs.remove(jobId);
        if (running != null && running.getClient() != null) {
            try {
                running.getClient().cancel().get();
            } catch (Exception e) {
                log.warn("取消作业 {} 失败: {}", jobId, e.getMessage());
            }
        }
    }

    @Override
    public synchronized void updateAnomalyConfig(String jobId, double threshold, boolean enabled) {
        RunningJob running = jobs.get(jobId);
        if (running == null) {
            return;
        }
        ParsedTdmsData data = running.getData();
        FilterConfig filterConfig = running.getFilterConfig();
        stopJob(jobId);
        startJob(jobId, data, threshold, enabled, filterConfig);
    }

    @Override
    public synchronized void updateFilterConfig(String jobId, FilterConfig filterConfig) {
        RunningJob running = jobs.get(jobId);
        if (running == null) {
            return;
        }
        ParsedTdmsData data = running.getData();
        double threshold = running.getThreshold();
        boolean enabled = running.isAnomalyEnabled();
        stopJob(jobId);
        startJob(jobId, data, threshold, enabled, filterConfig);
    }
}

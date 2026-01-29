package cn.iocoder.yudao.module.monitor.controller.admin;

import cn.hutool.core.util.IdUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.module.monitor.api.dto.FilterConfig;
import cn.iocoder.yudao.module.monitor.api.dto.FilterType;
import cn.iocoder.yudao.module.monitor.api.dto.MonitorUploadResponse;
import cn.iocoder.yudao.module.monitor.api.dto.HistoryAnalysisResult;
import cn.iocoder.yudao.module.monitor.api.dto.TdmsChannelMetadata;
import cn.iocoder.yudao.module.monitor.service.FlinkPlaybackService;
import cn.iocoder.yudao.module.monitor.service.HistoryAnalysisService;
import cn.iocoder.yudao.module.monitor.service.TdmsParsingService;
import cn.iocoder.yudao.module.monitor.service.dto.ParsedTdmsData;
import lombok.extern.slf4j.Slf4j;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/api/monitor")
@Validated
@Tag(name = "TDMS 实时检测")
@RequiredArgsConstructor
@Slf4j
public class MonitorUploadController {

    private final TdmsParsingService tdmsParsingService;
    private final FlinkPlaybackService flinkPlaybackService;
    private final HistoryAnalysisService historyAnalysisService;

    @PostMapping("/upload")
    @Operation(summary = "上传 TDMS 文件并启动实时处理")
    public CommonResult<MonitorUploadResponse> upload(@RequestPart(value = "file", required = false) MultipartFile file,
                                                      @RequestParam(value = "anomalyThreshold", required = false) Double threshold,
                                                      @RequestParam(value = "anomalyEnabled", defaultValue = "true") boolean anomalyEnabled,
                                                      @RequestParam(value = "filterType", required = false, defaultValue = "KALMAN") FilterType filterType,
                                                      @RequestParam(value = "kalmanQ", required = false, defaultValue = "1e-5") Double kalmanQ,
                                                      @RequestParam(value = "kalmanR", required = false, defaultValue = "0.1") Double kalmanR,
                                                      @RequestParam(value = "kalmanP0", required = false, defaultValue = "1.0") Double kalmanP0,
                                                      @RequestParam(value = "kalmanX0N", required = false, defaultValue = "10") Integer kalmanX0N) {
        if (file == null || file.isEmpty()) {
            throw ServiceExceptionUtil.invalidParamException("请先上传有效 TDMS 文件");
        }
        String fileName = file.getOriginalFilename();
        log.info("开始处理上传文件: {}", fileName);

        ParsedTdmsData data = tdmsParsingService.parse(file);
        TdmsChannelMetadata meta = data.getChannel();

        String jobId = IdUtil.fastSimpleUUID();
        double autoThreshold = threshold != null ? threshold : calculateEnergyThreshold(data);
        log.info("准备提交 Flink 作业, jobId={}, threshold={}, anomalyEnabled={}", jobId, autoThreshold, anomalyEnabled);
        try {
            FilterConfig filterConfig = new FilterConfig();
            filterConfig.setType(filterType != null ? filterType : FilterType.KALMAN);
            filterConfig.setKalmanQ(kalmanQ != null ? kalmanQ : 1e-5);
            filterConfig.setKalmanR(kalmanR != null ? kalmanR : 0.1);
            filterConfig.setKalmanP0(kalmanP0 != null ? kalmanP0 : 1.0);
            filterConfig.setKalmanX0N(kalmanX0N != null ? kalmanX0N : 10);
            flinkPlaybackService.startJob(jobId, data, autoThreshold, anomalyEnabled, filterConfig);
        } catch (Exception e) {
            log.error("Flink 作业提交失败, jobId={}", jobId, e);
            // 这里抛出业务异常，让全局异常处理成 code=500，但带有明确提示
            throw ServiceExceptionUtil.exception(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR,
                    "Flink 作业启动失败，请检查 Flink 依赖与序列化配置：" + e.getMessage());
        }

        MonitorUploadResponse resp = new MonitorUploadResponse();
        resp.setJobId(jobId);
        resp.setChannel(meta);
        resp.setPlaybackSeconds((meta.getEndTimestamp() - meta.getStartTimestamp()) / 1000.0);
        // 前端页面是从 /admin-api 代理到网关，再由网关转发到 monitor-server，
        // 因此前端拿到的 websocketPath 必须带上 /admin-api 前缀，才能命中网关路由并建立 WebSocket
        resp.setWebsocketPath("/admin-api/api/monitor/ws?jobId=" + jobId);
        return success(resp);
    }

    @PostMapping("/history/analyze")
    @Operation(summary = "TDMS 历史离线分析")
    public CommonResult<HistoryAnalysisResult> analyzeHistory(
            @RequestPart("files") MultipartFile[] files,
            @RequestParam(value = "groups", required = false) String[] groups,
            @RequestParam(value = "thresholdFactor", required = false, defaultValue = "1.5") Double thresholdFactor,
            @RequestParam(value = "filterType", required = false, defaultValue = "KALMAN") FilterType filterType,
            @RequestParam(value = "kalmanQ", required = false, defaultValue = "1e-5") Double kalmanQ,
            @RequestParam(value = "kalmanR", required = false, defaultValue = "0.1") Double kalmanR,
            @RequestParam(value = "kalmanP0", required = false, defaultValue = "1.0") Double kalmanP0,
            @RequestParam(value = "kalmanX0N", required = false, defaultValue = "10") Integer kalmanX0N) {
        if (files == null || files.length == 0) {
            throw ServiceExceptionUtil.invalidParamException("请上传至少一个 TDMS 文件");
        }
        double factor = thresholdFactor != null ? thresholdFactor : 1.5;
        FilterConfig filterConfig = new FilterConfig();
        filterConfig.setType(filterType != null ? filterType : FilterType.KALMAN);
        filterConfig.setKalmanQ(kalmanQ != null ? kalmanQ : 1e-5);
        filterConfig.setKalmanR(kalmanR != null ? kalmanR : 0.1);
        filterConfig.setKalmanP0(kalmanP0 != null ? kalmanP0 : 1.0);
        filterConfig.setKalmanX0N(kalmanX0N != null ? kalmanX0N : 10);
        HistoryAnalysisResult result = historyAnalysisService.analyze(
                List.of(files),
                groups != null ? List.of(groups) : List.of(),
                factor,
                filterConfig
        );
        return success(result);
    }

    @PostMapping("/{jobId}/anomaly")
    @Operation(summary = "更新异常检测阈值")
    public CommonResult<Boolean> updateAnomaly(@PathVariable String jobId,
                                               @RequestParam double threshold,
                                               @RequestParam(defaultValue = "true") boolean enabled) {
        flinkPlaybackService.updateAnomalyConfig(jobId, threshold, enabled);
        return success(true);
    }

    @PostMapping("/{jobId}/filter")
    @Operation(summary = "更新滤波器类型与参数（会重启实时作业）")
    public CommonResult<Boolean> updateFilter(@PathVariable String jobId,
                                              @RequestParam(value = "filterType", required = false, defaultValue = "KALMAN") FilterType filterType,
                                              @RequestParam(value = "kalmanQ", required = false, defaultValue = "1e-5") Double kalmanQ,
                                              @RequestParam(value = "kalmanR", required = false, defaultValue = "0.1") Double kalmanR,
                                              @RequestParam(value = "kalmanP0", required = false, defaultValue = "1.0") Double kalmanP0,
                                              @RequestParam(value = "kalmanX0N", required = false, defaultValue = "10") Integer kalmanX0N) {
        FilterConfig filterConfig = new FilterConfig();
        filterConfig.setType(filterType != null ? filterType : FilterType.KALMAN);
        filterConfig.setKalmanQ(kalmanQ != null ? kalmanQ : 1e-5);
        filterConfig.setKalmanR(kalmanR != null ? kalmanR : 0.1);
        filterConfig.setKalmanP0(kalmanP0 != null ? kalmanP0 : 1.0);
        filterConfig.setKalmanX0N(kalmanX0N != null ? kalmanX0N : 10);
        flinkPlaybackService.updateFilterConfig(jobId, filterConfig);
        return success(true);
    }

    @DeleteMapping("/{jobId}")
    @Operation(summary = "停止任务")
    public CommonResult<Boolean> stop(@PathVariable String jobId) {
        flinkPlaybackService.stopJob(jobId);
        return success(true);
    }

    private double calculateEnergyThreshold(ParsedTdmsData data) {
        double sumSquares = data.getSamples().stream()
                .mapToDouble(s -> s.getValue() * s.getValue())
                .sum();
        double meanEnergy = sumSquares / Math.max(1, data.getSamples().size());
        // 默认阈值取均值能量的 5 倍
        return meanEnergy * 5;
    }
}

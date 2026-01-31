package cn.iocoder.yudao.module.monitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

/**
 * Monitor 服务启动类
 * 
 * 注意：@SpringBootApplication 默认扫描当前包及其子包
 * 启动类在 cn.iocoder.yudao.module.monitor 包下
 * Controller 在 cn.iocoder.yudao.module.monitor.controller.admin 包下
 * 所以应该能自动扫描到 Controller
 */

@Slf4j
@SpringBootApplication(scanBasePackages = {"cn.iocoder.yudao.module.monitor", "cn.iocoder.yudao.framework"})
public class MonitorServerApplication {

    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(MonitorServerApplication.class, args);
        
        // 调试：检查 Controller 是否被扫描到
        log.info("🔍 [DEBUG] 检查 Controller 是否被扫描到...");
        try {
            Object controller = context.getBean("monitorUploadController");
            log.info("✅ [DEBUG] MonitorUploadController 已找到: {}", controller.getClass().getName());
        } catch (Exception e) {
            log.warn("❌ [DEBUG] MonitorUploadController 未找到: {}", e.getMessage());
        }
        
        try {
            Object fallback = context.getBean("debugFallbackController");
            log.info("✅ [DEBUG] DebugFallbackController 已找到: {}", fallback.getClass().getName());
        } catch (Exception e) {
            log.warn("❌ [DEBUG] DebugFallbackController 未找到: {}", e.getMessage());
        }
        
        // 列出所有 Controller Bean
        String[] controllerBeans = context.getBeanNamesForAnnotation(org.springframework.web.bind.annotation.RestController.class);
        log.info("📋 [DEBUG] 找到 {} 个 @RestController: {}", controllerBeans.length, java.util.Arrays.toString(controllerBeans));
    }
}

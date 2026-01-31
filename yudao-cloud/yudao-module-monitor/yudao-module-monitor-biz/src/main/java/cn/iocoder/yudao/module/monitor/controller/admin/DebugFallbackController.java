package cn.iocoder.yudao.module.monitor.controller.admin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 调试用兜底 Controller
 * 用于捕获所有未匹配的 /monitor/** 请求，帮助诊断路由问题
 * 
 * 注意：Spring 的路由匹配机制中，精确匹配优先于通配符匹配
 * 如果正常接口（如 /realtime/analyze）匹配成功，不会进入这个方法
 * 只有真正未匹配的请求才会进入这里
 */
@Slf4j
@RestController
@RequestMapping("/monitor")
public class DebugFallbackController {

    /**
     * 捕获所有未匹配的 /monitor/** 请求
     * 使用最低优先级，不会干扰正常的路由匹配
     */
    @RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH})
    public String fallback(@RequestHeader(value = "Content-Type", required = false) String contentType,
                          @PathVariable(required = false) String path,
                          @RequestParam(required = false) java.util.Map<String, String> allParams) {
        log.warn("🚨 [DEBUG] 收到未匹配的 /monitor/** 请求！");
        log.warn("  - Path Variable: {}", path);
        log.warn("  - Content-Type: {}", contentType);
        log.warn("  - Request Params: {}", allParams);
        
        // 返回错误信息，但至少我们知道它进来了
        throw new RuntimeException("No handler found for /monitor/" + (path != null ? path : "unknown"));
    }
}

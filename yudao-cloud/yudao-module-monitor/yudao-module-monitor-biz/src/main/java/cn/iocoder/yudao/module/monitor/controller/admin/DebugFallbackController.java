package cn.iocoder.yudao.module.monitor.controller.admin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 调试用兜底 Controller
 * 注意：已将其作用域限定为 /monitor/debug，避免与任何正常业务（尤其是 WebSocket）冲突
 */
@Slf4j
@RestController
@RequestMapping("/internal/debug") // 👈 关键修改点 1: 路径加了 /debug
public class DebugFallbackController {

    // 👈 关键修改点 2: 移除所有关于 WebSocket 的检查和异常抛出逻辑
    //@RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH})
    public String fallback(
            @RequestHeader(value = "Content-Type", required = false) String contentType,
            @PathVariable(required = false) String path,
            @RequestParam(required = false) java.util.Map<String, String> allParams,
            javax.servlet.http.HttpServletRequest request) {

        log.warn("🚨 [DEBUG] 收到未匹配的 /monitor/debug/** 请求！");
        log.warn("  - Path Variable: {}", path);
        log.warn("  - Content-Type: {}", contentType);
        log.warn("  - Request Params: {}", allParams);
        log.warn("  - Request URI: {}", request.getRequestURI());

        throw new RuntimeException("No handler found for debug path: /monitor/debug/" + (path != null ? path : "unknown"));
    }
}
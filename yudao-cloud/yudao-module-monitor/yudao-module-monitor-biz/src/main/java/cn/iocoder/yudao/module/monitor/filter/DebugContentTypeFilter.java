package cn.iocoder.yudao.module.monitor.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * 调试用 Filter：记录 /monitor/realtime/analyze 请求的 Content-Type
 * 用于诊断为什么接口返回 404
 */
@Slf4j
@Component
@Order(1) // 确保在其他 Filter 之前执行
public class DebugContentTypeFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            String contentType = httpRequest.getContentType();
            String uri = httpRequest.getRequestURI();
            String method = httpRequest.getMethod();
            
            if (uri != null && uri.contains("/monitor/realtime/analyze")) {
                log.warn("🔍 [DEBUG Filter] 收到 /monitor/realtime/analyze 请求");
                log.warn("  - Method: {}", method);
                log.warn("  - URI: {}", uri);
                log.warn("  - Content-Type: {}", contentType);
                log.warn("  - Content-Length: {}", httpRequest.getContentLength());
                log.warn("  - Query String: {}", httpRequest.getQueryString());
                
                // 检查是否是 multipart 请求
                if (contentType != null && contentType.contains("multipart")) {
                    log.warn("  ✅ 检测到 multipart/form-data 请求");
                } else {
                    log.warn("  ⚠️ 不是 multipart/form-data 请求！可能是问题所在");
                }
            }
        }
        chain.doFilter(request, response);
    }
}

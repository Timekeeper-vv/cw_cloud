package cn.iocoder.yudao.module.monitor.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class MonitorWebSocketConfig implements WebSocketConfigurer {

    private final MonitorWebSocketHandler webSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 同时兼容直连后端（/api/monitor/ws）和通过网关+前端代理的路径（/admin-api/api/monitor/ws）
        registry.addHandler(webSocketHandler, "/api/monitor/ws", "/admin-api/api/monitor/ws")
                .setAllowedOrigins("*");
    }
}

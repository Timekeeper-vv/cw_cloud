package cn.iocoder.yudao.module.monitor.config;

import cn.iocoder.yudao.module.monitor.service.MonitorResultHub;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class MonitorWebSocketHandler extends TextWebSocketHandler {

    private final MonitorResultHub resultHub;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Map<String, String> params = UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams()
                .toSingleValueMap();
        String jobId = params.get("jobId");
        if (jobId == null) {
            closeWithReason(session, "缺少 jobId 参数");
            return;
        }
        resultHub.register(jobId, session);
        log.info("WebSocket 已连接 jobId={}", jobId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        resultHub.unregister(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 目前只推送，不接受业务指令
    }

    private void closeWithReason(WebSocketSession session, String reason) {
        try {
            session.close(new CloseStatus(CloseStatus.PROTOCOL_ERROR.getCode(), reason));
        } catch (Exception ignored) {
        }
    }
}

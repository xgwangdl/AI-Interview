package com.guangge.Interview.language.stream;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 外语口语的实时通道（L2）。
 *
 * 单独一个 `/ws/language`：REST 那条路（L1）保持不动，同一场会话两种模式都能用。
 * 浏览器给 WebSocket 设不了 Authorization 头，所以 token 走查询串，在握手时验。
 */
@Configuration
@EnableWebSocket
public class LanguageStreamConfig implements WebSocketConfigurer {

    /** 音频帧默认上限只有 8KB，一帧 4096 个采样就顶满了；放宽到 64KB。 */
    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxBinaryMessageBufferSize(64 * 1024);
        container.setMaxTextMessageBufferSize(64 * 1024);
        return container;
    }

    private final LanguageStreamHandler handler;
    private final LanguageHandshakeInterceptor interceptor;

    public LanguageStreamConfig(LanguageStreamHandler handler, LanguageHandshakeInterceptor interceptor) {
        this.handler = handler;
        this.interceptor = interceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/language")
                .addInterceptors(interceptor)
                .setAllowedOriginPatterns("*");
    }
}

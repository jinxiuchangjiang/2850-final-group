package com.obg.config

import com.obg.service.GameRelayWebSocketHandler
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

/**
 * 注册 WebSocket 路由。
 * /ws/relay — 通用游戏消息中继，所有游戏共用同一个端点。
 *             游戏文件通过 SDK 连接这个端点，平台负责转发消息。
 */
@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val relayHandler: GameRelayWebSocketHandler
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry
            .addHandler(relayHandler, "/ws/relay")
            .setAllowedOrigins("*")   // 本地开发允许所有来源；生产改为具体域名
    }
}

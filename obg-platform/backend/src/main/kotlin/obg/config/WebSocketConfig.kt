package com.obg.config

import com.obg.service.GameRelayWebSocketHandler
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

/**
* Register the WebSocket route.
* /ws/relay - General game message relay, all games share the same endpoint.
* The game files connect to this endpoint through the SDK, and the platform is responsible for forwarding the messages. */
@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val relayHandler: GameRelayWebSocketHandler
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry
            .addHandler(relayHandler, "/ws/relay")
            .setAllowedOrigins("*")
    }
}

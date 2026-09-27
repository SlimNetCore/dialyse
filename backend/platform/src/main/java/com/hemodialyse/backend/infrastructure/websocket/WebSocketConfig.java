package com.hemodialyse.backend.infrastructure.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthentication authentication;

    public WebSocketConfig(WebSocketAuthentication authentication) {
        this.authentication = authentication;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Native WebSocket endpoint (preferred for Angular build compatibility)
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").addInterceptors(authentication);
        // SockJS fallback
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").addInterceptors(authentication).withSockJS();
    }

    /**
     * Authentifie la connexion et contrôle l'abonnement aux canaux de société.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authentication);
    }
}

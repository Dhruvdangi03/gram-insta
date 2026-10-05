package com.instaclone.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over SockJS, matching the build doc's messaging design. The endpoint path (/ws) picks up
 * the app's server.servlet.context-path automatically, same as every REST controller, so it's
 * actually served at /api/v1/ws.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final CorsProperties corsProperties;

    public WebSocketConfig(StompAuthChannelInterceptor stompAuthChannelInterceptor, CorsProperties corsProperties) {
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
        this.corsProperties = corsProperties;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Spring's SockJsService does its own origin allowlisting, entirely separate from Spring
        // Security's CORS filter (SecurityConfig's CorsConfigurationSource) — without this, every
        // cross-origin SockJS handshake from the frontend dev server 403s with "Origin header value
        // ... not allowed", regardless of the CORS config being otherwise correct.
        registry.addEndpoint("/ws")
                .setAllowedOrigins(corsProperties.allowedOrigins().toArray(new String[0]))
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}

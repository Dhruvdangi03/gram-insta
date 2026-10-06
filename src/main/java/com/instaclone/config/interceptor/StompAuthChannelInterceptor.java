package com.instaclone.config.interceptor;

import com.instaclone.common.util.SecurityUtils;
import java.security.Principal;
import java.time.Instant;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * Real auth for the WebSocket path — the HTTP handshake itself is permitAll (browsers can't
 * attach an Authorization header to a WebSocket upgrade request), so the actual gate is here: the
 * opening STOMP frame carries its own Authorization header, validated with the same JwtDecoder
 * bean the REST API uses. A missing/invalid token rejects the frame before a session exists.
 *
 * <p>Gated on message TYPE (SimpMessageType.CONNECT), not the literal StompCommand.CONNECT —
 * STOMP 1.1/1.2 also allows the alias command STOMP to open a session, and both report the same
 * message type. Checking only the CONNECT command let a client that opens with a STOMP frame skip
 * this block entirely.
 *
 * <p>The decoded token's expiry is stashed in the session attributes and re-checked on every
 * subsequent frame, not just at connect time — otherwise a session opened with a valid 15-minute
 * access token stays usable for as long as the socket itself stays open.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String EXPIRES_AT_ATTRIBUTE = "tokenExpiresAt";

    private final JwtDecoder jwtDecoder;

    public StompAuthChannelInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (accessor.getCommand() != null && accessor.getCommand().getMessageType() == SimpMessageType.CONNECT) {
            authenticateConnect(accessor);
        } else if (accessor.getSessionAttributes() != null) {
            Instant expiresAt = (Instant) accessor.getSessionAttributes().get(EXPIRES_AT_ATTRIBUTE);
            if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
                throw new BadCredentialsException("STOMP session's access token has expired");
            }
        }
        return message;
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new BadCredentialsException("Missing bearer token on STOMP connect");
        }
        try {
            Jwt jwt = jwtDecoder.decode(authHeader.substring(7));
            Long userId = SecurityUtils.currentUserId(jwt);
            Principal principal = () -> String.valueOf(userId);
            accessor.setUser(principal);
            if (accessor.getSessionAttributes() != null) {
                accessor.getSessionAttributes().put(EXPIRES_AT_ATTRIBUTE, jwt.getExpiresAt());
            }
        } catch (JwtException e) {
            throw new BadCredentialsException("Invalid bearer token on STOMP connect", e);
        }
    }
}

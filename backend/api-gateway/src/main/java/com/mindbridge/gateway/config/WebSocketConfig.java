package com.mindbridge.gateway.config;

import com.mindbridge.auth.jwt.JwtProvider;
import com.mindbridge.auth.service.AuthService;
import com.mindbridge.core.entity.User;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;

/**
 * WebSocket STOMP configuration with JWT authentication.
 * - Endpoint: /ws/chat (with SockJS fallback)
 * - App prefix: /app
 * - Broker: /topic, /queue
 * - JWT validated during STOMP CONNECT frame
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtProvider jwtProvider;
    private final AuthService authService;

    public WebSocketConfig(JwtProvider jwtProvider, AuthService authService) {
        this.jwtProvider = jwtProvider;
        this.authService = authService;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .addInterceptors(new JwtHandshakeInterceptor())
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new JwtStompInterceptor());
    }

    /**
     * HTTP handshake interceptor — extracts JWT from query param (?token=xxx)
     * and stores userId in WebSocket session attributes for later use.
     */
    private class JwtHandshakeInterceptor implements HandshakeInterceptor {
        @Override
        public boolean beforeHandshake(
                ServerHttpRequest request,
                ServerHttpResponse response,
                WebSocketHandler wsHandler,
                Map<String, Object> attributes) {

            if (request instanceof ServletServerHttpRequest servletRequest) {
                String token = servletRequest.getServletRequest().getParameter("token");
                if (token != null && jwtProvider.validateToken(token)) {
                    Long userId = jwtProvider.getUserIdFromToken(token);
                    attributes.put("userId", userId);
                    return true;
                }
            }
            // Reject unauthenticated connections
            return false;
        }

        @Override
        public void afterHandshake(
                ServerHttpRequest request,
                ServerHttpResponse response,
                WebSocketHandler wsHandler,
                Exception exception) {
            // no-op
        }
    }

    /**
     * STOMP CONNECT interceptor — validates JWT from the Authorization header
     * and sets Spring Security principal on the STOMP session.
     */
    private class JwtStompInterceptor implements ChannelInterceptor {
        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor =
                    MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

            if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                String authHeader = accessor.getFirstNativeHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    String token = authHeader.substring(7);
                    if (jwtProvider.validateToken(token)) {
                        Long userId = jwtProvider.getUserIdFromToken(token);
                        String role = jwtProvider.getRoleFromToken(token);
                        User user = authService.getUserById(userId);

                        if (user != null && user.getIsActive()) {
                            UsernamePasswordAuthenticationToken auth =
                                    new UsernamePasswordAuthenticationToken(
                                            user, null,
                                            List.of(new SimpleGrantedAuthority("ROLE_" + role))
                                    );
                            accessor.setUser(auth);
                        }
                    }
                }
            }
            return message;
        }
    }
}

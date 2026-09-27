package com.hemodialyse.backend.infrastructure.websocket;

import com.hemodialyse.backend.infrastructure.security.DirectionAccessGuard;
import com.hemodialyse.backend.infrastructure.security.JwtAuthenticationFactory;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.UUID;

/**
 * Authentification des connexions WebSocket : le cookie d'accès (le même que pour l'API) est lu à l'établissement de
 * la connexion, puis chaque abonnement à un canal de <b>société</b> ({@code /topic/societe/{id}/...}) est contrôlé :
 * seule la direction de cette société, revalidée en base, peut s'y abonner.
 * <p>
 * Les canaux de centre existants ({@code /topic/center/...}) ne changent pas de comportement.
 */
@Component
public class WebSocketAuthentication implements HandshakeInterceptor, ChannelInterceptor {

    static final String AUTH_ATTRIBUTE = "HEMO_WS_AUTH";
    static final String SOCIETE_TOPIC_PREFIX = "/topic/societe/";

    private final JwtAuthenticationFactory authentications;
    private final DirectionAccessGuard directionGuard;

    @Value("${app.auth.cookie.name:HEMO_AUTH}")
    private String authCookieName;

    public WebSocketAuthentication(JwtAuthenticationFactory authentications, DirectionAccessGuard directionGuard) {
        this.authentications = authentications;
        this.directionGuard = directionGuard;
    }

    // ───────────────────────────── Handshake HTTP ─────────────────────────────

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                                   Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servlet && servlet.getServletRequest().getCookies() != null) {
            for (Cookie cookie : servlet.getServletRequest().getCookies()) {
                if (authCookieName.equals(cookie.getName())) {
                    authentications.fromToken(cookie.getValue()).ifPresent(a -> attributes.put(AUTH_ATTRIBUTE, a));
                }
            }
        }
        return true; // la connexion reste permise : seuls les canaux de société exigent une authentification
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                               Exception exception) {
        // rien à faire
    }

    // ───────────────────────────── Canal STOMP entrant ─────────────────────────────

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        if (StompCommand.CONNECT.equals(accessor.getCommand()) && accessor.getSessionAttributes() != null
                && accessor.getSessionAttributes().get(AUTH_ATTRIBUTE) instanceof Authentication auth) {
            accessor.setUser(auth);
        }
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        }
        return message;
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(SOCIETE_TOPIC_PREFIX)) {
            return;
        }
        Object user = accessor.getUser();
        if (user instanceof Authentication auth && auth.getPrincipal() instanceof UserPrincipal principal
                && auth.getAuthorities().stream().anyMatch(a -> "ROLE_DIRECTION".equals(a.getAuthority()))
                && principal.getSocieteId() != null) {
            String requested = destination.substring(SOCIETE_TOPIC_PREFIX.length()).split("/", 2)[0];
            if (principal.getSocieteId().equals(requested)) {
                try {
                    directionGuard.verifyMember(UUID.fromString(principal.getId()), UUID.fromString(requested));
                    return;
                } catch (RuntimeException ignored) {
                    // refus ci-dessous
                }
            }
        }
        throw new MessagingException("Abonnement refusé : canal réservé à la direction de la société");
    }
}

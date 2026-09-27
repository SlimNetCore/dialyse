package com.hemodialyse.backend.infrastructure.websocket;

import com.hemodialyse.backend.infrastructure.security.DirectionAccessGuard;
import com.hemodialyse.backend.infrastructure.security.JwtAuthenticationFactory;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Abonnement aux canaux de société : réservé à la direction de la société, revalidée en base.
 */
class WebSocketAuthenticationTest {

    private static final UUID SOCIETE = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    private DirectionAccessGuard guard;
    private WebSocketAuthentication ws;

    private static Authentication auth(String role, UUID societe) {
        UserPrincipal principal = new UserPrincipal(USER.toString(), null, "u", "",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)), true, societe == null ? null : societe.toString());
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private static Message<byte[]> subscribe(String destination, Authentication user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setSubscriptionId("sub-0");
        accessor.setSessionId("s1");
        accessor.setUser(user);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @BeforeEach
    void setup() {
        guard = mock(DirectionAccessGuard.class);
        ws = new WebSocketAuthentication(mock(JwtAuthenticationFactory.class), guard);
    }

    @Test
    void the_direction_of_the_societe_can_subscribe() {
        assertDoesNotThrow(() -> ws.preSend(subscribe("/topic/societe/" + SOCIETE + "/dashboard", auth("DIRECTION", SOCIETE)), null));
        verify(guard).verifyMember(USER, SOCIETE);
    }

    @Test
    void an_anonymous_connection_is_refused() {
        assertThrows(MessagingException.class,
                () -> ws.preSend(subscribe("/topic/societe/" + SOCIETE + "/dashboard", null), null));
    }

    @Test
    void another_role_or_another_societe_is_refused() {
        assertThrows(MessagingException.class,
                () -> ws.preSend(subscribe("/topic/societe/" + SOCIETE + "/dashboard", auth("ADMIN", SOCIETE)), null));
        assertThrows(MessagingException.class,
                () -> ws.preSend(subscribe("/topic/societe/" + SOCIETE + "/dashboard", auth("DIRECTION", UUID.randomUUID())), null));
        assertThrows(MessagingException.class,
                () -> ws.preSend(subscribe("/topic/societe/" + SOCIETE + "/dashboard", auth("SUPERADMIN", null)), null));
    }

    @Test
    void a_deactivated_account_is_refused_even_with_a_valid_token() {
        doThrow(new AccessDeniedException("désactivé")).when(guard).verifyMember(any(), any());
        assertThrows(MessagingException.class,
                () -> ws.preSend(subscribe("/topic/societe/" + SOCIETE + "/dashboard", auth("DIRECTION", SOCIETE)), null));
    }

    @Test
    void other_topics_are_left_untouched() {
        Message<byte[]> centre = subscribe("/topic/center/" + UUID.randomUUID() + "/events", null);
        assertSame(centre, assertDoesNotThrow(() -> ws.preSend(centre, null)));
    }

    @Test
    void connect_attaches_the_authentication_read_at_the_handshake() {
        Authentication auth = auth("DIRECTION", SOCIETE);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(WebSocketAuthentication.AUTH_ATTRIBUTE, auth);
        accessor.setSessionAttributes(attributes);
        accessor.setLeaveMutable(true); // comme les messages entrants réels : l'intercepteur y attache l'utilisateur
        Message<byte[]> connect = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        ws.preSend(connect, null);

        assertSame(auth, StompHeaderAccessor.wrap(connect).getUser());
    }
}

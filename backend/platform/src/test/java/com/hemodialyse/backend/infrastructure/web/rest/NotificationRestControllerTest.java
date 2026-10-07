package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.notification.NotificationJournalPort;
import com.hemodialyse.backend.application.notification.NotificationJournalPort.Alerte;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.TenantScope;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.rest.NotificationRestController.LecturesRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Journal des alertes : le centre et les rôles viennent toujours de la session, la liste est paginée et bornée.
 */
class NotificationRestControllerTest {

    private final NotificationJournalPort journal = mock(NotificationJournalPort.class);
    private final CenterAccessGuard guard = mock(CenterAccessGuard.class);
    private final UUID centre = UUID.randomUUID();
    private final NotificationRestController controller = new NotificationRestController(journal, guard);

    NotificationRestControllerTest() {
        when(guard.requireCenter(any())).thenReturn(CenterId.of(centre));
        when(guard.currentScope()).thenReturn(new TenantScope(centre, "user-7", Set.of("ROLE_ADMIN", "ROLE_MEDECIN")));
    }

    @Test
    void should_list_the_alerts_of_the_session_center_for_the_roles_of_the_user_without_the_role_prefix() {
        UUID id = UUID.randomUUID();
        when(journal.lister(centre, "user-7", Set.of("ADMIN", "MEDECIN"), 0, 50)).thenReturn(PagedResult.of(
                List.of(new Alerte(id, "GENERATEUR_INDISPONIBLE", Map.of("generateur", "A-G1"),
                        Instant.parse("2026-10-07T02:30:00Z"), false)), 1, 0, 50));

        var reponse = controller.lister(null, 0, 50).getBody();

        assertEquals(1, reponse.total());
        var alerte = reponse.items().get(0);
        assertEquals(id, alerte.id());
        assertEquals(centre, alerte.centerId());
        assertEquals("2026-10-07T02:30:00Z", alerte.timestamp());
        assertEquals("A-G1", alerte.payload().get("generateur"));
    }

    @Test
    void should_bound_the_page_size() {
        when(journal.lister(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(PagedResult.of(List.of(), 0, 0, 100));

        controller.lister(null, -3, 5000);

        verify(journal).lister(centre, "user-7", Set.of("ADMIN", "MEDECIN"), 0, 100);
    }

    @Test
    void should_mark_alerts_as_read_for_the_current_user_only() {
        UUID id = UUID.randomUUID();

        assertEquals(204, controller.marquerLues(null, new LecturesRequest(List.of(id))).getStatusCode().value());
        assertEquals(204, controller.toutMarquerLu(null).getStatusCode().value());

        verify(journal).marquerLues(centre, "user-7", List.of(id));
        verify(journal).toutMarquerLu(centre, "user-7", Set.of("ADMIN", "MEDECIN"));
    }
}

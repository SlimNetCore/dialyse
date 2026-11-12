package com.hemodialyse.backend.infrastructure.acl.stock;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.JournalDesNotifications;
import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.NotificationsDesPeremptions.LotSignale;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ACL des notifications de péremption : identifiant déterministe (centre, jour), une seule alerte par centre et par jour, tolérance
 * à l'insertion concurrente de cet identifiant par une autre instance.
 */
class NotificationsDesPeremptionsLegacyTest {

    private static final UUID CENTRE = UUID.fromString("99997500-0000-0000-0000-00000000000a");
    private static final LocalDate JOUR = LocalDate.parse("2026-10-24");

    private final JournalDesNotifications journal = mock(JournalDesNotifications.class);
    private final NotificationService service = mock(NotificationService.class);
    private final NotificationsDesPeremptionsLegacy acl = new NotificationsDesPeremptionsLegacy(journal, service);

    private static LotSignale lot(int n, boolean perime) {
        return new LotSignale("L-" + n, "ART", "Article", JOUR.plusDays(n), new BigDecimal("2.50"), perime);
    }

    @Test
    void l_identifiant_depend_du_centre_et_du_jour_et_est_stable() {
        UUID id = NotificationsDesPeremptionsLegacy.identifiant(CENTRE, JOUR);
        assertEquals(id, NotificationsDesPeremptionsLegacy.identifiant(CENTRE, JOUR));
        assertNotEquals(id, NotificationsDesPeremptionsLegacy.identifiant(CENTRE, JOUR.plusDays(1)));
        assertNotEquals(id, NotificationsDesPeremptionsLegacy.identifiant(UUID.randomUUID(), JOUR));
    }

    @Test
    void la_premiere_alerte_du_jour_est_emise_avec_les_comptes_et_le_plus_proche() {
        UUID id = NotificationsDesPeremptionsLegacy.identifiant(CENTRE, JOUR);
        when(journal.existe(id)).thenReturn(false);
        assertTrue(acl.signaler(CENTRE, JOUR, List.of(lot(-1, true), lot(3, false))));
        verify(service).notifyLotsProchesPeremption(CENTRE, 2L, 1L, JOUR.minusDays(1), id);
    }

    @Test
    void une_alerte_deja_enregistree_ce_jour_n_est_pas_renvoyee() {
        when(journal.existe(NotificationsDesPeremptionsLegacy.identifiant(CENTRE, JOUR))).thenReturn(true);
        assertFalse(acl.signaler(CENTRE, JOUR, List.of(lot(3, false))));
        verify(service, never()).notifyLotsProchesPeremption(any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void une_insertion_concurrente_de_l_identifiant_par_une_autre_instance_est_traitee_comme_deja_envoyee() {
        UUID id = NotificationsDesPeremptionsLegacy.identifiant(CENTRE, JOUR);
        when(journal.existe(id)).thenReturn(false, true);
        doThrow(new DuplicateKeyException("notification_evenement_pkey")).when(service)
                .notifyLotsProchesPeremption(CENTRE, 1L, 0L, JOUR.plusDays(3), id);
        assertFalse(acl.signaler(CENTRE, JOUR, List.of(lot(3, false))), "pas d'erreur : l'autre instance a émis l'alerte");
    }

    @Test
    void une_violation_d_integrite_sans_alerte_enregistree_reste_une_erreur() {
        UUID id = NotificationsDesPeremptionsLegacy.identifiant(CENTRE, JOUR);
        when(journal.existe(id)).thenReturn(false);
        DuplicateKeyException cause = new DuplicateKeyException("autre contrainte");
        doThrow(cause).when(service).notifyLotsProchesPeremption(CENTRE, 1L, 0L, JOUR.plusDays(3), id);
        assertSame(cause, assertThrows(DuplicateKeyException.class, () -> acl.signaler(CENTRE, JOUR, List.of(lot(3, false)))));
    }
}

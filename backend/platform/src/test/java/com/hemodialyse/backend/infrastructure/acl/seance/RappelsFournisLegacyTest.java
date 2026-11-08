package com.hemodialyse.backend.infrastructure.acl.seance;

import com.hemodialyse.backend.application.notification.NotificationJournalPort;
import com.hemodialyse.backend.application.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
 * Fournisseur du langage publié {@code RappelsFournis} (SE4) : identifiant déterministe (centre, jour du centre), une seule alerte par
 * centre et par jour, tolérance à l'insertion concurrente de cet identifiant par une autre instance (KB-SEA-35).
 */
class RappelsFournisLegacyTest {

    private static final UUID CENTRE = UUID.fromString("99997500-0000-0000-0000-00000000000b");
    private static final LocalDate JOUR = LocalDate.parse("2026-11-08");

    private final NotificationJournalPort journal = mock(NotificationJournalPort.class);
    private final NotificationService service = mock(NotificationService.class);
    private final RappelsFournisLegacy fournisseur = new RappelsFournisLegacy(journal, service);

    @Test
    void l_identifiant_depend_du_centre_et_du_jour_et_est_stable() {
        UUID id = RappelsFournisLegacy.identifiant(CENTRE, JOUR);
        assertEquals(id, RappelsFournisLegacy.identifiant(CENTRE, JOUR));
        assertNotEquals(id, RappelsFournisLegacy.identifiant(CENTRE, JOUR.plusDays(1)));
        assertNotEquals(id, RappelsFournisLegacy.identifiant(UUID.randomUUID(), JOUR));
    }

    @Test
    void le_premier_rappel_du_jour_est_emis_avec_les_comptes_et_la_plus_ancienne() {
        UUID id = RappelsFournisLegacy.identifiant(CENTRE, JOUR);
        when(journal.existe(id)).thenReturn(false);

        assertTrue(fournisseur.rappeler(CENTRE, JOUR, 3, JOUR.minusDays(5)));

        verify(service).notifySeancesARegulariser(CENTRE, 3L, JOUR.minusDays(5), id);
    }

    @Test
    void un_rappel_deja_journalise_ce_jour_n_est_pas_renvoye() {
        when(journal.existe(RappelsFournisLegacy.identifiant(CENTRE, JOUR))).thenReturn(true);

        assertFalse(fournisseur.rappeler(CENTRE, JOUR, 3, JOUR.minusDays(5)));

        verify(service, never()).notifySeancesARegulariser(any(), anyLong(), any(), any());
    }

    @Test
    void une_insertion_concurrente_de_l_identifiant_par_une_autre_instance_est_traitee_comme_deja_emise() {
        UUID id = RappelsFournisLegacy.identifiant(CENTRE, JOUR);
        when(journal.existe(id)).thenReturn(false, true);
        doThrow(new DuplicateKeyException("notification_evenement_pkey")).when(service)
                .notifySeancesARegulariser(CENTRE, 1L, null, id);

        assertFalse(fournisseur.rappeler(CENTRE, JOUR, 1, null), "pas d'erreur : l'autre instance a émis le rappel");
    }

    @Test
    void une_violation_d_integrite_sans_rappel_enregistre_reste_une_erreur() {
        UUID id = RappelsFournisLegacy.identifiant(CENTRE, JOUR);
        when(journal.existe(id)).thenReturn(false);
        doThrow(new DataIntegrityViolationException("autre contrainte")).when(service).notifySeancesARegulariser(CENTRE, 1L, null, id);

        assertThrows(DataIntegrityViolationException.class, () -> fournisseur.rappeler(CENTRE, JOUR, 1, null));
    }
}

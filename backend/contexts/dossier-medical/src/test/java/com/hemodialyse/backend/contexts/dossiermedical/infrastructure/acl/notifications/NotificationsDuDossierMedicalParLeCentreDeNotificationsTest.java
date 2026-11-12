package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DemandeDeNotification;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotificationAEnvoyer;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationsDuDossierMedicalParLeCentreDeNotificationsTest {

    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final Instant MAINTENANT = Instant.parse("2026-11-12T08:00:00Z");

    private final List<DemandeDeNotification> demandes = new ArrayList<>();
    private final List<String> diffusions = new ArrayList<>();
    private final List<String> saisies = new ArrayList<>();
    private boolean dejaJournalisee;

    private final NotificationsDuDossierMedicalParLeCentreDeNotifications acl = new NotificationsDuDossierMedicalParLeCentreDeNotifications(
            demande -> {
                demandes.add(demande);
                if (dejaJournalisee) return Optional.empty();
                return Optional.of(new NotificationAEnvoyer(demande.centre(), demande.type(), demande.contenu(), null, MAINTENANT, null));
            },
            (centre, type, contenu, identifiant, emisLe) -> diffusions.add(type),
            (centre, patient, jour) -> saisies.add(centre + "|" + patient + "|" + jour));

    @Test
    void sans_transaction_la_prescription_changee_est_diffusee_aussitot() {
        acl.prescriptionChangee(CENTRE, PATIENT);

        assertEquals(List.of("PRESCRIPTION_CHANGED"), diffusions);
        assertTrue(demandes.getFirst().audience().roles().containsAll(List.of("INFIRMIER", "MEDECIN", "ADMIN")));
    }

    @Test
    void sans_transaction_l_observance_est_journalisee_puis_diffusee() {
        acl.observanceNonRespectee(CENTRE, PATIENT, "Retard", Map.of());

        assertEquals(List.of("OBSERVANCE_NON_RESPECTEE"), diffusions);
        assertTrue(demandes.getFirst().journalisee());
    }

    @Test
    void dans_une_transaction_le_signal_attend_le_commit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            acl.prescriptionChangee(CENTRE, PATIENT);
            acl.observanceNonRespectee(CENTRE, PATIENT, "Retard", Map.of());

            assertTrue(demandes.isEmpty(), "rien avant le commit");
            for (TransactionSynchronization synchronisation : new ArrayList<>(TransactionSynchronizationManager.getSynchronizations())) {
                synchronisation.afterCommit();
            }
            assertEquals(List.of("PRESCRIPTION_CHANGED", "OBSERVANCE_NON_RESPECTEE"), diffusions, "dans l'ordre des signaux");
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void une_transaction_annulee_ne_signale_rien() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            acl.prescriptionChangee(CENTRE, PATIENT);
        } finally {
            TransactionSynchronizationManager.clear();
        }

        assertTrue(demandes.isEmpty());
        assertTrue(diffusions.isEmpty());
    }

    @Test
    void rien_n_est_diffuse_si_le_journal_ignore_la_demande() {
        dejaJournalisee = true;

        acl.observanceNonRespectee(CENTRE, PATIENT, "Retard", Map.of());

        assertTrue(diffusions.isEmpty());
    }

    @Test
    void la_saisie_de_l_anemie_est_deleguee_au_port_de_saisie() {
        acl.administrationSaisie(CENTRE, PATIENT, LocalDate.of(2026, 11, 12));

        assertEquals(List.of(CENTRE + "|" + PATIENT + "|2026-11-12"), saisies);
        assertTrue(demandes.isEmpty());
    }
}

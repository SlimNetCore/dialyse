package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DemandeDeNotification;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotificationAEnvoyer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationsDeLaFicheParLeCentreDeNotificationsTest {

    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final Instant MAINTENANT = Instant.parse("2026-11-12T08:00:00Z");

    private final List<DemandeDeNotification> demandes = new ArrayList<>();
    private final List<String> diffusions = new ArrayList<>();
    private Optional<RuntimeException> panne = Optional.empty();
    private boolean dejaJournalisee;

    private final NotificationsDeLaFicheParLeCentreDeNotifications acl = new NotificationsDeLaFicheParLeCentreDeNotifications(
            demande -> {
                demandes.add(demande);
                if (dejaJournalisee) return Optional.empty();
                return Optional.of(new NotificationAEnvoyer(demande.centre(), demande.type(), demande.contenu(), null, MAINTENANT,
                        panne.orElse(null)));
            },
            (centre, type, contenu, identifiant, emisLe) -> diffusions.add(type + "|" + centre + "|" + identifiant + "|" + emisLe));

    @Test
    void une_fiche_creee_est_demandee_puis_diffusee_sans_identifiant() {
        acl.ficheCreee(CENTRE, PATIENT, "P1", "Benali", "Karim");

        assertEquals(1, demandes.size());
        assertEquals("PATIENT_CREATED", demandes.getFirst().type());
        assertEquals(List.of("PATIENT_CREATED|" + CENTRE + "|null|" + MAINTENANT), diffusions);
    }

    @Test
    void chaque_signal_emet_son_type() {
        UUID autre = UUID.randomUUID();
        acl.ficheModifiee(CENTRE, PATIENT, "P1", "Benali", "Karim");
        acl.pecValidee(CENTRE, autre, PATIENT, "Benali");
        acl.pecCloturee(CENTRE, autre, PATIENT, "Benali");
        acl.pecSupprimee(CENTRE, autre, PATIENT);
        acl.attestationCreee(CENTRE, autre, PATIENT);
        acl.attestationSupprimee(CENTRE, autre, null);

        assertEquals(List.of("PATIENT_UPDATED", "PEC_VALIDATED", "PEC_CLOSED", "PEC_DELETED", "ATTESTATION_CREATED", "ATTESTATION_DELETED"),
                demandes.stream().map(DemandeDeNotification::type).toList());
        assertEquals(6, diffusions.size());
    }

    @Test
    void rien_n_est_diffuse_si_le_journal_ignore_la_demande() {
        dejaJournalisee = true;

        acl.ficheCreee(CENTRE, PATIENT, "P1", "Benali", "Karim");

        assertTrue(diffusions.isEmpty());
    }

    @Test
    void une_panne_du_journal_n_empeche_pas_la_diffusion() {
        panne = Optional.of(new IllegalStateException("base inaccessible"));

        acl.ficheCreee(CENTRE, PATIENT, "P1", "Benali", "Karim");

        assertEquals(1, diffusions.size());
    }
}

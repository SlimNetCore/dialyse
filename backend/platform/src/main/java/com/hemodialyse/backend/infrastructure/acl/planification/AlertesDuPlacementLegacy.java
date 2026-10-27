package com.hemodialyse.backend.infrastructure.acl.planification;

import com.hemodialyse.backend.application.notification.NotificationJournalPort;
import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.AlertesDuPlacement;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Fournit au contexte Planification son port consommé {@link AlertesDuPlacement} (ACL vers le centre de notifications legacy). Les
 * alertes {@code ISOLEMENT_IMPOSSIBLE}, {@code RETOUR_SALLE_ORDINAIRE_PROPOSE} et {@code GENERATEUR_INDISPONIBLE} ont un identifiant
 * déterministe (UUID v3 de « type | centre | patient ou générateur | jour du centre ») : une seule alerte par jour du centre, même si
 * l'événement est rejoué ou si plusieurs instances du serveur travaillent ensemble (KB-PLN-60, 63). {@code PATIENT_REPLACE_ISOLEMENT}
 * est émise à chaque replacement, comme dans le legacy.
 */
@Component
public class AlertesDuPlacementLegacy implements AlertesDuPlacement {

    static final String ISOLEMENT_IMPOSSIBLE = "ISOLEMENT_IMPOSSIBLE";
    static final String RETOUR_PROPOSE = "RETOUR_SALLE_ORDINAIRE_PROPOSE";
    static final String GENERATEUR_INDISPONIBLE = "GENERATEUR_INDISPONIBLE";

    private final NotificationJournalPort journal;
    private final NotificationService notifications;

    public AlertesDuPlacementLegacy(NotificationJournalPort journal, NotificationService notifications) {
        this.journal = journal;
        this.notifications = notifications;
    }

    static UUID identifiant(String type, UUID centre, UUID sujet, LocalDate jour) {
        return UUID.nameUUIDFromBytes((type + "|" + centre + "|" + sujet + "|" + jour).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean patientReplaceEnIsolement(UUID centre, UUID patient, String nomDuPatient, String salle, LocalDate jour) {
        notifications.notifyPatientReplaceIsolement(centre, patient, nomDuPatient, salle);
        return true;
    }

    @Override
    public boolean isolementImpossible(UUID centre, UUID patient, String nomDuPatient, LocalDate jour) {
        UUID id = identifiant(ISOLEMENT_IMPOSSIBLE, centre, patient, jour);
        return emettre(id, () -> notifications.notifyIsolementImpossible(centre, patient, nomDuPatient, id));
    }

    @Override
    public boolean retourEnSalleOrdinairePropose(UUID centre, UUID patient, String nomDuPatient, String salleProposee, LocalDate jour) {
        UUID id = identifiant(RETOUR_PROPOSE, centre, patient, jour);
        return emettre(id, () -> notifications.notifyRetourEnSalleOrdinairePropose(centre, patient, nomDuPatient, salleProposee, id));
    }

    @Override
    public boolean generateurIndisponible(UUID centre, UUID generateur, String code, String statut, int nombreDePatients,
                                          List<String> patientsCites, LocalDate jour) {
        UUID id = identifiant(GENERATEUR_INDISPONIBLE, centre, generateur, jour);
        return emettre(id, () -> notifications.notifyGenerateurIndisponible(centre, code, statut, nombreDePatients, patientsCites, id));
    }

    private boolean emettre(UUID id, Runnable envoi) {
        if (journal.existe(id)) {
            return false;
        }
        try {
            envoi.run();
        } catch (DataIntegrityViolationException concurrente) {
            // une autre instance a inséré l'identifiant déterministe d'abord : l'alerte est déjà émise (une seule fois)
            if (journal.existe(id)) {
                return false;
            }
            throw concurrente;
        }
        return true;
    }
}

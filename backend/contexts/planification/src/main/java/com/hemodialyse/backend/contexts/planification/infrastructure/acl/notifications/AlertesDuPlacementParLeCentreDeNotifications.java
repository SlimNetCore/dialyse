package com.hemodialyse.backend.contexts.planification.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.JournalDesNotifications;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.AlertesDuPlacement;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * ACL sortante de la Planification vers {@code notifications.v1} (ADR 0155) pour son port {@link AlertesDuPlacement}. Les alertes
 * {@code ISOLEMENT_IMPOSSIBLE}, {@code RETOUR_SALLE_ORDINAIRE_PROPOSE} et {@code GENERATEUR_INDISPONIBLE} ont un identifiant déterministe
 * (UUID v3 de « type | centre | patient ou générateur | jour du centre ») : une seule alerte par jour du centre, même rejouée ou avec
 * plusieurs instances (KB-PLN-60, 63). {@code PATIENT_REPLACE_ISOLEMENT} est émise à chaque replacement.
 */
public class AlertesDuPlacementParLeCentreDeNotifications implements AlertesDuPlacement {

    static final String ISOLEMENT_IMPOSSIBLE = "ISOLEMENT_IMPOSSIBLE";
    static final String RETOUR_PROPOSE = "RETOUR_SALLE_ORDINAIRE_PROPOSE";
    static final String GENERATEUR_INDISPONIBLE = "GENERATEUR_INDISPONIBLE";

    private final EmissionDesNotificationsDeLaPlanification emission;

    public AlertesDuPlacementParLeCentreDeNotifications(NotifierEnDeuxTemps notifier, DiffusionEnTempsReel diffusion,
                                                        JournalDesNotifications journal) {
        this.emission = new EmissionDesNotificationsDeLaPlanification(notifier, diffusion, journal);
    }

    @Override
    public boolean patientReplaceEnIsolement(UUID centre, UUID patient, String nomDuPatient, String salle, LocalDate jour) {
        emission.emettre(CatalogueDesNotificationsDeLaPlanification.patientReplaceEnIsolement(centre, patient, nomDuPatient, salle), false);
        return true;
    }

    @Override
    public boolean isolementImpossible(UUID centre, UUID patient, String nomDuPatient, LocalDate jour) {
        UUID id = CatalogueDesNotificationsDeLaPlanification.identifiant(ISOLEMENT_IMPOSSIBLE, centre, patient, jour);
        return emission.emettreUneFois(CatalogueDesNotificationsDeLaPlanification.isolementImpossible(centre, patient, nomDuPatient, id));
    }

    @Override
    public boolean retourEnSalleOrdinairePropose(UUID centre, UUID patient, String nomDuPatient, String salleProposee, LocalDate jour) {
        UUID id = CatalogueDesNotificationsDeLaPlanification.identifiant(RETOUR_PROPOSE, centre, patient, jour);
        return emission.emettreUneFois(
                CatalogueDesNotificationsDeLaPlanification.retourEnSalleOrdinairePropose(centre, patient, nomDuPatient, salleProposee, id));
    }

    @Override
    public boolean generateurIndisponible(UUID centre, UUID generateur, String code, String statut, int nombreDePatients,
                                          List<String> patientsCites, LocalDate jour) {
        UUID id = CatalogueDesNotificationsDeLaPlanification.identifiant(GENERATEUR_INDISPONIBLE, centre, generateur, jour);
        return emission.emettreUneFois(
                CatalogueDesNotificationsDeLaPlanification.generateurIndisponible(centre, code, statut, nombreDePatients, patientsCites, id));
    }
}

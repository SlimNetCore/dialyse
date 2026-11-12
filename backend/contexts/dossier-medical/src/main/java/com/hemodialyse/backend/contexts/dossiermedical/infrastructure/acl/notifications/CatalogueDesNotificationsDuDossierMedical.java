package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.Audience;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DemandeDeNotification;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Catalogue des notifications émises par le Dossier médical (RG-NOT, ADR 0153) : codes de type conservés tels quels (le front en dépend).
 * <ul>
 *   <li>{@code PRESCRIPTION_CHANGED} : patientId ; audience INFIRMIER, MEDECIN, ADMIN ; diffusion seule (pas de journal) ;</li>
 *   <li>{@code OBSERVANCE_NON_RESPECTEE} : les détails (nature, traitement, période, quantités, unité), puis patientId, message ;
 *       audience MEDECIN ; alerte durable (journal), identifiant aléatoire.</li>
 * </ul>
 */
final class CatalogueDesNotificationsDuDossierMedical {

    private CatalogueDesNotificationsDuDossierMedical() {
    }

    static DemandeDeNotification prescriptionChangee(UUID centre, UUID patient) {
        var contenu = new HashMap<String, String>();
        contenu.put("patientId", patient.toString());
        contenu.put("targetRoles", "INFIRMIER,MEDECIN,ADMIN");
        return new DemandeDeNotification(centre, "PRESCRIPTION_CHANGED", contenu, Audience.desRoles("INFIRMIER", "MEDECIN", "ADMIN"), null,
                false);
    }

    static DemandeDeNotification observanceNonRespectee(UUID centre, UUID patient, String message, Map<String, String> details) {
        var contenu = new HashMap<String, String>(details);
        contenu.put("patientId", patient.toString());
        contenu.put("message", message);
        contenu.put("targetRoles", "MEDECIN");
        return new DemandeDeNotification(centre, "OBSERVANCE_NON_RESPECTEE", contenu, Audience.desRoles("MEDECIN"), null, true);
    }
}

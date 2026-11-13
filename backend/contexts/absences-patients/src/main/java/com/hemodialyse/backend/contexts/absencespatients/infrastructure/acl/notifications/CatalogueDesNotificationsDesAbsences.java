package com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.Audience;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DemandeDeNotification;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.UUID;

/**
 * Catalogue des notifications émises par les Absences de patients (RG-NOT, ADR 0156) : codes de type conservés tels quels (le front en
 * dépend), contenu composé dans l'ordre historique (table de hachage, donc même JSON), rôles ciblés repris dans {@code targetRoles}.
 * <ul>
 *   <li>{@code ABSENCES_A_QUALIFIER} : nbAQualifier, nbEnRetard ; ADMIN, SECRETAIRE, INFIRMIER, MEDECIN ; durable ; identifiant
 *       déterministe (une alerte par centre et par jour du centre) ;</li>
 *   <li>{@code SAISIE_INFIRMIER} (saisie {@code ABSENCE}) : saisie, patientId, patientNom, patientPrenom, auteur, date ; MEDECIN ;
 *       non conservée au journal.</li>
 * </ul>
 */
final class CatalogueDesNotificationsDesAbsences {

    static final String ABSENCES_A_QUALIFIER = "ABSENCES_A_QUALIFIER";
    static final String SAISIE_INFIRMIER = "SAISIE_INFIRMIER";

    private CatalogueDesNotificationsDesAbsences() {
    }

    /** UUID v3 de « type | centre | jour du centre ». */
    static UUID identifiantDesAbsencesAQualifier(UUID centre, LocalDate jour) {
        return UUID.nameUUIDFromBytes((ABSENCES_A_QUALIFIER + "|" + centre + "|" + jour).getBytes(StandardCharsets.UTF_8));
    }

    static DemandeDeNotification absencesAQualifier(UUID centre, long nbAQualifier, long nbEnRetard, UUID identifiant) {
        var contenu = new HashMap<String, String>();
        contenu.put("nbAQualifier", String.valueOf(nbAQualifier));
        contenu.put("nbEnRetard", String.valueOf(nbEnRetard));
        contenu.put("targetRoles", "ADMIN,SECRETAIRE,INFIRMIER,MEDECIN");
        return new DemandeDeNotification(centre, ABSENCES_A_QUALIFIER, contenu,
                Audience.desRoles("ADMIN", "SECRETAIRE", "INFIRMIER", "MEDECIN"), identifiant, true);
    }

    /** La saisie d'une absence est signalée au médecin du centre ; patient, nom, prénom, auteur et date peuvent être absents. */
    static DemandeDeNotification absenceSaisie(UUID centre, UUID patient, String nom, String prenom, String auteur, LocalDate jour) {
        var contenu = new HashMap<String, String>();
        contenu.put("saisie", "ABSENCE");
        contenu.put("patientId", patient == null ? "" : patient.toString());
        contenu.put("patientNom", ouVide(nom));
        contenu.put("patientPrenom", ouVide(prenom));
        contenu.put("auteur", ouVide(auteur));
        contenu.put("date", jour == null ? "" : jour.toString());
        contenu.put("targetRoles", "MEDECIN");
        return new DemandeDeNotification(centre, SAISIE_INFIRMIER, contenu, Audience.desRoles("MEDECIN"), null, false);
    }

    private static String ouVide(String valeur) {
        return valeur == null ? "" : valeur;
    }
}

package com.hemodialyse.backend.contexts.seance.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.Audience;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DemandeDeNotification;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Catalogue des notifications émises par la Séance (RG-NOT, ADR 0154) : codes de type conservés tels quels (le front en dépend), contenu
 * composé dans l'ordre historique (table de hachage, donc même JSON). Aucune n'est conservée au journal, sauf le rappel
 * {@code SEANCES_A_REGULARISER}.
 * <ul>
 *   <li>{@code SEANCE_CREATED}, {@code SEANCE_VALIDATED}, {@code SEANCE_PARAMEDICAL_SAVED} : seanceId, patientId, patientNom, patientPrenom,
 *       dateSeance ; audience INFIRMIER, SECRETAIRE ;</li>
 *   <li>{@code SEANCE_CONSOMMABLE_CHANGED} : seanceId ; INFIRMIER, SECRETAIRE ;</li>
 *   <li>{@code SEANCE_SUPPRIMEE} : seanceId, patientId, dateSeance ; ADMIN, INFIRMIER, SECRETAIRE ;</li>
 *   <li>{@code SEANCE_DEVERROUILLEE} : seanceId, patientNom, patientPrenom, dateSeance ; INFIRMIER ;</li>
 *   <li>{@code SAISIE_INFIRMIER} : saisie, patientId, patientNom, patientPrenom, auteur, date ; MEDECIN ;</li>
 *   <li>{@code SEANCES_A_REGULARISER} : nbSeances, plusAncienne ; ADMIN ; durable, identifiant déterministe (centre, jour du centre).</li>
 * </ul>
 */
final class CatalogueDesNotificationsDeLaSeance {

    private static final String ROLES = "targetRoles";
    private static final String INFIRMIER_SECRETAIRE = "INFIRMIER,SECRETAIRE";

    /** Types de saisie de l'infirmier émis par la Séance (le front traduit {@code NOTIFICATION.SAISIE.<type>}). */
    static final Set<String> SAISIES_DE_L_INFIRMIER = Set.of(
            "SEANCE_CREEE", "SEANCE_VALIDEE", "PARAMEDICAL", "CONSOMMABLE_AJOUT", "CONSOMMABLE_MODIF", "CONSOMMABLE_RETRAIT", "ANEMIE");

    private static final String TYPE_DU_RAPPEL = "SEANCES_A_REGULARISER";

    private CatalogueDesNotificationsDeLaSeance() {
    }

    static DemandeDeNotification seanceCreee(UUID centre, UUID seance, UUID patient, String nom, String prenom, LocalDate jour) {
        return seanceDuPatient("SEANCE_CREATED", centre, seance, patient, nom, prenom, jour);
    }

    static DemandeDeNotification seanceValidee(UUID centre, UUID seance, UUID patient, String nom, String prenom, LocalDate jour) {
        return seanceDuPatient("SEANCE_VALIDATED", centre, seance, patient, nom, prenom, jour);
    }

    static DemandeDeNotification voletParamedicalEnregistre(UUID centre, UUID seance, UUID patient, String nom, String prenom, LocalDate jour) {
        return seanceDuPatient("SEANCE_PARAMEDICAL_SAVED", centre, seance, patient, nom, prenom, jour);
    }

    static DemandeDeNotification consommablesModifies(UUID centre, UUID seance) {
        var contenu = new HashMap<String, String>();
        contenu.put("seanceId", seance.toString());
        contenu.put(ROLES, INFIRMIER_SECRETAIRE);
        return demande(centre, "SEANCE_CONSOMMABLE_CHANGED", contenu, Audience.desRoles("INFIRMIER", "SECRETAIRE"), null, false);
    }

    static DemandeDeNotification seanceSupprimee(UUID centre, UUID seance, UUID patient, LocalDate jour) {
        var contenu = new HashMap<String, String>();
        contenu.put("seanceId", seance.toString());
        contenu.put("patientId", patient.toString());
        contenu.put("dateSeance", texte(jour));
        contenu.put(ROLES, "ADMIN,INFIRMIER,SECRETAIRE");
        return demande(centre, "SEANCE_SUPPRIMEE", contenu, Audience.desRoles("ADMIN", "INFIRMIER", "SECRETAIRE"), null, false);
    }

    static DemandeDeNotification seanceDeverrouillee(UUID centre, UUID seance, String nom, String prenom, LocalDate jour) {
        var contenu = new HashMap<String, String>();
        contenu.put("seanceId", seance.toString());
        contenu.put("patientNom", ouVide(nom));
        contenu.put("patientPrenom", ouVide(prenom));
        contenu.put("dateSeance", texte(jour));
        contenu.put(ROLES, "INFIRMIER");
        return demande(centre, "SEANCE_DEVERROUILLEE", contenu, Audience.desRoles("INFIRMIER"), null, false);
    }

    /**
     * Toute saisie de l'infirmier est signalée au médecin du centre.
     *
     * @param type l'une de {@link #SAISIES_DE_L_INFIRMIER}, sinon {@link IllegalArgumentException}
     */
    static DemandeDeNotification saisieDeLInfirmier(UUID centre, String type, UUID patient, String nom, String prenom, String auteur,
                                                    LocalDate jour) {
        if (!SAISIES_DE_L_INFIRMIER.contains(type)) {
            throw new IllegalArgumentException("Type de saisie inconnu : " + type);
        }
        var contenu = new HashMap<String, String>();
        contenu.put("saisie", type);
        contenu.put("patientId", patient == null ? "" : patient.toString());
        contenu.put("patientNom", ouVide(nom));
        contenu.put("patientPrenom", ouVide(prenom));
        contenu.put("auteur", ouVide(auteur));
        contenu.put("date", texte(jour));
        contenu.put(ROLES, "MEDECIN");
        return demande(centre, "SAISIE_INFIRMIER", contenu, Audience.desRoles("MEDECIN"), null, false);
    }

    /** Identifiant déterministe du rappel : UUID v3 de « type | centre | jour du centre » (une seule alerte par centre et par jour, KB-SEA-35). */
    static UUID identifiantDuRappel(UUID centre, LocalDate jour) {
        return UUID.nameUUIDFromBytes((TYPE_DU_RAPPEL + "|" + centre + "|" + jour).getBytes(StandardCharsets.UTF_8));
    }

    static DemandeDeNotification seancesARegulariser(UUID centre, long nbSeances, LocalDate plusAncienne, UUID identifiant) {
        var contenu = new HashMap<String, String>();
        contenu.put("nbSeances", String.valueOf(nbSeances));
        contenu.put("plusAncienne", texte(plusAncienne));
        contenu.put(ROLES, "ADMIN");
        return demande(centre, TYPE_DU_RAPPEL, contenu, Audience.desRoles("ADMIN"), identifiant, true);
    }

    private static DemandeDeNotification seanceDuPatient(String type, UUID centre, UUID seance, UUID patient, String nom, String prenom,
                                                         LocalDate jour) {
        var contenu = new HashMap<String, String>();
        contenu.put("seanceId", seance.toString());
        contenu.put("patientId", patient.toString());
        contenu.put("patientNom", ouVide(nom));
        contenu.put("patientPrenom", ouVide(prenom));
        contenu.put("dateSeance", texte(jour));
        contenu.put(ROLES, INFIRMIER_SECRETAIRE);
        return demande(centre, type, contenu, Audience.desRoles("INFIRMIER", "SECRETAIRE"), null, false);
    }

    private static DemandeDeNotification demande(UUID centre, String type, Map<String, String> contenu, Audience audience, UUID identifiant,
                                                 boolean journalisee) {
        return new DemandeDeNotification(centre, type, contenu, audience, identifiant, journalisee);
    }

    private static String ouVide(String valeur) {
        return valeur == null ? "" : valeur;
    }

    private static String texte(LocalDate jour) {
        return jour == null ? "" : jour.toString();
    }
}

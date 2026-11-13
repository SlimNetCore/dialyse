package com.hemodialyse.backend.contexts.planification.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.Audience;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DemandeDeNotification;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Catalogue des notifications émises par la Planification (RG-NOT, ADR 0155) : codes de type conservés tels quels (le front en dépend),
 * contenu composé dans l'ordre historique (table de hachage, donc même JSON), rôles ciblés repris dans {@code targetRoles}. Toutes sont
 * conservées au journal.
 * <ul>
 *   <li>{@code INFIRMIER_SOUS_EFFECTIF} : nbCreneaux, premiereDate ; ADMIN, SECRETAIRE, MEDECIN ; id déterministe ;</li>
 *   <li>{@code INFIRMIER_SUREFFECTIF} : nbCreneaux, nbVacations, heures, premiereDate ; ADMIN ; id déterministe ;</li>
 *   <li>{@code INFIRMIER_ROULEMENT_MODIFIE} : infirmierId, runId ; ADMIN (+ compte visé) ; id déterministe ;</li>
 *   <li>{@code INFIRMIER_REMPLACEMENT_AFFECTE} : infirmierId, date, salleId, creneauId, runId ; ADMIN (+ compte visé) ; id déterministe ;</li>
 *   <li>{@code INFIRMIER_ABSENCE_ENREGISTREE} / {@code _DECLAREE} / {@code _RETIREE} : infirmier, debut, fin ; rôles variables ;</li>
 *   <li>{@code LIBERATION_PLACES_EN_ECHEC} : nbPatients, jour ; ADMIN ; id déterministe ;</li>
 *   <li>{@code OPTIMISATION_PROPOSITION} : runId, perimetre, motif, valeur ; ADMIN ;</li>
 *   <li>{@code REPLANIFICATION_NOCTURNE} : issue, propositions, echecs ; ADMIN ;</li>
 *   <li>{@code SEANCES_DEPLACEES} : perimetre, nbPatients, nbSeancesTemporaires ; MEDECIN, SECRETAIRE ;</li>
 *   <li>{@code GENERATEUR_INDISPONIBLE} : generateur, statut, nbPatients, patients ; ADMIN, SECRETAIRE ; id déterministe ;</li>
 *   <li>{@code ISOLEMENT_IMPOSSIBLE}, {@code RETOUR_SALLE_ORDINAIRE_PROPOSE} : patientId, patientNom[, salle] ; ADMIN, SECRETAIRE, MEDECIN ;
 *       id déterministe ; {@code PATIENT_REPLACE_ISOLEMENT} : idem sans identifiant imposé.</li>
 * </ul>
 */
final class CatalogueDesNotificationsDeLaPlanification {

    private static final String ROLES = "targetRoles";
    private static final String DESTINATAIRE = "targetUserId";
    private static final String ADMIN_SECRETAIRE_MEDECIN = "ADMIN,SECRETAIRE,MEDECIN";

    private CatalogueDesNotificationsDeLaPlanification() {
    }

    /** UUID v3 de « type | centre | jour du centre » (une seule alerte par type, centre et jour). */
    static UUID identifiant(String type, UUID centre, LocalDate jour) {
        return identifiant(type + "|" + centre + "|" + jour);
    }

    /** UUID v3 de « type | centre | sujet (patient ou générateur) | jour du centre ». */
    static UUID identifiant(String type, UUID centre, UUID sujet, LocalDate jour) {
        return identifiant(type + "|" + centre + "|" + sujet + "|" + jour);
    }

    static UUID identifiant(String cle) {
        return UUID.nameUUIDFromBytes(cle.getBytes(StandardCharsets.UTF_8));
    }

    static DemandeDeNotification sousEffectif(UUID centre, int nbCreneaux, LocalDate premiereDate, UUID id) {
        return demande(centre, "INFIRMIER_SOUS_EFFECTIF", contenu("nbCreneaux", String.valueOf(nbCreneaux),
                "premiereDate", premiereDate.toString(), ROLES, ADMIN_SECRETAIRE_MEDECIN), id);
    }

    static DemandeDeNotification sureffectif(UUID centre, int nbCreneaux, int nbVacations, int heures, LocalDate premiereDate, UUID id) {
        return demande(centre, "INFIRMIER_SUREFFECTIF", contenu("nbCreneaux", String.valueOf(nbCreneaux),
                "nbVacations", String.valueOf(nbVacations), "heures", String.valueOf(heures),
                "premiereDate", premiereDate.toString(), ROLES, "ADMIN"), id);
    }

    /** @param compte le compte lié à la fiche de l'infirmier, ou {@code null} : seule l'administration est alors prévenue */
    static DemandeDeNotification roulementModifie(UUID centre, UUID infirmier, UUID compte, UUID execution, UUID id) {
        var contenu = contenu("infirmierId", infirmier.toString(), "runId", execution.toString(), ROLES, "ADMIN");
        visantLeCompte(contenu, compte);
        return demande(centre, "INFIRMIER_ROULEMENT_MODIFIE", contenu, id);
    }

    static DemandeDeNotification remplacementAffecte(UUID centre, UUID infirmier, UUID compte, LocalDate date, UUID salle, UUID creneau,
                                                     UUID execution, UUID id) {
        var contenu = contenu("infirmierId", infirmier.toString(), "date", date.toString(), "salleId", salle.toString(),
                "creneauId", creneau.toString(), "runId", execution.toString(), ROLES, "ADMIN");
        visantLeCompte(contenu, compte);
        return demande(centre, "INFIRMIER_REMPLACEMENT_AFFECTE", contenu, id);
    }

    static DemandeDeNotification absenceEnregistree(UUID centre, String infirmier, LocalDate debut, LocalDate fin, boolean prevenirAdministrateur) {
        return demande(centre, "INFIRMIER_ABSENCE_ENREGISTREE",
                absence(infirmier, debut, fin, prevenirAdministrateur ? "ADMIN,MEDECIN" : "MEDECIN"), null);
    }

    static DemandeDeNotification absenceDeclaree(UUID centre, String infirmier, LocalDate debut, LocalDate fin) {
        return demande(centre, "INFIRMIER_ABSENCE_DECLAREE", absence(infirmier, debut, fin, ADMIN_SECRETAIRE_MEDECIN), null);
    }

    /** @param roles rôles prévenus, séparés par des virgules */
    static DemandeDeNotification absenceRetiree(UUID centre, String infirmier, LocalDate debut, LocalDate fin, String roles) {
        return demande(centre, "INFIRMIER_ABSENCE_RETIREE", absence(infirmier, debut, fin, roles), null);
    }

    static DemandeDeNotification liberationEnEchec(UUID centre, LocalDate jour, int nbPatients, UUID id) {
        return demande(centre, "LIBERATION_PLACES_EN_ECHEC",
                contenu("nbPatients", String.valueOf(nbPatients), "jour", jour.toString(), ROLES, "ADMIN"), id);
    }

    static DemandeDeNotification propositionASignaler(UUID centre, UUID execution, String perimetre, String motif, int valeur) {
        return demande(centre, "OPTIMISATION_PROPOSITION", contenu("runId", execution.toString(), "perimetre", perimetre, "motif", motif,
                "valeur", String.valueOf(valeur), ROLES, "ADMIN"), null);
    }

    static DemandeDeNotification bilanDeLaNuit(UUID centre, String issue, int propositions, int echecs) {
        return demande(centre, "REPLANIFICATION_NOCTURNE", contenu("issue", issue, "propositions", String.valueOf(propositions),
                "echecs", String.valueOf(echecs), ROLES, "ADMIN"), null);
    }

    static DemandeDeNotification seancesDeplacees(UUID centre, String perimetre, int nbPatients, int nbSeancesTemporaires) {
        return demande(centre, "SEANCES_DEPLACEES", contenu("perimetre", perimetre, "nbPatients", String.valueOf(nbPatients),
                "nbSeancesTemporaires", String.valueOf(nbSeancesTemporaires), ROLES, "MEDECIN,SECRETAIRE"), null);
    }

    /** Le total des patients est donné à part ; seuls les noms déjà choisis par l'appelant sont cités. */
    static DemandeDeNotification generateurIndisponible(UUID centre, String generateur, String statut, int nbPatients,
                                                        List<String> patientsCites, UUID id) {
        return demande(centre, "GENERATEUR_INDISPONIBLE", contenu("generateur", generateur, "statut", statut,
                "nbPatients", String.valueOf(nbPatients), "patients", String.join(", ", patientsCites),
                ROLES, "ADMIN,SECRETAIRE"), id);
    }

    static DemandeDeNotification isolementImpossible(UUID centre, UUID patient, String nomDuPatient, UUID id) {
        return demande(centre, "ISOLEMENT_IMPOSSIBLE",
                contenu("patientId", patient.toString(), "patientNom", nomDuPatient, ROLES, ADMIN_SECRETAIRE_MEDECIN), id);
    }

    static DemandeDeNotification patientReplaceEnIsolement(UUID centre, UUID patient, String nomDuPatient, String salle) {
        return demande(centre, "PATIENT_REPLACE_ISOLEMENT", contenu("patientId", patient.toString(), "patientNom", nomDuPatient,
                "salle", salle, ROLES, ADMIN_SECRETAIRE_MEDECIN), null);
    }

    static DemandeDeNotification retourEnSalleOrdinairePropose(UUID centre, UUID patient, String nomDuPatient, String salleProposee, UUID id) {
        return demande(centre, "RETOUR_SALLE_ORDINAIRE_PROPOSE", contenu("patientId", patient.toString(), "patientNom", nomDuPatient,
                "salle", salleProposee, ROLES, ADMIN_SECRETAIRE_MEDECIN), id);
    }

    /** Paires clé/valeur (dans l'ordre) vers une table de hachage : la forme historique du contenu, donc du JSON émis. */
    private static HashMap<String, String> contenu(String... paires) {
        var contenu = new HashMap<String, String>();
        for (int i = 0; i < paires.length; i += 2) {
            contenu.put(paires[i], paires[i + 1]);
        }
        return contenu;
    }

    private static HashMap<String, String> absence(String infirmier, LocalDate debut, LocalDate fin, String roles) {
        return contenu("infirmier", infirmier, "debut", debut.toString(), "fin", fin.toString(), ROLES, roles);
    }

    private static void visantLeCompte(HashMap<String, String> contenu, UUID compte) {
        if (compte != null) {
            contenu.put(DESTINATAIRE, compte.toString());
        }
    }

    /** Toutes les alertes de la Planification sont durables ; l'audience est déduite du contenu (rôles et compte visé), comme avant. */
    private static DemandeDeNotification demande(UUID centre, String type, HashMap<String, String> contenu, UUID id) {
        String roles = contenu.getOrDefault(ROLES, "");
        List<String> liste = roles.isBlank() ? List.of() : Arrays.stream(roles.split(",")).map(String::trim).toList();
        return new DemandeDeNotification(centre, type, contenu, new Audience(liste, contenu.get(DESTINATAIRE)), id, true);
    }
}

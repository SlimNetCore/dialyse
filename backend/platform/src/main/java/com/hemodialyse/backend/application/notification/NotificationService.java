package com.hemodialyse.backend.application.notification;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.Audience;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DemandeDeNotification;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.JournalDesNotifications;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TempsReelDeLaDirection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Publishes real-time events to WebSocket topics per center. Les alertes qui demandent une action
 * ({@link #DURABLES}) sont aussi enregistrées dans le journal du centre : une personne absente ou déconnectée au moment
 * de l'événement (replanification de nuit, panne d'un générateur) les retrouve à sa connexion.
 * <p>
 * Chaque méthode {@code notifyXxx} ne fait que composer le contenu et le rôle ciblé ; la demande
 * ({@link DemandeDeNotification}, langage publié notifications.v1) est construite en un seul endroit ({@link #demande}).
 */
@Service
public class NotificationService implements DiffusionEnTempsReel {

    /**
     * Types d'alerte conservés dans le journal ; les autres évènements (création, mise à jour) ne valent que sur le
     * moment.
     */
    static final Set<String> DURABLES = Set.of(
            "INFIRMIER_SOUS_EFFECTIF", "INFIRMIER_ABSENCE_DECLAREE", "OPTIMISATION_PROPOSITION",
            "ABSENCES_A_QUALIFIER", "SEANCES_A_REGULARISER", "PATIENT_REPLACE_ISOLEMENT", "ISOLEMENT_IMPOSSIBLE",
            "GENERATEUR_INDISPONIBLE", "SEANCES_DEPLACEES", "INFIRMIER_ABSENCE_ENREGISTREE",
            "INFIRMIER_SUREFFECTIF", "OBSERVANCE_NON_RESPECTEE", "REPLANIFICATION_NOCTURNE", "REFORME_PROPOSEE",
            "LOTS_PROCHES_PEREMPTION", "INFIRMIER_ABSENCE_RETIREE", "RETOUR_SALLE_ORDINAIRE_PROPOSE",
            "LIBERATION_PLACES_EN_ECHEC", "INFIRMIER_ROULEMENT_MODIFIE", "INFIRMIER_REMPLACEMENT_AFFECTE");

    /**
     * Types de saisie de l'infirmier notifiés au médecin du centre (clé de traduction {@code NOTIFICATION.SAISIE.<type>}).
     */
    public static final Set<String> SAISIES_INFIRMIER = Set.of(
            "SEANCE_CREEE", "SEANCE_VALIDEE", "PARAMEDICAL", "CONSOMMABLE_AJOUT", "CONSOMMABLE_MODIF",
            "CONSOMMABLE_RETRAIT", "ANEMIE", "ABSENCE");

    private static final String CLE_DES_ROLES = "targetRoles";
    private static final String CLE_DU_DESTINATAIRE = "targetUserId";

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final SimpMessagingTemplate messaging;
    private final TempsReelDeLaDirection directionRealtime;
    private final JournalDesNotifications journal;

    public NotificationService(SimpMessagingTemplate messaging, TempsReelDeLaDirection directionRealtime,
                               JournalDesNotifications journal) {
        this.messaging = messaging;
        this.directionRealtime = directionRealtime;
        this.journal = journal;
    }

    // ---------------------------------------------------------------- patients, prises en charge, attestations

    public void notifyPatientCreated(UUID centerId, UUID patientId, String patientCode, String patientNom, String patientPrenom) {
        send(centerId, "PATIENT_CREATED", identitePatient(patientId, patientCode, patientNom, patientPrenom));
    }

    public void notifyPatientUpdated(UUID centerId, UUID patientId, String patientCode, String patientNom, String patientPrenom) {
        send(centerId, "PATIENT_UPDATED", identitePatient(patientId, patientCode, patientNom, patientPrenom));
    }

    public void notifyPecValidated(UUID centerId, UUID pecId, UUID patientId, String patientNom) {
        send(centerId, "PEC_VALIDATED", Map.of("pecId", pecId.toString(), "patientId", patientId.toString(), "patientNom", patientNom));
    }

    public void notifyPecClosed(UUID centerId, UUID pecId, UUID patientId, String patientNom) {
        send(centerId, "PEC_CLOSED", Map.of("pecId", pecId.toString(), "patientId", patientId.toString(), "patientNom", patientNom));
    }

    public void notifyPecDeleted(UUID centerId, UUID pecId, UUID patientId) {
        send(centerId, "PEC_DELETED", avecPatientFacultatif("pecId", pecId, patientId));
    }

    public void notifyAttestationCreated(UUID centerId, UUID attestationId, UUID patientId) {
        send(centerId, "ATTESTATION_CREATED", avecPatientFacultatif("attestationId", attestationId, patientId));
    }

    public void notifyAttestationDeleted(UUID centerId, UUID attestationId, UUID patientId) {
        send(centerId, "ATTESTATION_DELETED", avecPatientFacultatif("attestationId", attestationId, patientId));
    }

    // ---------------------------------------------------------------- séances

    public void notifySeanceCreated(UUID centerId, UUID seanceId, UUID patientId,
                                    String patientNom, String patientPrenom, String dateSeance) {
        // le médecin est prévenu par l'évènement SAISIE_INFIRMIER (qui nomme l'auteur), pas par celui-ci
        send(centerId, "SEANCE_CREATED", seanceDuPatient(seanceId, patientId, patientNom, patientPrenom, dateSeance, "INFIRMIER,SECRETAIRE"));
    }

    public void notifySeanceValidated(UUID centerId, UUID seanceId, UUID patientId,
                                      String patientNom, String patientPrenom, String dateSeance) {
        send(centerId, "SEANCE_VALIDATED", seanceDuPatient(seanceId, patientId, patientNom, patientPrenom, dateSeance, "INFIRMIER,SECRETAIRE"));
    }

    public void notifySeanceMedicalSaved(UUID centerId, UUID seanceId, UUID patientId,
                                         String patientNom, String patientPrenom, String dateSeance) {
        send(centerId, "SEANCE_MEDICAL_SAVED",
                seanceDuPatient(seanceId, patientId, patientNom, patientPrenom, dateSeance, "INFIRMIER,MEDECIN,SECRETAIRE"));
    }

    public void notifySeanceParamedicalSaved(UUID centerId, UUID seanceId, UUID patientId,
                                             String patientNom, String patientPrenom, String dateSeance) {
        send(centerId, "SEANCE_PARAMEDICAL_SAVED",
                seanceDuPatient(seanceId, patientId, patientNom, patientPrenom, dateSeance, "INFIRMIER,SECRETAIRE"));
    }

    /** Notify that a seance consommable was added, updated or removed (stock changed). */
    public void notifySeanceUpdated(UUID centerId, UUID seanceId) {
        send(centerId, "SEANCE_CONSOMMABLE_CHANGED", contenu("seanceId", seanceId.toString(), CLE_DES_ROLES, "INFIRMIER,SECRETAIRE"));
    }

    /**
     * La prescription d'un patient a changé (création, modification, suppression) : le poste de l'infirmier qui a ce
     * patient à l'écran recalcule ce qu'il reste à administrer, sans rechargement. Évènement technique, absent de la cloche.
     */
    public void notifyPrescriptionChanged(UUID centerId, UUID patientId) {
        send(centerId, "PRESCRIPTION_CHANGED", contenu("patientId", patientId.toString(), CLE_DES_ROLES, "INFIRMIER,MEDECIN,ADMIN"));
    }

    /** Une séance a été supprimée par l'administrateur (listes, poste infirmier et planning se rafraîchissent). */
    public void notifySeanceSupprimee(UUID centerId, UUID seanceId, UUID patientId, LocalDate dateSeance) {
        send(centerId, "SEANCE_SUPPRIMEE", contenu("seanceId", seanceId.toString(), "patientId", patientId.toString(),
                "dateSeance", dateSeance == null ? "" : dateSeance.toString(), CLE_DES_ROLES, "ADMIN,INFIRMIER,SECRETAIRE"));
    }

    /**
     * Toute saisie de l'infirmier (séance créée ou validée, volet paramédical, consommables, administration EPO/fer,
     * absence de patient) est signalée au <b>médecin</b> du centre dans son centre de notifications.
     *
     * @param type    l'une de {@link #SAISIES_INFIRMIER}
     * @param patient identité du patient concerné (peut être vide pour une saisie sans patient)
     * @param auteur  nom d'utilisateur de l'auteur de la saisie
     * @param date    date de la séance ou de l'absence concernée (ISO), facultative
     */
    public void notifySaisieInfirmier(UUID centerId, String type, UUID patientId, String patientNom,
                                      String patientPrenom, String auteur, String date) {
        if (!SAISIES_INFIRMIER.contains(type)) {
            throw new IllegalArgumentException("Type de saisie inconnu : " + type);
        }
        send(centerId, "SAISIE_INFIRMIER", contenu("saisie", type, "patientId", patientId == null ? "" : patientId.toString(),
                "patientNom", ouVide(patientNom), "patientPrenom", ouVide(patientPrenom), "auteur", ouVide(auteur),
                "date", ouVide(date), CLE_DES_ROLES, "MEDECIN"));
    }

    /** L'administrateur a déverrouillé une séance oubliée : les infirmiers peuvent la valider. */
    public void notifySeanceDeverrouillee(UUID centerId, UUID seanceId, String patientNom, String patientPrenom,
                                          String dateSeance) {
        send(centerId, "SEANCE_DEVERROUILLEE", contenu("seanceId", seanceId.toString(), "patientNom", ouVide(patientNom),
                "patientPrenom", ouVide(patientPrenom), "dateSeance", ouVide(dateSeance), CLE_DES_ROLES, "INFIRMIER"));
    }

    /**
     * Des séances des jours précédents n'ont jamais été validées : l'administrateur doit les régulariser, sinon les
     * patients concernés sont comptés absents.
     */
    public void notifySeancesARegulariser(UUID centerId, long nbSeances, LocalDate plusAncienne) {
        notifySeancesARegulariser(centerId, nbSeances, plusAncienne, null);
    }

    /**
     * Variante à identifiant déterministe (un par centre et par jour du centre, calculé par l'appelant) : déjà enregistré, le rappel
     * n'est ni renvoyé ni dupliqué, même si deux instances du serveur le déclenchent ensemble (KB-SEA-35). Sans identifiant imposé,
     * comportement historique : un identifiant aléatoire à chaque appel.
     */
    public void notifySeancesARegulariser(UUID centerId, long nbSeances, LocalDate plusAncienne, UUID idImpose) {
        send(centerId, "SEANCES_A_REGULARISER", contenu("nbSeances", String.valueOf(nbSeances),
                "plusAncienne", plusAncienne == null ? "" : plusAncienne.toString(), CLE_DES_ROLES, "ADMIN"), idImpose);
    }

    // ---------------------------------------------------------------- observance

    public void notifyObservanceNonRespectee(UUID centerId, UUID patientId, String message) {
        notifyObservanceNonRespectee(centerId, patientId, message, Map.of());
    }

    /**
     * Retard constaté ou rappel d'échéance sur un traitement de l'anémie. {@code details} (nature de l'alerte, traitement,
     * période, quantité attendue et administrée, unité) permet à l'écran de l'expliquer dans la langue de l'utilisateur ;
     * {@code message} reste le texte complet de repli.
     */
    public void notifyObservanceNonRespectee(UUID centerId, UUID patientId, String message, Map<String, String> details) {
        var payload = new HashMap<String, String>(details);
        payload.put("patientId", patientId.toString());
        payload.put("message", message);
        payload.put(CLE_DES_ROLES, "MEDECIN");
        send(centerId, "OBSERVANCE_NON_RESPECTEE", payload);
    }

    // ---------------------------------------------------------------- présence des infirmiers

    /** Des créneaux sont en sous-effectif d'infirmiers dans les prochains jours (remplacements à organiser). */
    public void notifyPresenceSousEffectif(UUID centerId, int nbCreneaux, LocalDate premiereDate) {
        notifyPresenceSousEffectif(centerId, nbCreneaux, premiereDate, null);
    }

    /**
     * Variante à identifiant déterministe (un par centre et par jour du centre, calculé par l'appelant, KB-PLN-46) : déjà enregistrée,
     * l'alerte n'est ni renvoyée ni dupliquée.
     */
    public void notifyPresenceSousEffectif(UUID centerId, int nbCreneaux, LocalDate premiereDate, UUID idImpose) {
        send(centerId, "INFIRMIER_SOUS_EFFECTIF", contenu("nbCreneaux", String.valueOf(nbCreneaux),
                "premiereDate", premiereDate.toString(), CLE_DES_ROLES, "ADMIN,SECRETAIRE,MEDECIN"), idImpose);
    }

    /**
     * Des infirmiers sont prévus au-delà de l'effectif requis dans les prochains jours : personnel payé sans activité
     * utile, à réaffecter (optimisation du roulement).
     *
     * @param nbCreneaux  cases (salle, créneau, jour) concernées
     * @param nbVacations infirmiers en trop, cumulés sur ces cases
     * @param heures      heures de vacation correspondantes
     */
    public void notifyPresenceSureffectif(UUID centerId, int nbCreneaux, int nbVacations, int heures, LocalDate premiereDate) {
        notifyPresenceSureffectif(centerId, nbCreneaux, nbVacations, heures, premiereDate, null);
    }

    /** Variante à identifiant déterministe, voir {@link #notifyPresenceSousEffectif(UUID, int, LocalDate, UUID)}. */
    public void notifyPresenceSureffectif(UUID centerId, int nbCreneaux, int nbVacations, int heures,
                                          LocalDate premiereDate, UUID idImpose) {
        send(centerId, "INFIRMIER_SUREFFECTIF", contenu("nbCreneaux", String.valueOf(nbCreneaux),
                "nbVacations", String.valueOf(nbVacations), "heures", String.valueOf(heures),
                "premiereDate", premiereDate.toString(), CLE_DES_ROLES, "ADMIN"), idImpose);
    }

    /**
     * Le roulement d'un infirmier a été modifié par une proposition d'optimisation appliquée (slice P8) : l'infirmier concerné (par
     * son COMPTE) et l'administration du centre en sont prévenus, pas les autres infirmiers (revue P8, M2). Sans compte lié à la
     * fiche, seule l'administration est prévenue. Identifiant déterministe : une seule alerte par exécution et par infirmier.
     *
     * @param compteId le compte lié à la fiche de l'infirmier, ou {@code null}
     */
    public void notifyRoulementModifie(UUID centerId, UUID infirmierId, UUID compteId, UUID runId, UUID id) {
        var payload = contenu("infirmierId", infirmierId.toString(), "runId", runId.toString(), CLE_DES_ROLES, "ADMIN");
        visantLeCompte(payload, compteId);
        send(centerId, "INFIRMIER_ROULEMENT_MODIFIE", payload, id);
    }

    /**
     * Un infirmier a été affecté comme remplaçant par une proposition d'optimisation appliquée (slice P8) : lui seul (par son compte)
     * et l'administration du centre en sont prévenus (revue P8, M2). Identifiant déterministe : une seule alerte par exécution,
     * infirmier et case datée.
     *
     * @param compteId le compte lié à la fiche de l'infirmier, ou {@code null}
     */
    public void notifyRemplacementAffecte(UUID centerId, UUID infirmierId, UUID compteId, LocalDate date, UUID salleId,
                                          UUID creneauId, UUID runId, UUID id) {
        var payload = contenu("infirmierId", infirmierId.toString(), "date", date.toString(), "salleId", salleId.toString(),
                "creneauId", creneauId.toString(), "runId", runId.toString(), CLE_DES_ROLES, "ADMIN");
        visantLeCompte(payload, compteId);
        send(centerId, "INFIRMIER_REMPLACEMENT_AFFECTE", payload, id);
    }

    /**
     * L'absence d'un infirmier a été enregistrée : le médecin en est toujours prévenu (quels soins seront assurés par un
     * remplaçant), l'administrateur seulement quand la saisie ne vient pas de lui, pour organiser les remplacements.
     *
     * @param prevenirAdministrateur la saisie vient du secrétariat
     */
    public void notifyAbsenceInfirmierEnregistree(UUID centerId, String infirmier, LocalDate debut, LocalDate fin,
                                                  boolean prevenirAdministrateur) {
        send(centerId, "INFIRMIER_ABSENCE_ENREGISTREE",
                absenceInfirmier(infirmier, debut, fin, prevenirAdministrateur ? "ADMIN,MEDECIN" : "MEDECIN"));
    }

    /** Un infirmier a déclaré lui-même une absence (à prendre en compte dans les remplacements). */
    public void notifyAbsenceInfirmierDeclaree(UUID centerId, String infirmier, LocalDate debut, LocalDate fin) {
        send(centerId, "INFIRMIER_ABSENCE_DECLAREE", absenceInfirmier(infirmier, debut, fin, "ADMIN,SECRETAIRE,MEDECIN"));
    }

    /**
     * Une absence d'infirmier a été retirée (par l'administration, le secrétariat ou l'infirmier lui-même) : les écrans de
     * planification se rafraîchissent (slice P3, KB-PLN-34).
     *
     * @param targetRoles rôles prévenus, séparés par des virgules
     */
    public void notifyAbsenceInfirmierRetiree(UUID centerId, String infirmier, LocalDate debut, LocalDate fin, String targetRoles) {
        send(centerId, "INFIRMIER_ABSENCE_RETIREE", absenceInfirmier(infirmier, debut, fin, targetRoles));
    }

    // ---------------------------------------------------------------- planification, optimisation, isolement, générateurs

    /**
     * La libération nocturne des places (04:00) n'a pas pu libérer {@code nbPatients} patient(s) du centre ; les autres l'ont été et la
     * nuit suivante réessaie (P7). Identifiant déterministe (type, centre, jour du centre) calculé par l'appelant : une seule alerte
     * par centre et par jour.
     */
    public void notifyLiberationDesPlacesEnEchec(UUID centerId, LocalDate jour, int nbPatients, UUID idImpose) {
        send(centerId, "LIBERATION_PLACES_EN_ECHEC",
                contenu("nbPatients", String.valueOf(nbPatients), "jour", jour.toString(), CLE_DES_ROLES, "ADMIN"), idImpose);
    }

    /**
     * La replanification automatique nocturne a calculé une proposition qui mérite l'attention de l'administration
     * (sous-effectif à pourvoir, séances à déplacer pour maintenance, gain de ressources).
     */
    public void notifyOptimisationProposition(UUID centerId, UUID runId, String perimetre, String motif, int valeur) {
        send(centerId, "OPTIMISATION_PROPOSITION", contenu("runId", runId.toString(), "perimetre", perimetre, "motif", motif,
                "valeur", String.valueOf(valeur), CLE_DES_ROLES, "ADMIN"));
    }

    /**
     * Bilan de la replanification automatique nocturne d'un centre, envoyé <b>à chaque exécution</b> : l'administrateur
     * sait qu'elle a tourné, même quand elle n'a rien à proposer.
     *
     * @param issue        {@code PROPOSITIONS} (à examiner), {@code RIEN} (rien à changer), {@code ECHEC} (au moins un
     *                     calcul n'a pas abouti) ou {@code REPORTEE} (un calcul était déjà en cours dans le centre)
     * @param propositions propositions à examiner
     * @param echecs       calculs qui n'ont pas abouti
     */
    public void notifyReplanificationNocturne(UUID centerId, String issue, int propositions, int echecs) {
        send(centerId, "REPLANIFICATION_NOCTURNE", contenu("issue", issue, "propositions", String.valueOf(propositions),
                "echecs", String.valueOf(echecs), CLE_DES_ROLES, "ADMIN"));
    }

    /**
     * Une proposition d'optimisation appliquée a déplacé des patients ou des séances : le médecin et le secrétariat en
     * sont prévenus.
     */
    public void notifySeancesDeplacees(UUID centerId, String perimetre, int nbPatients, int nbSeancesTemporaires) {
        // émise dans la transaction d'application d'une proposition : le temps réel part après le commit (revue P8, m8)
        send(centerId, "SEANCES_DEPLACEES", contenu("perimetre", perimetre, "nbPatients", String.valueOf(nbPatients),
                "nbSeancesTemporaires", String.valueOf(nbSeancesTemporaires), CLE_DES_ROLES, "MEDECIN,SECRETAIRE"), null, true);
    }

    /**
     * Un générateur n'est plus disponible (maintenance, attente de pièce, panne, réforme) : les séances de ses patients
     * sont à déplacer. Les premiers noms sont cités, le total est donné à part.
     */
    public void notifyGenerateurIndisponible(UUID centerId, String generateur, String statut, List<String> patients) {
        send(centerId, "GENERATEUR_INDISPONIBLE", contenu("generateur", generateur, "statut", statut,
                "nbPatients", String.valueOf(patients.size()), "patients", String.join(", ", patients.stream().limit(5).toList()),
                CLE_DES_ROLES, "ADMIN,SECRETAIRE"));
    }

    /**
     * Variante à identifiant déterministe (générateur et jour du centre, calculé par l'appelant, P5 KB-PLN-63) : le total des patients
     * est donné à part, seuls les noms déjà choisis par l'appelant sont cités. Déjà enregistrée, l'alerte n'est ni renvoyée ni dupliquée.
     */
    public void notifyGenerateurIndisponible(UUID centerId, String generateur, String statut, int nbPatients,
                                             List<String> patientsCites, UUID idImpose) {
        send(centerId, "GENERATEUR_INDISPONIBLE", contenu("generateur", generateur, "statut", statut,
                "nbPatients", String.valueOf(nbPatients), "patients", String.join(", ", patientsCites),
                CLE_DES_ROLES, "ADMIN,SECRETAIRE"), idImpose);
    }

    /** Variante à identifiant déterministe (patient et jour du centre, P5 KB-PLN-60), voir {@link #notifyIsolementImpossible(UUID, UUID, String)}. */
    public void notifyIsolementImpossible(UUID centerId, UUID patientId, String patientNom, UUID idImpose) {
        send(centerId, "ISOLEMENT_IMPOSSIBLE", patientDansLIsolement(patientId, patientNom), idImpose);
    }

    /** Un patient devenu à risque infectieux n'a pu être replacé faute de place d'isolement : à replanifier. */
    public void notifyIsolementImpossible(UUID centerId, UUID patientId, String patientNom) {
        send(centerId, "ISOLEMENT_IMPOSSIBLE", patientDansLIsolement(patientId, patientNom));
    }

    /** Un patient devenu à risque infectieux a été replacé automatiquement en salle d'isolement. */
    public void notifyPatientReplaceIsolement(UUID centerId, UUID patientId, String patientNom, String salle) {
        send(centerId, "PATIENT_REPLACE_ISOLEMENT", contenu("patientId", patientId.toString(), "patientNom", patientNom,
                "salle", salle, CLE_DES_ROLES, "ADMIN,SECRETAIRE,MEDECIN"));
    }

    /**
     * Un patient redevenu sans risque infectieux pourrait quitter la salle d'isolement : le retour en salle ordinaire est proposé à
     * l'administration, jamais appliqué (P5 KB-PLN-61). Identifiant déterministe (patient et jour du centre).
     */
    public void notifyRetourEnSalleOrdinairePropose(UUID centerId, UUID patientId, String patientNom, String salleProposee,
                                                    UUID idImpose) {
        send(centerId, "RETOUR_SALLE_ORDINAIRE_PROPOSE", contenu("patientId", patientId.toString(), "patientNom", patientNom,
                "salle", salleProposee, CLE_DES_ROLES, "ADMIN,SECRETAIRE,MEDECIN"), idImpose);
    }

    // ---------------------------------------------------------------- GMAO, absences de patients, stock

    /**
     * Une intervention a constaté l'état « à réformer » d'un équipement (P2, KB-GMAO-57) : les détenteurs de l'habilitation
     * {@code GMAO_REFORME} du centre sont prévenus qu'une réforme est à examiner. Jamais de réforme automatique (RG-GMA-063).
     * L'identifiant de l'alerte se déduit de l'équipement et de l'instant de l'événement : un événement rejoué (livraison au
     * moins une fois) ne crée pas de doublon. Contrairement aux autres alertes, si l'enregistrement durable échoue l'exception
     * remonte à l'appelant (l'écouteur d'événement) sans rien envoyer en temps réel : la publication reste incomplète et
     * l'événement est rejoué.
     */
    public void notifyReformeProposee(UUID centerId, UUID equipementId, String code, Instant survenuLe) {
        var payload = contenu("equipementId", equipementId.toString(), "code", code, "statut", "A_REFORMER",
                "message", "Équipement " + code + " : état constaté « à réformer » à la clôture d'une intervention. "
                        + "Aucune réforme n'est décidée automatiquement : consultez l'aide à la décision de maintenance.",
                CLE_DES_ROLES, "GMAO_REFORME");
        UUID id = UUID.nameUUIDFromBytes(("REFORME_PROPOSEE|" + equipementId + "|" + survenuLe).getBytes(StandardCharsets.UTF_8));
        send(centerId, "REFORME_PROPOSEE", payload, id);
    }

    /** Des absences de patients attendent leur qualification (motif) ; certaines dépassent le délai. */
    public void notifyAbsencesAQualifier(UUID centerId, long nbAQualifier, long nbEnRetard) {
        notifyAbsencesAQualifier(centerId, nbAQualifier, nbEnRetard, null);
    }

    /**
     * Variante à identifiant déterministe (un par centre et par jour du centre, calculé par l'appelant) : déjà enregistrée, l'alerte
     * n'est ni renvoyée ni dupliquée, même si deux instances du serveur la déclenchent ensemble.
     */
    public void notifyAbsencesAQualifier(UUID centerId, long nbAQualifier, long nbEnRetard, UUID idImpose) {
        send(centerId, "ABSENCES_A_QUALIFIER", contenu("nbAQualifier", String.valueOf(nbAQualifier),
                "nbEnRetard", String.valueOf(nbEnRetard), CLE_DES_ROLES, "ADMIN,SECRETAIRE,INFIRMIER,MEDECIN"), idImpose);
    }

    /**
     * Des lots en stock périment dans les 30 jours ou sont périmés (contrôle quotidien du Stock, RG-STK-061) : le pharmacien et
     * l'administrateur du centre en sont prévenus par une alerte à identifiant déterministe (une par centre et par jour du centre).
     */
    public void notifyLotsProchesPeremption(UUID centerId, long nbLots, long nbPerimes, LocalDate plusProche, UUID idImpose) {
        send(centerId, "LOTS_PROCHES_PEREMPTION", contenu("nbLots", String.valueOf(nbLots), "nbPerimes", String.valueOf(nbPerimes),
                "plusProche", plusProche == null ? "" : plusProche.toString(), CLE_DES_ROLES, "ADMIN,PHARMACIEN"), idImpose);
    }

    // ---------------------------------------------------------------- composition du contenu

    /** Paires clé/valeur (dans l'ordre) vers une table de hachage : la forme historique du contenu, donc du JSON émis. */
    private static HashMap<String, String> contenu(String... paires) {
        var contenu = new HashMap<String, String>();
        for (int i = 0; i < paires.length; i += 2) {
            contenu.put(paires[i], paires[i + 1]);
        }
        return contenu;
    }

    private static String ouVide(String valeur) {
        return valeur == null ? "" : valeur;
    }

    private static Map<String, String> identitePatient(UUID patientId, String patientCode, String patientNom, String patientPrenom) {
        return Map.of("patientId", patientId.toString(), "patientCode", patientCode, "nom", patientNom, "prenom", patientPrenom);
    }

    private static Map<String, String> avecPatientFacultatif(String cle, UUID identifiant, UUID patientId) {
        var payload = contenu(cle, identifiant.toString());
        if (patientId != null) {
            payload.put("patientId", patientId.toString());
        }
        return payload;
    }

    private static Map<String, String> seanceDuPatient(UUID seanceId, UUID patientId, String patientNom, String patientPrenom,
                                                       String dateSeance, String roles) {
        return contenu("seanceId", seanceId.toString(), "patientId", patientId.toString(), "patientNom", ouVide(patientNom),
                "patientPrenom", ouVide(patientPrenom), "dateSeance", ouVide(dateSeance), CLE_DES_ROLES, roles);
    }

    private static Map<String, String> absenceInfirmier(String infirmier, LocalDate debut, LocalDate fin, String roles) {
        return contenu("infirmier", infirmier, "debut", debut.toString(), "fin", fin.toString(), CLE_DES_ROLES, roles);
    }

    private static Map<String, String> patientDansLIsolement(UUID patientId, String patientNom) {
        return contenu("patientId", patientId.toString(), "patientNom", patientNom, CLE_DES_ROLES, "ADMIN,SECRETAIRE,MEDECIN");
    }

    private static void visantLeCompte(Map<String, String> payload, UUID compteId) {
        if (compteId != null) payload.put(CLE_DU_DESTINATAIRE, compteId.toString());
    }

    // ---------------------------------------------------------------- émission

    private void send(UUID centerId, String eventType, Map<String, String> payload) {
        send(centerId, eventType, payload, null, false);
    }

    private void send(UUID centerId, String eventType, Map<String, String> payload, UUID idImpose) {
        send(centerId, eventType, payload, idImpose, false);
    }

    /**
     * {@code idImpose} : identifiant déterministe d'une alerte durable ; déjà enregistrée, elle n'est ni renvoyée ni dupliquée.
     * {@code apresLeCommit} : le temps réel part après le commit de la transaction en cours (jamais d'alerte fantôme si elle est
     * annulée), même sans identifiant imposé.
     */
    private void send(UUID centerId, String eventType, Map<String, String> payload, UUID idImpose, boolean apresLeCommit) {
        emettre(demande(centerId, eventType, payload, idImpose), apresLeCommit);
    }

    /**
     * Seul point de construction de la demande publiée (notifications.v1). Le contenu reste celui d'avant (rôles et compte visé inclus,
     * lus par le front) ; l'audience en est déduite, elle n'est pas une seconde source.
     */
    private static DemandeDeNotification demande(UUID centerId, String eventType, Map<String, String> payload, UUID idImpose) {
        String roles = payload.getOrDefault(CLE_DES_ROLES, "");
        List<String> listeDesRoles = roles.isBlank() ? List.of() : Arrays.stream(roles.split(",")).map(String::trim).toList();
        return new DemandeDeNotification(centerId, eventType, payload,
                new Audience(listeDesRoles, payload.get(CLE_DU_DESTINATAIRE)), idImpose, DURABLES.contains(eventType));
    }

    private void emettre(DemandeDeNotification demande, boolean apresLeCommit) {
        UUID centerId = demande.centre();
        String eventType = demande.type();
        Map<String, String> payload = demande.contenu();
        UUID idImpose = demande.identifiantDeterministe();
        if (idImpose != null && demande.journalisee() && journal.existe(idImpose)) {
            return;
        }
        Instant maintenant = Instant.now();
        UUID idJournalise = null;
        if (demande.journalisee()) {
            UUID id = idImpose != null ? idImpose : UUID.randomUUID();
            try {
                journal.enregistrer(id, centerId, eventType, payload, maintenant);
                idJournalise = id;
            } catch (RuntimeException e) {
                // identifiant imposé (alerte portée par un événement rejouable) : l'erreur remonte, rien n'est envoyé en
                // temps réel, et l'événement rejoué recommencera l'enregistrement puis l'envoi
                if (idImpose != null) throw e;
                // le temps réel reste prioritaire : une base indisponible n'empêche pas d'alerter les connectés
                log.warn("[NOTIFICATION] Alerte {} non enregistrée pour le centre {}", eventType, centerId, e);
            }
        }
        final UUID idDiffuse = idJournalise;
        Runnable tempsReel = () -> diffuser(centerId, eventType, payload, idDiffuse, maintenant);
        // Alerte à identifiant imposé (P3 à P5) émise dans une transaction : le temps réel part APRÈS le commit, pour qu'une
        // transaction annulée (409, 422, échec du commit) ne diffuse jamais d'alerte fantôme (revue P5, constat 4).
        if ((idImpose != null || apresLeCommit) && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    tempsReel.run();
                }
            });
        } else {
            tempsReel.run();
        }
    }

    /**
     * Transport temps réel (port {@link DiffusionEnTempsReel} du contexte Notifications, NOT1, ADR 0141) : le message STOMP du centre, puis
     * l'avertissement de la direction de la société. Les destinations et la forme du message sont celles d'avant le contexte.
     */
    @Override
    public void diffuser(UUID centerId, String eventType, Map<String, String> payload, UUID id, Instant maintenant) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", eventType);
        event.put("centerId", centerId.toString());
        // Alerte personnelle : le canal temps réel du centre ne porte que la cible ; le contenu (infirmier, date, salle, créneau)
        // se lit au journal, filtré par compte (revue P8, M2)
        event.put("payload", payload.containsKey(CLE_DU_DESTINATAIRE)
                ? Map.of(CLE_DES_ROLES, payload.getOrDefault(CLE_DES_ROLES, ""), CLE_DU_DESTINATAIRE, payload.get(CLE_DU_DESTINATAIRE))
                : payload);
        event.put("timestamp", maintenant.toString());
        if (id != null) event.put("id", id.toString());
        String destination = "/topic/center/" + centerId + "/events";
        messaging.convertAndSend(destination, (Object) event);
        // La direction de la société voit aussi ce changement, sans attendre le prochain balayage.
        directionRealtime.unCentreAChange(centerId);
    }
}

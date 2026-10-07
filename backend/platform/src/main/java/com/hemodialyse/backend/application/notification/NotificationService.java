package com.hemodialyse.backend.application.notification;

import com.hemodialyse.backend.application.direction.DirectionRealtimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes real-time events to WebSocket topics per center. Les alertes qui demandent une action
 * ({@link #DURABLES}) sont aussi enregistrées dans le journal du centre : une personne absente ou déconnectée au moment
 * de l'événement (replanification de nuit, panne d'un générateur) les retrouve à sa connexion.
 */
@Service
public class NotificationService {

    /**
     * Types d'alerte conservés dans le journal ; les autres évènements (création, mise à jour) ne valent que sur le
     * moment.
     */
    static final java.util.Set<String> DURABLES = java.util.Set.of(
            "INFIRMIER_SOUS_EFFECTIF", "INFIRMIER_ABSENCE_DECLAREE", "OPTIMISATION_PROPOSITION",
            "ABSENCES_A_QUALIFIER", "SEANCES_A_REGULARISER", "PATIENT_REPLACE_ISOLEMENT", "ISOLEMENT_IMPOSSIBLE",
            "GENERATEUR_INDISPONIBLE", "SEANCES_DEPLACEES", "INFIRMIER_ABSENCE_ENREGISTREE",
            "INFIRMIER_SUREFFECTIF", "OBSERVANCE_NON_RESPECTEE");

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final SimpMessagingTemplate messaging;
    private final DirectionRealtimeService directionRealtime;
    private final NotificationJournalPort journal;

    public NotificationService(SimpMessagingTemplate messaging, DirectionRealtimeService directionRealtime,
                               NotificationJournalPort journal) {
        this.messaging = messaging;
        this.directionRealtime = directionRealtime;
        this.journal = journal;
    }

    public void notifyPatientCreated(UUID centerId, UUID patientId, String patientCode, String patientNom, String patientPrenom) {
        send(centerId, "PATIENT_CREATED", Map.of(
                "patientId", patientId.toString(),
                "patientCode", patientCode,
                "nom", patientNom,
                "prenom", patientPrenom
        ));
    }

    public void notifyPatientUpdated(UUID centerId, UUID patientId, String patientCode, String patientNom, String patientPrenom) {
        send(centerId, "PATIENT_UPDATED", Map.of(
                "patientId", patientId.toString(),
                "patientCode", patientCode,
                "nom", patientNom,
                "prenom", patientPrenom
        ));
    }

    public void notifyPecValidated(UUID centerId, UUID pecId, UUID patientId, String patientNom) {
        send(centerId, "PEC_VALIDATED", Map.of(
                "pecId", pecId.toString(),
                "patientId", patientId.toString(),
                "patientNom", patientNom
        ));
    }

    public void notifyPecClosed(UUID centerId, UUID pecId, UUID patientId, String patientNom) {
        send(centerId, "PEC_CLOSED", Map.of(
                "pecId", pecId.toString(),
                "patientId", patientId.toString(),
                "patientNom", patientNom
        ));
    }

    public void notifyPecDeleted(UUID centerId, UUID pecId, UUID patientId) {
        var payload = new java.util.HashMap<String, String>();
        payload.put("pecId", pecId.toString());
        if (patientId != null) {
            payload.put("patientId", patientId.toString());
        }
        send(centerId, "PEC_DELETED", payload);
    }

    public void notifyAttestationCreated(UUID centerId, UUID attestationId, UUID patientId) {
        var payload = new java.util.HashMap<String, String>();
        payload.put("attestationId", attestationId.toString());
        if (patientId != null) {
            payload.put("patientId", patientId.toString());
        }
        send(centerId, "ATTESTATION_CREATED", payload);
    }

    public void notifyAttestationDeleted(UUID centerId, UUID attestationId, UUID patientId) {
        var payload = new java.util.HashMap<String, String>();
        payload.put("attestationId", attestationId.toString());
        if (patientId != null) {
            payload.put("patientId", patientId.toString());
        }
        send(centerId, "ATTESTATION_DELETED", payload);
    }

    /**
     * Types de saisie de l'infirmier notifiés au médecin du centre (clé de traduction {@code NOTIFICATION.SAISIE.<type>}).
     */
    public static final java.util.Set<String> SAISIES_INFIRMIER = java.util.Set.of(
            "SEANCE_CREEE", "SEANCE_VALIDEE", "PARAMEDICAL", "CONSOMMABLE_AJOUT", "CONSOMMABLE_MODIF",
            "CONSOMMABLE_RETRAIT", "ANEMIE", "ABSENCE");

    public void notifySeanceCreated(UUID centerId, UUID seanceId, UUID patientId,
                                    String patientNom, String patientPrenom, String dateSeance) {
        var payload = new java.util.HashMap<String, String>();
        payload.put("seanceId", seanceId.toString());
        payload.put("patientId", patientId.toString());
        payload.put("patientNom", patientNom == null ? "" : patientNom);
        payload.put("patientPrenom", patientPrenom == null ? "" : patientPrenom);
        payload.put("dateSeance", dateSeance == null ? "" : dateSeance);
        // le médecin est prévenu par l'évènement SAISIE_INFIRMIER (qui nomme l'auteur), pas par celui-ci
        payload.put("targetRoles", "INFIRMIER,SECRETAIRE");
        send(centerId, "SEANCE_CREATED", payload);
    }

    public void notifySeanceValidated(UUID centerId, UUID seanceId, UUID patientId,
                                      String patientNom, String patientPrenom, String dateSeance) {
        var payload = new java.util.HashMap<String, String>();
        payload.put("seanceId", seanceId.toString());
        payload.put("patientId", patientId.toString());
        payload.put("patientNom", patientNom == null ? "" : patientNom);
        payload.put("patientPrenom", patientPrenom == null ? "" : patientPrenom);
        payload.put("dateSeance", dateSeance == null ? "" : dateSeance);
        payload.put("targetRoles", "INFIRMIER,SECRETAIRE");
        send(centerId, "SEANCE_VALIDATED", payload);
    }

    public void notifySeanceMedicalSaved(UUID centerId, UUID seanceId, UUID patientId,
                                         String patientNom, String patientPrenom, String dateSeance) {
        var payload = new java.util.HashMap<String, String>();
        payload.put("seanceId", seanceId.toString());
        payload.put("patientId", patientId.toString());
        payload.put("patientNom", patientNom == null ? "" : patientNom);
        payload.put("patientPrenom", patientPrenom == null ? "" : patientPrenom);
        payload.put("dateSeance", dateSeance == null ? "" : dateSeance);
        payload.put("targetRoles", "INFIRMIER,MEDECIN,SECRETAIRE");
        send(centerId, "SEANCE_MEDICAL_SAVED", payload);
    }

    public void notifySeanceParamedicalSaved(UUID centerId, UUID seanceId, UUID patientId,
                                             String patientNom, String patientPrenom, String dateSeance) {
        var payload = new java.util.HashMap<String, String>();
        payload.put("seanceId", seanceId.toString());
        payload.put("patientId", patientId.toString());
        payload.put("patientNom", patientNom == null ? "" : patientNom);
        payload.put("patientPrenom", patientPrenom == null ? "" : patientPrenom);
        payload.put("dateSeance", dateSeance == null ? "" : dateSeance);
        payload.put("targetRoles", "INFIRMIER,SECRETAIRE");
        send(centerId, "SEANCE_PARAMEDICAL_SAVED", payload);
    }

    /**
     * Notify that a seance consommable was added, updated or removed (stock changed).
     */
    public void notifySeanceUpdated(UUID centerId, UUID seanceId) {
        var payload = new java.util.HashMap<String, String>();
        payload.put("seanceId", seanceId.toString());
        payload.put("targetRoles", "INFIRMIER,SECRETAIRE");
        send(centerId, "SEANCE_CONSOMMABLE_CHANGED", payload);
    }

    /**
     * La prescription d'un patient a changé (création, modification, suppression) : le poste de l'infirmier qui a ce
     * patient à l'écran recalcule ce qu'il reste à administrer, sans rechargement. Évènement technique, absent de la cloche.
     */
    public void notifyPrescriptionChanged(UUID centerId, UUID patientId) {
        var payload = new java.util.HashMap<String, String>();
        payload.put("patientId", patientId.toString());
        payload.put("targetRoles", "INFIRMIER,MEDECIN,ADMIN");
        send(centerId, "PRESCRIPTION_CHANGED", payload);
    }

    /**
     * Une séance a été supprimée par l'administrateur (listes, poste infirmier et planning se rafraîchissent).
     */
    public void notifySeanceSupprimee(UUID centerId, UUID seanceId, UUID patientId, java.time.LocalDate dateSeance) {
        var payload = new java.util.HashMap<String, String>();
        payload.put("seanceId", seanceId.toString());
        payload.put("patientId", patientId.toString());
        payload.put("dateSeance", dateSeance == null ? "" : dateSeance.toString());
        payload.put("targetRoles", "ADMIN,INFIRMIER,SECRETAIRE");
        send(centerId, "SEANCE_SUPPRIMEE", payload);
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
        var payload = new java.util.HashMap<String, String>();
        payload.put("saisie", type);
        payload.put("patientId", patientId == null ? "" : patientId.toString());
        payload.put("patientNom", patientNom == null ? "" : patientNom);
        payload.put("patientPrenom", patientPrenom == null ? "" : patientPrenom);
        payload.put("auteur", auteur == null ? "" : auteur);
        payload.put("date", date == null ? "" : date);
        payload.put("targetRoles", "MEDECIN");
        send(centerId, "SAISIE_INFIRMIER", payload);
    }

    public void notifyObservanceNonRespectee(UUID centerId, UUID patientId, String message) {
        notifyObservanceNonRespectee(centerId, patientId, message, Map.of());
    }

    /**
     * Retard constaté ou rappel d'échéance sur un traitement de l'anémie. {@code details} (nature de l'alerte, traitement,
     * période, quantité attendue et administrée, unité) permet à l'écran de l'expliquer dans la langue de l'utilisateur ;
     * {@code message} reste le texte complet de repli.
     */
    public void notifyObservanceNonRespectee(UUID centerId, UUID patientId, String message,
                                             Map<String, String> details) {
        var payload = new java.util.HashMap<String, String>(details);
        payload.put("patientId", patientId.toString());
        payload.put("message", message);
        payload.put("targetRoles", "MEDECIN");
        send(centerId, "OBSERVANCE_NON_RESPECTEE", payload);
    }

    /**
     * Des créneaux sont en sous-effectif d'infirmiers dans les prochains jours (remplacements à organiser).
     */
    public void notifyPresenceSousEffectif(UUID centerId, int nbCreneaux, java.time.LocalDate premiereDate) {
        var payload = new java.util.HashMap<String, String>();
        payload.put("nbCreneaux", String.valueOf(nbCreneaux));
        payload.put("premiereDate", premiereDate.toString());
        payload.put("targetRoles", "ADMIN,SECRETAIRE,MEDECIN");
        send(centerId, "INFIRMIER_SOUS_EFFECTIF", payload);
    }

    /**
     * Des infirmiers sont prévus au-delà de l'effectif requis dans les prochains jours : personnel payé sans activité
     * utile, à réaffecter (optimisation du roulement).
     *
     * @param nbCreneaux  cases (salle, créneau, jour) concernées
     * @param nbVacations infirmiers en trop, cumulés sur ces cases
     * @param heures      heures de vacation correspondantes
     */
    public void notifyPresenceSureffectif(UUID centerId, int nbCreneaux, int nbVacations, int heures,
                                          java.time.LocalDate premiereDate) {
        var payload = new java.util.HashMap<String, String>();
        payload.put("nbCreneaux", String.valueOf(nbCreneaux));
        payload.put("nbVacations", String.valueOf(nbVacations));
        payload.put("heures", String.valueOf(heures));
        payload.put("premiereDate", premiereDate.toString());
        payload.put("targetRoles", "ADMIN");
        send(centerId, "INFIRMIER_SUREFFECTIF", payload);
    }

    /**
     * La replanification automatique nocturne a calculé une proposition qui mérite l'attention de l'administration
     * (sous-effectif à pourvoir, séances à déplacer pour maintenance, gain de ressources).
     */
    public void notifyOptimisationProposition(UUID centerId, UUID runId, String perimetre, String motif, int valeur) {
        var payload = new java.util.HashMap<String, String>();
        payload.put("runId", runId.toString());
        payload.put("perimetre", perimetre);
        payload.put("motif", motif);
        payload.put("valeur", String.valueOf(valeur));
        payload.put("targetRoles", "ADMIN");
        send(centerId, "OPTIMISATION_PROPOSITION", payload);
    }

    /**
     * Un générateur n'est plus disponible (maintenance, attente de pièce, panne, réforme) : les séances de ses patients
     * sont à déplacer. Les premiers noms sont cités, le total est donné à part.
     */
    public void notifyGenerateurIndisponible(UUID centerId, String generateur, String statut, java.util.List<String> patients) {
        var payload = new java.util.HashMap<String, String>();
        payload.put("generateur", generateur);
        payload.put("statut", statut);
        payload.put("nbPatients", String.valueOf(patients.size()));
        payload.put("patients", String.join(", ", patients.stream().limit(5).toList()));
        payload.put("targetRoles", "ADMIN,SECRETAIRE");
        send(centerId, "GENERATEUR_INDISPONIBLE", payload);
    }

    /**
     * Une proposition d'optimisation appliquée a déplacé des patients ou des séances : le médecin et le secrétariat en
     * sont prévenus.
     */
    public void notifySeancesDeplacees(UUID centerId, String perimetre, int nbPatients, int nbSeancesTemporaires) {
        var payload = new java.util.HashMap<String, String>();
        payload.put("perimetre", perimetre);
        payload.put("nbPatients", String.valueOf(nbPatients));
        payload.put("nbSeancesTemporaires", String.valueOf(nbSeancesTemporaires));
        payload.put("targetRoles", "MEDECIN,SECRETAIRE");
        send(centerId, "SEANCES_DEPLACEES", payload);
    }

    /**
     * Le secrétariat a enregistré l'absence d'un infirmier : l'administrateur en est prévenu pour les remplacements.
     */
    public void notifyAbsenceInfirmierEnregistree(UUID centerId, String infirmier, java.time.LocalDate debut,
                                                  java.time.LocalDate fin) {
        var payload = new java.util.HashMap<String, String>();
        payload.put("infirmier", infirmier);
        payload.put("debut", debut.toString());
        payload.put("fin", fin.toString());
        payload.put("targetRoles", "ADMIN");
        send(centerId, "INFIRMIER_ABSENCE_ENREGISTREE", payload);
    }

    /**
     * Un infirmier a déclaré lui-même une absence (à prendre en compte dans les remplacements).
     */
    public void notifyAbsenceInfirmierDeclaree(UUID centerId, String infirmier, java.time.LocalDate debut,
                                               java.time.LocalDate fin) {
        var payload = new java.util.HashMap<String, String>();
        payload.put("infirmier", infirmier);
        payload.put("debut", debut.toString());
        payload.put("fin", fin.toString());
        payload.put("targetRoles", "ADMIN,SECRETAIRE");
        send(centerId, "INFIRMIER_ABSENCE_DECLAREE", payload);
    }

    /**
     * Des absences de patients attendent leur qualification (motif) ; certaines dépassent le délai.
     */
    public void notifyAbsencesAQualifier(UUID centerId, long nbAQualifier, long nbEnRetard) {
        var payload = new java.util.HashMap<String, String>();
        payload.put("nbAQualifier", String.valueOf(nbAQualifier));
        payload.put("nbEnRetard", String.valueOf(nbEnRetard));
        payload.put("targetRoles", "ADMIN,SECRETAIRE,INFIRMIER,MEDECIN");
        send(centerId, "ABSENCES_A_QUALIFIER", payload);
    }

    /**
     * Des séances des jours précédents n'ont jamais été validées : l'administrateur doit les régulariser, sinon les
     * patients concernés sont comptés absents.
     */
    public void notifySeancesARegulariser(UUID centerId, long nbSeances, java.time.LocalDate plusAncienne) {
        var payload = new java.util.HashMap<String, String>();
        payload.put("nbSeances", String.valueOf(nbSeances));
        payload.put("plusAncienne", plusAncienne == null ? "" : plusAncienne.toString());
        payload.put("targetRoles", "ADMIN");
        send(centerId, "SEANCES_A_REGULARISER", payload);
    }

    /**
     * L'administrateur a déverrouillé une séance oubliée : les infirmiers peuvent la valider.
     */
    public void notifySeanceDeverrouillee(UUID centerId, UUID seanceId, String patientNom, String patientPrenom,
                                          String dateSeance) {
        var payload = new java.util.HashMap<String, String>();
        payload.put("seanceId", seanceId.toString());
        payload.put("patientNom", patientNom == null ? "" : patientNom);
        payload.put("patientPrenom", patientPrenom == null ? "" : patientPrenom);
        payload.put("dateSeance", dateSeance == null ? "" : dateSeance);
        payload.put("targetRoles", "INFIRMIER");
        send(centerId, "SEANCE_DEVERROUILLEE", payload);
    }

    /**
     * Un patient devenu à risque infectieux a été replacé automatiquement en salle d'isolement.
     */
    public void notifyPatientReplaceIsolement(UUID centerId, UUID patientId, String patientNom, String salle) {
        var payload = new java.util.HashMap<String, String>();
        payload.put("patientId", patientId.toString());
        payload.put("patientNom", patientNom);
        payload.put("salle", salle);
        payload.put("targetRoles", "ADMIN,SECRETAIRE,MEDECIN");
        send(centerId, "PATIENT_REPLACE_ISOLEMENT", payload);
    }

    /**
     * Un patient devenu à risque infectieux n'a pu être replacé faute de place d'isolement : à replanifier.
     */
    public void notifyIsolementImpossible(UUID centerId, UUID patientId, String patientNom) {
        var payload = new java.util.HashMap<String, String>();
        payload.put("patientId", patientId.toString());
        payload.put("patientNom", patientNom);
        payload.put("targetRoles", "ADMIN,SECRETAIRE,MEDECIN");
        send(centerId, "ISOLEMENT_IMPOSSIBLE", payload);
    }

    private void send(UUID centerId, String eventType, Map<String, String> payload) {
        Map<String, Object> event = new java.util.HashMap<>();
        Instant maintenant = Instant.now();
        event.put("type", eventType);
        event.put("centerId", centerId.toString());
        event.put("payload", payload);
        event.put("timestamp", maintenant.toString());
        if (DURABLES.contains(eventType)) {
            UUID id = UUID.randomUUID();
            try {
                journal.enregistrer(id, centerId, eventType, payload, maintenant);
                event.put("id", id.toString());
            } catch (RuntimeException e) {
                // le temps réel reste prioritaire : une base indisponible n'empêche pas d'alerter les connectés
                log.warn("[NOTIFICATION] Alerte {} non enregistrée pour le centre {}", eventType, centerId, e);
            }
        }
        String destination = "/topic/center/" + centerId + "/events";
        messaging.convertAndSend(destination, (Object) event);
        // La direction de la société voit aussi ce changement, sans attendre le prochain balayage.
        directionRealtime.markDirtyForCentre(centerId);
    }
}



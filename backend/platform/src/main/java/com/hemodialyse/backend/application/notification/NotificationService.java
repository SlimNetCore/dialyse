package com.hemodialyse.backend.application.notification;

import com.hemodialyse.backend.application.direction.DirectionRealtimeService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes real-time events to WebSocket topics per center.
 */
@Service
public class NotificationService {

    private final SimpMessagingTemplate messaging;
    private final DirectionRealtimeService directionRealtime;

    public NotificationService(SimpMessagingTemplate messaging, DirectionRealtimeService directionRealtime) {
        this.messaging = messaging;
        this.directionRealtime = directionRealtime;
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

    public void notifySeanceCreated(UUID centerId, UUID seanceId, UUID patientId,
                                    String patientNom, String patientPrenom, String dateSeance) {
        var payload = new java.util.HashMap<String, String>();
        payload.put("seanceId", seanceId.toString());
        payload.put("patientId", patientId.toString());
        payload.put("patientNom", patientNom == null ? "" : patientNom);
        payload.put("patientPrenom", patientPrenom == null ? "" : patientPrenom);
        payload.put("dateSeance", dateSeance == null ? "" : dateSeance);
        payload.put("targetRoles", "INFIRMIER,MEDECIN");
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
        payload.put("targetRoles", "INFIRMIER,MEDECIN");
        send(centerId, "SEANCE_VALIDATED", payload);
    }

    public void notifySeanceParamedicalSaved(UUID centerId, UUID seanceId, UUID patientId,
                                             String patientNom, String patientPrenom, String dateSeance) {
        var payload = new java.util.HashMap<String, String>();
        payload.put("seanceId", seanceId.toString());
        payload.put("patientId", patientId.toString());
        payload.put("patientNom", patientNom == null ? "" : patientNom);
        payload.put("patientPrenom", patientPrenom == null ? "" : patientPrenom);
        payload.put("dateSeance", dateSeance == null ? "" : dateSeance);
        payload.put("targetRoles", "INFIRMIER,MEDECIN,SECRETAIRE");
        send(centerId, "SEANCE_PARAMEDICAL_SAVED", payload);
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

    /**
     * Notify that a seance consommable was added, updated or removed (stock changed).
     */
    public void notifySeanceUpdated(UUID centerId, UUID seanceId) {
        send(centerId, "SEANCE_CONSOMMABLE_CHANGED", Map.of("seanceId", seanceId.toString()));
    }

    public void notifyObservanceNonRespectee(UUID centerId, UUID patientId, String message) {
        var payload = new java.util.HashMap<String, String>();
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
        payload.put("targetRoles", "ADMIN,SECRETAIRE");
        send(centerId, "INFIRMIER_SOUS_EFFECTIF", payload);
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
        event.put("type", eventType);
        event.put("centerId", centerId.toString());
        event.put("payload", payload);
        event.put("timestamp", Instant.now().toString());
        String destination = "/topic/center/" + centerId + "/events";
        messaging.convertAndSend(destination, (Object) event);
        // La direction de la société voit aussi ce changement, sans attendre le prochain balayage.
        directionRealtime.markDirtyForCentre(centerId);
    }
}



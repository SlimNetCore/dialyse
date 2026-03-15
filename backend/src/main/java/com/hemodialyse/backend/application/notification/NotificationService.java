package com.hemodialyse.backend.application.notification;

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

    public NotificationService(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public void notifyPatientCreated(UUID centerId, String patientCode, String patientNom, String patientPrenom) {
        send(centerId, "PATIENT_CREATED", Map.of(
                "patientCode", patientCode,
                "nom", patientNom,
                "prenom", patientPrenom
        ));
    }

    public void notifyPecValidated(UUID centerId, UUID pecId, String patientNom) {
        send(centerId, "PEC_VALIDATED", Map.of(
                "pecId", pecId.toString(),
                "patientNom", patientNom
        ));
    }

    public void notifyPecClosed(UUID centerId, UUID pecId, String patientNom) {
        send(centerId, "PEC_CLOSED", Map.of(
                "pecId", pecId.toString(),
                "patientNom", patientNom
        ));
    }

    private void send(UUID centerId, String eventType, Map<String, String> payload) {
        Map<String, Object> event = new java.util.HashMap<>();
        event.put("type", eventType);
        event.put("centerId", centerId.toString());
        event.put("payload", payload);
        event.put("timestamp", Instant.now().toString());
        String destination = "/topic/center/" + centerId + "/events";
        messaging.convertAndSend(destination, (Object) event);
    }
}



package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DemandeDeNotification;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Catalogue des notifications du Dossier médical : codes de type, clés du contenu, audience, durabilité. */
class CatalogueDesNotificationsDuDossierMedicalTest {

    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @Test
    void une_prescription_changee_rafraichit_les_roles_cliniques_sans_journal() {
        DemandeDeNotification demande = CatalogueDesNotificationsDuDossierMedical.prescriptionChangee(CENTRE, PATIENT);

        assertEquals("PRESCRIPTION_CHANGED", demande.type());
        assertEquals(CENTRE, demande.centre());
        assertFalse(demande.journalisee());
        assertNull(demande.identifiantDeterministe());
        assertEquals(List.of("INFIRMIER", "MEDECIN", "ADMIN"), demande.audience().roles());
        assertNull(demande.audience().destinataire());
        assertEquals(Map.of("patientId", PATIENT.toString(), "targetRoles", "INFIRMIER,MEDECIN,ADMIN"), demande.contenu());
    }

    @Test
    void une_observance_non_respectee_est_une_alerte_durable_pour_les_medecins() {
        DemandeDeNotification demande = CatalogueDesNotificationsDuDossierMedical.observanceNonRespectee(CENTRE, PATIENT, "Retard EPO",
                Map.of("nature", "RETARD", "unite", "UI"));

        assertEquals("OBSERVANCE_NON_RESPECTEE", demande.type());
        assertTrue(demande.journalisee(), "retrouvée à la connexion");
        assertNull(demande.identifiantDeterministe());
        assertEquals(List.of("MEDECIN"), demande.audience().roles());
        assertNull(demande.audience().destinataire());
        assertEquals(Map.of("nature", "RETARD", "unite", "UI", "patientId", PATIENT.toString(), "message", "Retard EPO",
                "targetRoles", "MEDECIN"), demande.contenu());
    }

    @Test
    void les_cles_de_l_observance_sont_celles_des_details_puis_patient_message_et_roles() {
        DemandeDeNotification demande = CatalogueDesNotificationsDuDossierMedical.observanceNonRespectee(CENTRE, PATIENT, "m", Map.of());

        assertEquals(List.of("message", "patientId", "targetRoles"), demande.contenu().keySet().stream().sorted().toList());
    }

    @Test
    void les_cles_reservees_l_emportent_sur_les_details() {
        DemandeDeNotification demande = CatalogueDesNotificationsDuDossierMedical.observanceNonRespectee(CENTRE, PATIENT, "m",
                Map.of("message", "autre", "targetRoles", "ADMIN"));

        assertEquals("m", demande.contenu().get("message"));
        assertEquals("MEDECIN", demande.contenu().get("targetRoles"));
    }
}

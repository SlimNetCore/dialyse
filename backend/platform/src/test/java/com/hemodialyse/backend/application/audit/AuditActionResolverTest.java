package com.hemodialyse.backend.application.audit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Décision de traçage (écritures toujours, lectures seulement pour la liste explicite) et libellés produits.
 */
class AuditActionResolverTest {

    @Test
    void every_write_on_the_api_is_tracked_whatever_the_resource() {
        for (String method : new String[]{"POST", "PUT", "PATCH", "DELETE"}) {
            assertTrue(AuditActionResolver.isTracked(method, "/api/v1/whatever-future-endpoint/{id}"),
                    method + " doit toujours être tracé, même une ressource inconnue");
        }
    }

    @Test
    void an_ordinary_read_is_not_tracked() {
        assertFalse(AuditActionResolver.isTracked("GET", "/api/v1/patients/{id}"));
        assertFalse(AuditActionResolver.isTracked("GET", "/api/v1/patients"));
    }

    @Test
    void reading_the_medical_record_is_tracked_as_a_sensitive_read() {
        assertTrue(AuditActionResolver.isTracked("GET", "/api/v1/patients/{patientId}/dossier-medical"));
    }

    @Test
    void unresolved_routes_or_methods_are_never_tracked() {
        assertFalse(AuditActionResolver.isTracked("GET", null));
        assertFalse(AuditActionResolver.isTracked(null, "/api/v1/patients"));
    }

    @Test
    void resolves_a_readable_action_code_and_label_per_resource_and_verb() {
        var created = AuditActionResolver.resolve("POST", "/api/v1/patients");
        assertEquals("PATIENTS_CREATION", created.actionCode());
        assertEquals("patients", created.entityType());
        assertEquals("Création : patients", created.libelle());

        var updated = AuditActionResolver.resolve("PUT", "/api/v1/patients/{id}");
        assertEquals("PATIENTS_MODIFICATION", updated.actionCode());

        var deleted = AuditActionResolver.resolve("DELETE", "/api/v1/stock/lots/{id}");
        assertEquals("STOCK_SUPPRESSION", deleted.actionCode());

        var read = AuditActionResolver.resolve("GET", "/api/v1/patients/{patientId}/dossier-medical");
        assertEquals("DOSSIER_MEDICAL_CONSULTATION", read.actionCode());
        assertEquals("dossier-medical", read.entityType());
    }
}

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

    /** PL4 (KB-PL4-4, ADR 0139) : la consultation de statistiques par corps (POST) est une lecture, journalisee sous son action propre. */
    @Test
    void the_patient_statistics_searches_are_audited_as_a_consultation_not_as_a_creation() {
        for (String route : new String[]{"/api/v1/patients/stats/paramedical/search", "/api/v1/patients/stats/medical/search",
                "/api/v1/patients/stats/export/search"}) {
            assertTrue(AuditActionResolver.isTracked("POST", route));
            var action = AuditActionResolver.resolve("POST", route);
            assertEquals("STATISTIQUES_PATIENT_CONSULTATION", action.actionCode());
            assertEquals("patients", action.entityType());
            assertEquals("Statistiques du patient consultées", action.libelle());
        }
        assertEquals("PATIENTS_CREATION", AuditActionResolver.resolve("POST", "/api/v1/patients/{id}/stats/autre").actionCode());
    }

    /** DOC3, KB-DOC3-3 : le dépôt, l'activation et la réinitialisation d'un gabarit ont leur action ; la lecture du gabarit n'est pas tracée. */
    @Test
    void the_three_template_writes_have_their_own_action_and_the_template_read_is_not_tracked() {
        String racine = "/api/v1/documents/modeles/{modeleId}";
        assertEquals("GABARIT_TELEVERSE", AuditActionResolver.resolve("POST", racine + "/versions").actionCode());
        assertEquals("GABARIT_ACTIVE", AuditActionResolver.resolve("POST", racine + "/versions/{version}/activate").actionCode());
        assertEquals("GABARIT_REINITIALISE", AuditActionResolver.resolve("POST", racine + "/reset").actionCode());
        assertEquals("documents", AuditActionResolver.resolve("POST", racine + "/reset").entityType());
        assertFalse(AuditActionResolver.isTracked("GET", racine + "/source"));
        assertFalse(AuditActionResolver.isTracked("GET", racine + "/versions"));
        // les autres routes de /documents gardent l'action générique
        assertEquals("DOCUMENTS_CREATION", AuditActionResolver.resolve("POST", "/api/v1/documents/modeles").actionCode());
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

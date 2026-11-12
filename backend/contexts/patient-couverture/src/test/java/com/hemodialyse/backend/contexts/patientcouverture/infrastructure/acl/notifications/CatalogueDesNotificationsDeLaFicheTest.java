package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DemandeDeNotification;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Catalogue des notifications de Patient et couverture : codes de type, clés du contenu, audience (tous les utilisateurs du centre). */
class CatalogueDesNotificationsDeLaFicheTest {

    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID AUTRE = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    private static List<String> cles(DemandeDeNotification demande) {
        return new ArrayList<>(demande.contenu().keySet().stream().sorted().toList());
    }

    private static void ponctuelleAPourTous(DemandeDeNotification demande, String type) {
        assertEquals(type, demande.type());
        assertEquals(CENTRE, demande.centre());
        assertFalse(demande.journalisee(), "simple diffusion, jamais au journal");
        assertNull(demande.identifiantDeterministe());
        assertTrue(demande.audience().roles().isEmpty(), "tous les utilisateurs du centre");
        assertNull(demande.audience().destinataire());
        assertFalse(demande.contenu().containsKey("targetRoles"));
    }

    @Test
    void une_fiche_creee_diffuse_l_identite_du_patient() {
        var demande = CatalogueDesNotificationsDeLaFiche.ficheCreee(CENTRE, PATIENT, "P1", "Benali", "Karim");

        ponctuelleAPourTous(demande, "PATIENT_CREATED");
        assertEquals(List.of("nom", "patientCode", "patientId", "prenom"), cles(demande));
        assertEquals(PATIENT.toString(), demande.contenu().get("patientId"));
        assertEquals("P1", demande.contenu().get("patientCode"));
        assertEquals("Benali", demande.contenu().get("nom"));
        assertEquals("Karim", demande.contenu().get("prenom"));
    }

    @Test
    void une_fiche_modifiee_a_le_meme_contenu_sous_son_propre_type() {
        var demande = CatalogueDesNotificationsDeLaFiche.ficheModifiee(CENTRE, PATIENT, "P1", "Benali", "Karim");

        ponctuelleAPourTous(demande, "PATIENT_UPDATED");
        assertEquals(List.of("nom", "patientCode", "patientId", "prenom"), cles(demande));
    }

    @Test
    void une_identite_incomplete_fait_echouer_l_emission_comme_avant() {
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> CatalogueDesNotificationsDeLaFiche.ficheModifiee(CENTRE, PATIENT, null, "Benali", "Karim"));
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> CatalogueDesNotificationsDeLaFiche.pecValidee(CENTRE, AUTRE, PATIENT, null));
    }

    @Test
    void une_pec_validee_ou_cloturee_nomme_le_patient() {
        var validee = CatalogueDesNotificationsDeLaFiche.pecValidee(CENTRE, AUTRE, PATIENT, "Benali");
        var cloturee = CatalogueDesNotificationsDeLaFiche.pecCloturee(CENTRE, AUTRE, PATIENT, "Benali");

        ponctuelleAPourTous(validee, "PEC_VALIDATED");
        ponctuelleAPourTous(cloturee, "PEC_CLOSED");
        for (var demande : List.of(validee, cloturee)) {
            assertEquals(List.of("patientId", "patientNom", "pecId"), cles(demande));
            assertEquals(AUTRE.toString(), demande.contenu().get("pecId"));
            assertEquals(PATIENT.toString(), demande.contenu().get("patientId"));
            assertEquals("Benali", demande.contenu().get("patientNom"));
        }
    }

    @Test
    void une_pec_supprimee_ne_cite_le_patient_que_s_il_est_connu() {
        var avec = CatalogueDesNotificationsDeLaFiche.pecSupprimee(CENTRE, AUTRE, PATIENT);
        var sans = CatalogueDesNotificationsDeLaFiche.pecSupprimee(CENTRE, AUTRE, null);

        ponctuelleAPourTous(avec, "PEC_DELETED");
        assertEquals(List.of("patientId", "pecId"), cles(avec));
        assertEquals(List.of("pecId"), cles(sans));
    }

    @Test
    void une_attestation_creee_ou_supprimee_ne_cite_le_patient_que_s_il_est_connu() {
        var creee = CatalogueDesNotificationsDeLaFiche.attestationCreee(CENTRE, AUTRE, PATIENT);
        var supprimee = CatalogueDesNotificationsDeLaFiche.attestationSupprimee(CENTRE, AUTRE, null);

        ponctuelleAPourTous(creee, "ATTESTATION_CREATED");
        ponctuelleAPourTous(supprimee, "ATTESTATION_DELETED");
        assertEquals(List.of("attestationId", "patientId"), cles(creee));
        assertEquals(List.of("attestationId"), cles(supprimee));
        assertEquals(AUTRE.toString(), creee.contenu().get("attestationId"));
    }
}

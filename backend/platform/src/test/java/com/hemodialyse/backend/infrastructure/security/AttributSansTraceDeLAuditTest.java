package com.hemodialyse.backend.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le contexte dossier-medical ne peut pas dépendre de la plateforme : l'attribut « sans trace » du filtre d'audit est dupliqué dans
 * {@code SerologiesControleur}. Ce test fige l'égalité des deux chaînes (lecture par réflexion : aucune dépendance de code vers
 * les couches internes du contexte, la frontière ArchUnit reste respectée).
 */
class AttributSansTraceDeLAuditTest {

    @Test
    void le_controleur_des_serologies_et_le_filtre_d_audit_partagent_le_meme_attribut() throws Exception {
        Object duControleur = Class.forName("com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.SerologiesControleur")
                .getField("ATTRIBUT_SANS_TRACE").get(null);
        assertThat(duControleur).isEqualTo(AuditRequestFilter.ATTRIBUT_SANS_TRACE).isEqualTo("hemodialyse.audit.sansTrace");
    }
}

package com.hemodialyse.backend.characterization.planification.infirmiers;

import com.hemodialyse.backend.application.audit.AuditWriterService;
import com.hemodialyse.backend.characterization.planification.InfirmiersCharacterizationSupport;
import com.hemodialyse.backend.infrastructure.security.AuditRequestFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * Filet de caractérisation P2 : traçabilité des écritures du référentiel des infirmiers. La voie est le filtre générique
 * {@link AuditRequestFilter} (écritures {@code POST|PUT|DELETE} sur {@code /api/v1/**}, y compris refusées) ; les lectures ne
 * sont pas tracées. Observation : lignes de {@code audit_log} (action, type et identifiant d'entité, méthode, gabarit de route,
 * statut), sans horodatage ni utilisateur. Aucune diffusion ni notification n'est émise par ce référentiel (aucun service
 * de notification n'est appelé par les services caractérisés).
 */
class AuditDesInfirmiersCharacterizationIntegrationTest extends InfirmiersCharacterizationSupport {

    @Autowired
    private AuditRequestFilter auditRequestFilter;
    @Autowired
    private AuditWriterService auditWriter;

    @Override
    protected String theme() {
        return "p2-infirmiers/audit";
    }

    @BeforeEach
    void monterLeFiltreDAudit() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                .addFilters(roleScopeFilter, auditRequestFilter).build();
        auditWriter.flush();
        jdbc.update("DELETE FROM audit_log WHERE center_id IN (?, ?)", CENTRE_A, CENTRE_B);
    }

    @AfterEach
    void nettoyerLAudit() {
        jdbc.update("DELETE FROM audit_log WHERE center_id IN (?, ?)", CENTRE_A, CENTRE_B);
    }

    private void traces(String name) {
        auditWriter.flush();
        goldenSql(name, "SELECT action_code, entity_type, entity_id, http_method, route_template, status_code FROM audit_log "
                + "WHERE center_id IN (?, ?) ORDER BY route_template, http_method, status_code, entity_id", CENTRE_A, CENTRE_B);
    }

    @Test
    void chaque_ecriture_du_referentiel_est_tracee_avec_sa_route_et_son_statut_et_les_lectures_ne_le_sont_pas() throws Exception {
        UUID liee = fiche(CENTRE_A, "INF_LIEE", "M1", "Amrani", "Sara", null, "INFIRMIER", false, true,
                compte("C_1", "sara", "Sara Amrani", "INFIRMIER", true, CENTRE_A));
        UUID libre = fiche(CENTRE_A, "INF_LIBRE", "M2", "Benali", "Karim", true);
        UUID candidat = compte("C_2", "candidat", "Candidat", "INFIRMIER", true, CENTRE_A);
        doGet(INFIRMIERS, adminA());
        doGet(INFIRMIERS + "/comptes-liables", adminA());
        doPost(INFIRMIERS, json("{'matricule':'M3','nom':'Chikhi','qualification':'INFIRMIER','habiliteIsolement':false}"), adminA());
        doPut(INFIRMIERS + "/" + libre, json("{'matricule':'M2','nom':'Benali','qualification':'MAJOR','habiliteIsolement':true}"), adminA());
        doPost(INFIRMIERS + "/" + libre + "/desactiver", "{}", adminA());
        doPost(INFIRMIERS + "/" + libre + "/reactiver", "{}", adminA());
        doPost(INFIRMIERS + "/" + libre + "/compte/lier", json("{'userId':'" + candidat + "'}"), adminA());
        doDelete(INFIRMIERS + "/" + libre + "/compte", adminA());
        doPost(INFIRMIERS + "/" + libre + "/compte/creer", json("{'identifiant':'p2.nouveau'}"), adminA());
        doDelete(INFIRMIERS + "/" + liee + "/compte", adminA());
        traces("ecritures_tracees");
    }

    @Test
    void une_ecriture_refusee_est_tracee_avec_son_statut_d_echec() throws Exception {
        UUID fiche = fiche(CENTRE_A, "INF_1", "M1", "Amrani", "Sara", true);
        doPost(INFIRMIERS, json("{'matricule':'M1','nom':'Doublon','qualification':'INFIRMIER','habiliteIsolement':false}"), adminA());
        doPost(INFIRMIERS, json("{'matricule':'M9','nom':'Invalide','qualification':'CHIRURGIEN','habiliteIsolement':false}"), adminA());
        doPost(INFIRMIERS, json("{'matricule':'M9','nom':'Invalide','qualification':'INFIRMIER','habiliteIsolement':false}"), as(CENTRE_A, "SECRETAIRE"));
        doPost(INFIRMIERS + "/" + fiche + "/compte/creer", json("{'identifiant':'ab'}"), adminA());
        doPost(INFIRMIERS + "/" + UUID.nameUUIDFromBytes("inconnu".getBytes()) + "/desactiver", "{}", adminA());
        traces("ecritures_refusees_tracees");
        assertEquals(1, nombreDeFiches());
    }
}

package com.hemodialyse.backend.characterization.planification.roulement;

import com.hemodialyse.backend.characterization.planification.RoulementCharacterizationSupport;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Filet de caractérisation P3 : traçabilité (filtre générique {@code AuditRequestFilter} : écritures {@code POST|PUT|DELETE} sur
 * {@code /api/v1/**}, y compris refusées, lectures non tracées), notifications durables et diffusions temps réel des écritures du
 * roulement, des absences, de « Mon planning » et des remplacements. Observation : lignes de {@code audit_log} (action, type et
 * identifiant d'entité, méthode, gabarit de route, statut), {@code notification_evenement} et canal du courtier de messages.
 */
class AuditEtDiffusionsDuRoulementCharacterizationIntegrationTest extends RoulementCharacterizationSupport {

    private UUID amrani;
    private UUID benali;
    private UUID compteAmrani;
    private LocalDate lundi;

    @Override
    protected String theme() {
        return "p3-roulement/audit";
    }

    @BeforeEach
    void amorcer() {
        monterLAudit();
        decor();
        lundi = semaine(1, JourSemaine.LUNDI);
        semaine(1);
        compteAmrani = compte("COMPTE_AMRANI", "amrani", "Sara Amrani", "INFIRMIER", true, CENTRE_A);
        amrani = fiche(CENTRE_A, "INF_AMRANI", "AMRANI", "Amrani", "Sara", null, "INFIRMIER", true, true, compteAmrani);
        benali = inf(CENTRE_A, "INF_BENALI", "Benali", "Karim", false);
    }

    @Test
    void chaque_ecriture_du_roulement_des_absences_et_des_remplacements_est_tracee_avec_sa_route_et_son_statut_et_les_lectures_ne_le_sont_pas() throws Exception {
        patientsPlaces(CENTRE_A, "PAT", 2, salle1, matin, JourSemaine.LUNDI);
        doGet(INFIRMIERS + "/absences", adminA());
        doGet(INFIRMIERS + "/moi/planning?date=" + lundi, comme(compteAmrani, CENTRE_A, "INFIRMIER"));
        doGet(INFIRMIERS + "/presence/semaine?date=" + lundi, adminA());
        String aff = corps(doPost(INFIRMIERS + "/" + benali + "/affectations", json(corpsAffectation(salle1, matin, "LUNDI")), adminA())).path("id").asString();
        doPut(INFIRMIERS + "/" + benali + "/affectations/" + aff, json(corpsAffectation(salle1, matin, "LUNDI", "MARDI")), adminA());
        doDelete(INFIRMIERS + "/" + benali + "/affectations/" + aff, adminA());
        String abs = corps(doPost(INFIRMIERS + "/absences", json(corpsAbsence(benali, lundi, lundi, "CONGE", null)), adminA())).path("id").asString();
        doDelete(INFIRMIERS + "/absences/" + abs, adminA());
        String rempl = corps(doPost(INFIRMIERS + "/presence/remplacements", json(corpsRemplacement(lundi, salle1, matin, benali, null)), adminA())).path("id").asString();
        doDelete(INFIRMIERS + "/presence/remplacements/" + rempl, adminA());
        audit("ecritures_tracees");
    }

    @Test
    void les_ecritures_de_mon_planning_sont_tracees_aussi_avec_le_gabarit_de_leur_route() throws Exception {
        var sara = comme(compteAmrani, CENTRE_A, "INFIRMIER");
        String id = corps(doPost(INFIRMIERS + "/moi/absences", json(corpsMonAbsence(lundi, lundi, "CONGE", null)), sara)).path("id").asString();
        doDelete(INFIRMIERS + "/moi/absences/" + id, sara);
        audit("mon_planning_trace");
    }

    @Test
    void une_ecriture_refusee_est_tracee_avec_son_statut_d_echec_et_n_ecrit_rien() throws Exception {
        UUID inconnu = UUID.nameUUIDFromBytes("inconnu".getBytes());
        doPost(INFIRMIERS + "/" + benali + "/affectations", json(corpsAffectation(salleB, matin, "LUNDI")), adminA());
        doPost(INFIRMIERS + "/" + benali + "/affectations", "{}", adminA());
        doPost(INFIRMIERS + "/" + benali + "/affectations", json(corpsAffectation(salle1, matin, "LUNDI")), secretaire());
        doPost(INFIRMIERS + "/absences", json(corpsAbsence(inconnu, lundi, lundi, "CONGE", null)), adminA());
        doPost(INFIRMIERS + "/absences", json(corpsAbsence(benali, lundi, lundi, "VACANCES", null)), adminA());
        doDelete(INFIRMIERS + "/absences/" + inconnu, adminA());
        doPost(INFIRMIERS + "/presence/remplacements", json(corpsRemplacement(lundi, salle1, creneauB, benali, null)), adminA());
        doDelete(INFIRMIERS + "/presence/remplacements/" + inconnu, adminA());
        doPost(INFIRMIERS + "/moi/absences", json(corpsMonAbsence(lundi, lundi, "CONGE", null)), comme(UUID.nameUUIDFromBytes("x".getBytes()), CENTRE_A, "INFIRMIER"));
        audit("ecritures_refusees_tracees");
        assertEquals(0, nombreDe("infirmier_affectation") + nombreDe("infirmier_absence") + nombreDe("infirmier_remplacement"));
    }

    @Test
    void le_roulement_et_les_remplacements_ne_notifient_ni_ne_diffusent_rien_seules_les_absences_le_font() throws Exception {
        String aff = corps(doPost(INFIRMIERS + "/" + benali + "/affectations", json(corpsAffectation(salle1, matin, "LUNDI")), adminA())).path("id").asString();
        doPut(INFIRMIERS + "/" + benali + "/affectations/" + aff, json(corpsAffectation(salle1, matin, "MARDI")), adminA());
        doDelete(INFIRMIERS + "/" + benali + "/affectations/" + aff, adminA());
        String rempl = corps(doPost(INFIRMIERS + "/presence/remplacements", json(corpsRemplacement(lundi, salle1, matin, benali, null)), adminA())).path("id").asString();
        doDelete(INFIRMIERS + "/presence/remplacements/" + rempl, adminA());
        String abs = corps(doPost(INFIRMIERS + "/absences", json(corpsAbsence(benali, lundi, lundi, "CONGE", null)), adminA())).path("id").asString();
        doDelete(INFIRMIERS + "/absences/" + abs, adminA());
        notifications("notifications_du_scenario");
        diffusionsCapturees("diffusions_du_scenario");
    }
}

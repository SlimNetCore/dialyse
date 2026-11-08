package com.hemodialyse.backend.characterization.seance.se7f;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SE7f : dernier lecteur vivant de {@code ResultatAnalyseRepositoryPort} (bc-seance) : {@code KdigoGreffeQueryService.dernierCreatinine}
 * ({@code GET /api/v1/patients/{id}/greffe/kdigo}) lit {@code resultats_analyses} du patient AU CENTRE, sans période, et retient la
 * créatinine non nulle de la date de prélèvement la plus récente. DM7 ({@code dossiermedical/dm7/lecteurs/fonction_renale}) fige les
 * cas d'un seul centre ; ici le cloisonnement (analyse plus récente du même patient dans un autre centre) et l'égalité de dates.
 * Le golden ne fige que le STADE (créatinine 1.0 : G2 ; 9.0 : G5, pour un homme né en 1950, stades stables sur des décennies), jamais la valeur
 * d'eGFR qui dépend de l'âge calculé au jour de l'appel.
 */
class LaDerniereCreatinineLueAuCentreCharacterizationIntegrationTest extends Se7fCharacterizationSupport {

    @Override
    protected String dossier() {
        return "analyses";
    }

    private UUID patientNeEn1950() {
        UUID patient = patientSe1(CENTRE_A, "PAT_A");
        naissance(patient, LocalDate.of(1950, 1, 1));
        return patient;
    }

    @Test
    void la_derniere_creatinine_est_celle_du_centre_de_la_session_les_analyses_du_meme_patient_ailleurs_sont_ignorees() throws Exception {
        UUID patient = patientNeEn1950();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 aucune analyse", evaluationKdigo(kdigo(patient, medecinA())));
        analyse(CENTRE_A, patient, "A_1", fixe(10), "1.0");
        analyse(CENTRE_B, patient, "B_RECENTE", fixe(40), "9.0");
        m.put("02 creatinine 1.0 au centre A (D+10), 9.0 au centre B (D+40) : la plus recente du centre A", evaluationKdigo(kdigo(patient, medecinA())));
        analyse(CENTRE_A, patient, "A_2", fixe(20), "9.0");
        m.put("03 plus 9.0 au centre A (D+20) : la plus recente du centre", evaluationKdigo(kdigo(patient, medecinA())));
        analyse(CENTRE_A, patient, "A_SANS", fixe(30), null);
        m.put("04 plus une analyse sans creatinine (D+30) : ignoree", evaluationKdigo(kdigo(patient, medecinA())));
        analyse(CENTRE_A, patient, "A_ZERO", fixe(35), "0");
        m.put("05 plus une creatinine a 0 (D+35) : retenue (non nulle) puis non evaluable", evaluationKdigo(kdigo(patient, medecinA())));
        m.put("06 lecture par l'administrateur", evaluationKdigo(kdigo(patient, adminA())));
        m.put("07 lecture par l'infirmier", evaluationKdigo(kdigo(patient, infirmierA())));
        m.put("08 lecture depuis le centre B (fiche au centre A)", evaluationKdigo(kdigo(patient, as(CENTRE_B, "MEDECIN"))));
        arbre("cloisonnement", m);
    }

    @Test
    void deux_analyses_du_meme_jour_ne_sont_pas_departagees() throws Exception {
        UUID patient = patientNeEn1950();
        analyse(CENTRE_A, patient, "A_1", fixe(10), "1.0");
        analyse(CENTRE_A, patient, "A_2", fixe(10), "9.0");
        Map<String, Object> premiere = evaluationKdigo(kdigo(patient, medecinA()));
        // Ordre non defini a date egale (ORDER BY date_prelevement DESC seul, puis Stream.max qui garde la premiere lue) : le golden fige
        // seulement que l'une des deux est retenue ; le stade effectivement servi est note a part, hors golden.
        assertThat(premiere.get("stade")).isIn("G2", "G5");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 status", premiere.get("status"));
        m.put("02 stade parmi G2 (creatinine 1.0) et G5 (creatinine 9.0)", List.of("G2", "G5").contains(premiere.get("stade")));
        m.put("03 egfr renseigne", premiere.get("egfrRenseigne"));
        arbre("egalite_de_dates", m);
    }
}

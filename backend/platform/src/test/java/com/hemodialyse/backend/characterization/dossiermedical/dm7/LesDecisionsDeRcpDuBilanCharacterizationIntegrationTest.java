package com.hemodialyse.backend.characterization.dossiermedical.dm7;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DM7, lot 2 (décisions de RCP) : route {@code POST greffe/bilan/decisions-rcp}. La décision est une entité du bilan, réécrite avec lui
 * (suppression puis réinsertion de toutes les décisions à chaque sauvegarde du bilan).
 */
class LesDecisionsDeRcpDuBilanCharacterizationIntegrationTest extends Dm7CharacterizationSupport {

    @Override
    protected String dossier() {
        return "bilan/rcp";
    }

    private String decision(Object date, String avis, String compteRendu, Object revue) {
        return corps("centerId", CENTRE_A, "dateReunion", date, "avis", avis, "compteRendu", compteRendu, "prochaineDateRevue", revue);
    }

    private List<String> decisions(Resp r) {
        List<String> sortie = new ArrayList<>();
        if (r.status() != 200) {
            sortie.add(refus(r));
            return sortie;
        }
        JsonNode liste = corps(r).path("decisionsRcp");
        liste.forEach(d -> sortie.add(resume(d, "dateReunion", "avis", "compteRendu", "prochaineDateRevue")));
        return sortie;
    }

    @Test
    void une_decision_s_ajoute_au_bilan_et_les_decisions_sont_servies_de_la_plus_recente_a_la_plus_ancienne() throws Exception {
        UUID patient = patientDm7(CENTRE_A, "PAT_A");
        relatif(0);
        Map<String, Object> m = new LinkedHashMap<>();
        Resp premiere = doPost(bilanApi(patient) + "/decisions-rcp", decision(fixe(10), "AJOURNE", "Complements demandes", fixe(40)), medecinA());
        m.put("01 premiere decision sur un patient sans bilan (ouvre le bilan)", reponse(premiere));
        String idPremiere = corps(premiere).path("decisionsRcp").path(0).path("id").asString();
        doPost(bilanApi(patient) + "/decisions-rcp", decision(fixe(30), "FAVORABLE", "Avis favorable", null), medecinA());
        doPost(bilanApi(patient) + "/decisions-rcp", decision(fixe(20), "DEFAVORABLE", null, fixe(25)), medecinA());
        Resp aujourdhui = doPost(bilanApi(patient) + "/decisions-rcp", decision(relatif(0), "FAVORABLE", "Reunion du jour", null), medecinA());
        m.put("02 reponse du dernier POST : decisions chargees (recentes d'abord) puis la nouvelle en dernier", decisions(aujourdhui));
        m.put("03 la premiere decision garde son identifiant apres les reecritures", corps(aujourdhui).path("decisionsRcp").toString()
                .contains(idPremiere));
        m.put("04 etat des decisions en base", etatDesDecisions());
        m.put("05 relecture du bilan (GET : date de reunion decroissante)", decisions(doGet(bilanApi(patient), medecinA())));
        m.put("06 le statut du bilan n'est pas touche par une decision", resume(doGet(bilanApi(patient), medecinA()), "statut"));
        arbre("ajout_et_tri", m);
    }

    @Test
    void les_decisions_a_la_meme_date_et_les_doublons_sont_acceptes() throws Exception {
        UUID patient = patientDm7(CENTRE_A, "PAT_A");
        Map<String, Object> m = new LinkedHashMap<>();
        doPost(bilanApi(patient) + "/decisions-rcp", decision(fixe(10), "FAVORABLE", "Identique", null), medecinA());
        doPost(bilanApi(patient) + "/decisions-rcp", decision(fixe(10), "FAVORABLE", "Identique", null), medecinA());
        Resp r = doPost(bilanApi(patient) + "/decisions-rcp", decision(fixe(10), "DEFAVORABLE", "Meme jour, avis contraire", null), medecinA());
        m.put("01 trois decisions du meme jour (aucune unicite, aucun ordre de depart garanti : comptage)", corps(r).path("decisionsRcp").size());
        m.put("02 avis du meme jour", etatDesDecisions().stream().map(d -> d.get("avis")).sorted().toList());
        m.put("03 revue anterieure a la reunion acceptee", resume(corps(doPost(bilanApi(patient) + "/decisions-rcp",
                decision(fixe(50), "AJOURNE", "Revue avant reunion", fixe(1)), medecinA())).path("decisionsRcp").path(0), "dateReunion",
                "prochaineDateRevue"));
        arbre("doublons", m);
    }

    @Test
    void les_champs_obligatoires_la_date_future_et_les_valeurs_illisibles() throws Exception {
        UUID patient = patientDm7(CENTRE_A, "PAT_A");
        bilanSimple(CENTRE_A, patient, "BIL", "ELIGIBLE");
        String avant = empreinteGreffe();
        relatif(0);
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("01 date de reunion demain", refus(doPost(bilanApi(patient) + "/decisions-rcp", decision(relatif(1), "FAVORABLE", "x", null), medecinA())));
        cas.put("02 date de reunion absente", refus(doPost(bilanApi(patient) + "/decisions-rcp", decision(null, "FAVORABLE", "x", null), medecinA())));
        cas.put("03 avis absent", refus(doPost(bilanApi(patient) + "/decisions-rcp", decision(fixe(10), null, "x", null), medecinA())));
        cas.put("04 avis inconnu", refus(doPost(bilanApi(patient) + "/decisions-rcp", decision(fixe(10), "PEUT_ETRE", "x", null), medecinA())));
        cas.put("05 avis en minuscules", refus(doPost(bilanApi(patient) + "/decisions-rcp", decision(fixe(10), "favorable", "x", null), medecinA())));
        cas.put("06 date illisible", refus(doPost(bilanApi(patient) + "/decisions-rcp", "{\"dateReunion\":\"31/12/2026\",\"avis\":\"FAVORABLE\"}", medecinA())));
        cas.put("07 date inexistante", refus(doPost(bilanApi(patient) + "/decisions-rcp", "{\"dateReunion\":\"2026-02-30\",\"avis\":\"FAVORABLE\"}", medecinA())));
        cas.put("08 corps vide", refus(doPost(bilanApi(patient) + "/decisions-rcp", "", medecinA())));
        cas.put("09 corps illisible", refus(doPost(bilanApi(patient) + "/decisions-rcp", "{pas du json", medecinA())));
        m.put("cas", cas);
        m.put("aucune ecriture par les refus", empreinteGreffe().equals(avant));
        m.put("revue dans le futur lointain (acceptee)", refus(doPost(bilanApi(patient) + "/decisions-rcp", decision(fixe(10), "AJOURNE", null,
                alias(java.time.LocalDate.of(2099, 1, 1), "<2099>")), medecinA())));
        // KB-MED-87 : avis absent ou inconnu, date et corps illisibles en 400 (avant : 500 ou nom de classe) ; legacy archivé dans bilan/rcp/_ecarts-legacy
        arbre("refus_nouveau_code", m);
    }

    @Test
    void la_date_de_reunion_du_jour_est_acceptee_et_un_compte_rendu_tres_long_aussi() throws Exception {
        UUID patient = patientDm7(CENTRE_A, "PAT_A");
        relatif(0);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 reunion aujourd'hui", decisions(doPost(bilanApi(patient) + "/decisions-rcp", decision(relatif(0), "FAVORABLE", "Jour meme", null), medecinA())));
        Resp longue = doPost(bilanApi(patient) + "/decisions-rcp", decision(fixe(2), "AJOURNE", "c".repeat(8000), null), medecinA());
        m.put("02 compte rendu de 8000 caracteres", longue.status());
        m.put("03 longueur relue", jdbc.queryForObject("SELECT MAX(LENGTH(compte_rendu)) FROM decisions_rcp", Integer.class));
        arbre("bornes", m);
    }
}

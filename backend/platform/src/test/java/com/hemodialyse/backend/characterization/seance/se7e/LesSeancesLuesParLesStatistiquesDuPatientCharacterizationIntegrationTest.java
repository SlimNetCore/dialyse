package com.hemodialyse.backend.characterization.seance.se7e;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SE7e lot e2 : les six lectures SQL de {@code seances} des statistiques paramédicales du patient
 * ({@code PatientStatsQueryService.getParamedicalStats}, {@code :294-327} : compte, trois moyennes, évolution du poids avec le poids sec
 * en vigueur à la séance, évolution de la tension), toutes sur {@code seances s LEFT JOIN volet_paramedical vp ON vp.seance_id = s.id}
 * (SANS le centre du volet), {@code s.center_id = ? AND s.patient_id = ?}, filtre de dates en littéral, AUCUN prédicat de statut.
 * Observées par {@code GET /api/v1/patients/{id}/stats/paramedical} (ADMIN, INFIRMIER, MEDECIN, SECRETAIRE) et par l'export PDF
 * {@code GET /api/v1/patients/{id}/stats/export?format=pdf} (ADMIN). Complète DM1 et DM8 (dates relatives, statuts réalisés seulement).
 * <p>
 * Les moyennes (AVG d'un NUMERIC) n'ont pas la même précision sur H2 et PostgreSQL 16 : le golden fige H2, le rejeu
 * {@code backend/scripts/seance-se7e-pg16-rejeu.sh} fige PostgreSQL 16 (KB-SEA-87).
 */
class LesSeancesLuesParLesStatistiquesDuPatientCharacterizationIntegrationTest extends Se7eCharacterizationSupport {

    @Override
    protected String dossier() {
        return "statistiques";
    }

    private UUID patient;

    /** Patient PS du centre A : sept séances du 1er au 5 février 2026, statuts variés, volets dont un posé dans le centre B. */
    private void jeuDesStatistiques() {
        patient = patientSe1(CENTRE_A, "PS");
        UUID s1 = seanceEnBase(CENTRE_A, patient, "S1_VALIDEE", fixe(31), "VALIDEE");
        UUID s2 = seanceEnBase(CENTRE_A, patient, "S2_ANNULEE", fixe(32), "ANNULEE");
        seanceEnBase(CENTRE_A, patient, "S3_MINUSCULES_SANS_VOLET", fixe(33), "validee");
        UUID s4 = seanceEnBase(CENTRE_A, patient, "S4_CREE_VOLET_DU_CENTRE_B", fixe(34), "CREE");
        UUID s5 = seanceEnBase(CENTRE_A, patient, "S5_MEME_JOUR_VALIDEE", fixe(35), "VALIDEE");
        seanceEnBase(CENTRE_A, patient, "S6_MEME_JOUR_SIGNEE_SANS_VOLET", fixe(35), "SIGNEE");
        seanceEnBase(CENTRE_A, patient, "S8_HORS_PERIODE", fixe(60), "VALIDEE");
        // même patient (même identifiant) posé dans le centre B : jamais lu pour le centre A
        UUID s7 = seanceEnBase(CENTRE_B, patient, "S7_CENTRE_B", fixe(35), "VALIDEE");
        voletParamedical(CENTRE_A, s1, "70.50", "68.00", "130/80", "2500");
        voletParamedical(CENTRE_A, s2, "72.00", "70.00", "13/8", null);
        voletParamedical(CENTRE_B, s4, "80.00", "77.50", " 140 - 90 ", null);
        voletParamedical(CENTRE_A, s5, "69.25", "67.75", "120/70", "1500");
        voletParamedical(CENTRE_B, s7, "99.00", "90.00", "150/95", "9000");
        // poids sec : en vigueur à la date de la séance ; deux prescriptions le même jour départagées par la mise à jour ; poids nul ignoré ;
        // prescription du centre B ignorée
        prescription(CENTRE_A, patient, "PS_JANVIER", fixe(0), "66.00", CREATION_FIXE);
        prescription(CENTRE_A, patient, "PS_SANS_POIDS", fixe(31), null, CREATION_FIXE);
        prescription(CENTRE_A, patient, "PS_3_FEVRIER_TOT", fixe(33), "67.50", CREATION_FIXE);
        prescription(CENTRE_A, patient, "PS_3_FEVRIER_TARD", fixe(33), "68.00", CREATION_FIXE.plus(Duration.ofHours(2)));
        prescription(CENTRE_B, patient, "PS_CENTRE_B", fixe(34), "99.00", CREATION_FIXE);
    }

    /** Corps des statistiques : lignes d'évolution triées par le test (date, puis poids) : à date égale, l'ordre servi n'est pas garanti. */
    private Map<String, Object> statistiques(Resp r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", r.status());
        if (r.status() != 200) {
            m.put("corps", r.raw());
            return m;
        }
        JsonNode c = mapper.readTree(joursDesStatistiques(r.raw()));
        for (String cle : List.of("seanceCount", "avgPoidsAvantKg", "avgPoidsApresKg", "avgUfReelleMl", "poidsSecCibleKg")) {
            m.put(cle, c.path(cle));
        }
        List<String> poids = new ArrayList<>();
        for (JsonNode l : c.path("poidsEvolution")) {
            List<String> champs = new ArrayList<>();
            l.properties().forEach(e -> champs.add(e.getKey() + "=" + e.getValue()));
            poids.add(String.join(" ", champs));
        }
        m.put("ordre servi des dates (poidsEvolution)", datesServies(c.path("poidsEvolution")));
        poids.sort(Comparator.naturalOrder());
        m.put("poidsEvolution (lignes triees par le test)", poids);
        List<String> tension = new ArrayList<>();
        for (JsonNode l : c.path("taEvolution")) {
            List<String> champs = new ArrayList<>();
            l.properties().forEach(e -> champs.add(e.getKey() + "=" + e.getValue()));
            tension.add(String.join(" ", champs));
        }
        tension.sort(Comparator.naturalOrder());
        m.put("taEvolution (lignes triees par le test)", tension);
        return m;
    }

    private List<String> datesServies(JsonNode lignes) {
        List<String> d = new ArrayList<>();
        for (JsonNode l : lignes) {
            d.add(l.path("DATE_SEANCE").isMissingNode() ? l.path("date_seance").asString() : l.path("DATE_SEANCE").asString());
        }
        return d;
    }

    private Resp stats(String parametres) throws Exception {
        return doGet(PATIENTS_API + "/" + patient + "/stats/paramedical?centerId=" + CENTRE_A + parametres, infirmierA());
    }

    @Test
    void les_statistiques_paramedicales_comptent_toutes_les_seances_du_patient_dans_le_centre_quel_que_soit_le_statut() throws Exception {
        jeuDesStatistiques();

        arbre("tous_statuts_et_volet_d_un_autre_centre", statistiques(stats("&from=" + fixe(31) + "&to=" + fixe(35))));
    }

    @Test
    void les_bornes_de_periode_sont_incluses_et_chacune_peut_manquer() throws Exception {
        jeuDesStatistiques();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sans periode", statistiques(stats("")));
        m.put("from seul (D+34)", statistiques(stats("&from=" + fixe(34))));
        m.put("to seul (D+32)", statistiques(stats("&to=" + fixe(32))));
        m.put("un seul jour (D+35, deux seances)", statistiques(stats("&from=" + fixe(35) + "&to=" + fixe(35))));
        m.put("periode sans seance (D+36 a D+59)", statistiques(stats("&from=" + fixe(36) + "&to=" + fixe(59))));
        m.put("to seul (D+34) : trois volets, moyennes periodiques (precision H2, KB-SEA-87)", statistiques(stats("&to=" + fixe(34))));
        arbre("bornes_de_periode", m);
    }

    @Test
    void l_export_pdf_reprend_le_compte_et_les_poids_de_toutes_les_seances_de_la_periode() throws Exception {
        jeuDesStatistiques();

        MockHttpServletResponse r = binaire(PATIENTS_API + "/" + patient + "/stats/export?centerId=" + CENTRE_A + "&format=pdf&from=" + fixe(31)
                + "&to=" + fixe(35), adminA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", r.getStatus());
        m.put("contentType", r.getContentType());
        List<String> lignes = new ArrayList<>();
        boolean dansLesStatistiquesParamedicales = false;
        for (String ligne : texteNormalise(r.getContentAsByteArray())) {
            if (ligne.startsWith("Statistiques paramedicales")) dansLesStatistiquesParamedicales = true;
            if (ligne.startsWith("Statistiques medicales")) dansLesStatistiquesParamedicales = false;
            if (dansLesStatistiquesParamedicales || ligne.startsWith("Rapport") || ligne.contains("Periode") || ligne.startsWith("pages=")) {
                lignes.add(ligne);
            }
        }
        // lignes du tableau (une par séance) : triées par le test, l'ordre de deux séances du même jour n'étant pas garanti (KB-SEA-86)
        List<String> rangees = new ArrayList<>(lignes.stream().filter(l -> l.matches("^\\d{4}-\\d{2}-\\d{2}\\b.*")).toList());
        lignes.removeAll(rangees);
        rangees.sort(Comparator.naturalOrder());
        m.put("texte (partie paramedicale, en-tete et pagination)", lignes);
        m.put("rangees du tableau des poids (triees par le test)", rangees);
        arbre("export_pdf", m);
    }
}

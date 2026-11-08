package com.hemodialyse.backend.characterization.seance.se7e;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SE7e lot e1 : les quatre lectures SQL de {@code seances} du pilotage de la Direction, observées par les routes de la Direction (rôle
 * DIRECTION, société du jeton revérifiée en base) :
 * <ul>
 *   <li>{@code DirectionDashboardQueryService} : comptage par centre ({@code :121}), période précédente ({@code :167}), détail mensuel
 *   ({@code :183}), servis par {@code GET /api/v1/direction/overview} et repris par {@code GET /api/v1/direction/report} (PDF) ;</li>
 *   <li>{@code DirectionBreakdownQueryService} : séances par caisse ({@code :190}, jointure {@code patients} sur l'identifiant ET le
 *   centre), servies par {@code GET /api/v1/direction/breakdown} ;</li>
 *   <li>{@code DirectionAbsencesQueryService} : séances réalisées par centre ({@code :115}), dénominateur du taux d'absentéisme, servies
 *   par {@code GET /api/v1/direction/absences}.</li>
 * </ul>
 * Complète F5 (part financière) sur les bornes, les statuts, la casse, les doublons, la fiche dans un autre centre, la société.
 */
class LesSeancesLuesParLePilotageDeLaDirectionCharacterizationIntegrationTest extends Se7eCharacterizationSupport {

    @Override
    protected String dossier() {
        return "pilotage";
    }

    private String periode(LocalDate du, LocalDate au) {
        return "?from=" + du + "&to=" + au;
    }

    /**
     * Jeu de février 2026 (période observée {@code <D+30>} à {@code <D+59>}, 30 jours ; période précédente {@code <D+0>} à {@code <D+29>}).
     */
    private void jeuDuPilotage() {
        societeDeLaDirection();
        UUID pa1 = patientSe1(CENTRE_A, "PA1");
        UUID pa2 = patientSe1(CENTRE_A, "PA2");
        UUID pb1 = patientSe1(CENTRE_B, "PB1");
        UUID pc1 = patientSe1(CENTRE_C, "PC1");
        // période observée, centre A : bornes incluses, lendemain exclu
        seanceEnBase(CENTRE_A, pa1, "A_DEBUT_VALIDEE", fixe(30), "VALIDEE");
        seanceEnBase(CENTRE_A, pa1, "A_SIGNEE", fixe(45), "SIGNEE");
        seanceEnBase(CENTRE_A, pa1, "A_FIN_FACTUREE", fixe(59), "FACTUREE");
        seanceEnBase(CENTRE_A, pa1, "A_LENDEMAIN", fixe(60), "VALIDEE");
        // statuts non réalisés et casse
        seanceEnBase(CENTRE_A, pa2, "A_MINUSCULES", fixe(40), "validee");
        seanceEnBase(CENTRE_A, pa2, "A_BLANC_DE_FIN", fixe(41), "VALIDEE ");
        seanceEnBase(CENTRE_A, pa2, "A_ANNULEE", fixe(42), "ANNULEE");
        seanceEnBase(CENTRE_A, pa2, "A_ABSENT", fixe(43), "ABSENT");
        seanceEnBase(CENTRE_A, pa2, "A_CREE", fixe(44), "CREE");
        seanceEnBase(CENTRE_A, pa2, "A_PLANIFIEE", fixe(46), "PLANIFIEE");
        // deux séances réalisées du même patient le même jour, et une séance du centre A posée sur un patient dont la fiche est au centre B
        seanceEnBase(CENTRE_A, pa2, "A_DOUBLON_1", fixe(47), "VALIDEE");
        seanceEnBase(CENTRE_A, pa2, "A_DOUBLON_2", fixe(47), "SIGNEE");
        seanceEnBase(CENTRE_A, pb1, "A_FICHE_AU_CENTRE_B", fixe(48), "VALIDEE");
        // période précédente : bornes incluses, veille exclue
        seanceEnBase(CENTRE_A, pa1, "A_PREC_FIN", fixe(29), "VALIDEE");
        seanceEnBase(CENTRE_A, pa1, "A_PREC_DEBUT", fixe(0), "SIGNEE");
        seanceEnBase(CENTRE_A, pa1, "A_PREC_VEILLE", fixe(-1), "VALIDEE");
        // centre B de la même société
        seanceEnBase(CENTRE_B, pb1, "B_FEVRIER", fixe(50), "VALIDEE");
        seanceEnBase(CENTRE_B, pb1, "B_FIN", fixe(59), "SIGNEE");
        seanceEnBase(CENTRE_B, pb1, "B_PREC", fixe(10), "FACTUREE");
        // centre C d'une autre société
        seanceEnBase(CENTRE_C, pc1, "C_AUTRE_SOCIETE", fixe(45), "VALIDEE");
        seanceEnBase(CENTRE_C, pc1, "C_AUTRE_SOCIETE_PREC", fixe(5), "VALIDEE");
    }

    /** La part « séances » d'une vue d'ensemble : par centre, totaux, détail mensuel, période précédente. */
    private Map<String, Object> seancesDeLaVue(Resp r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", r.status());
        if (r.status() != 200) {
            m.put("corps", r.raw());
            return m;
        }
        JsonNode c = corps(r);
        m.put("from", c.path("from").asString());
        m.put("to", c.path("to").asString());
        List<String> centres = new ArrayList<>();
        for (JsonNode centre : c.path("centres")) {
            centres.add(centre.path("nom").asString() + " | " + centre.path("centerId").asString() + " | seances=" + centre.path("seances"));
        }
        m.put("centres (ordre servi)", centres);
        m.put("totaux.seances", c.path("totaux").path("seances"));
        List<String> mensuel = new ArrayList<>();
        for (JsonNode p : c.path("mensuel")) {
            mensuel.add(p.path("mois").asString() + " | " + p.path("centerId").asString() + " | seances=" + p.path("seances"));
        }
        m.put("mensuel (ordre servi)", mensuel);
        JsonNode prec = c.path("periodePrecedente");
        m.put("periodePrecedente", prec.path("from").asString() + " -> " + prec.path("to").asString() + " | seances=" + prec.path("seances"));
        return m;
    }

    @Test
    void la_vue_d_ensemble_compte_les_seances_realisees_par_centre_par_mois_et_sur_la_periode_precedente() throws Exception {
        jeuDuPilotage();

        arbre("vue_d_ensemble", seancesDeLaVue(doGet(DIRECTION_API + "/overview" + periode(fixe(30), fixe(59)), directionSe7e())));
    }

    @Test
    void le_detail_mensuel_traverse_l_annee_et_une_periode_d_un_jour_se_compare_a_la_veille() throws Exception {
        societeDeLaDirection();
        UUID pa1 = patientSe1(CENTRE_A, "PA1");
        UUID pb1 = patientSe1(CENTRE_B, "PB1");
        seanceEnBase(CENTRE_A, pa1, "A_15_DECEMBRE", fixe(-17), "VALIDEE");
        seanceEnBase(CENTRE_A, pa1, "A_31_DECEMBRE", fixe(-1), "SIGNEE");
        seanceEnBase(CENTRE_A, pa1, "A_1ER_JANVIER", fixe(0), "FACTUREE");
        seanceEnBase(CENTRE_A, pa1, "A_15_JANVIER", fixe(14), "VALIDEE");
        seanceEnBase(CENTRE_A, pa1, "A_14_DECEMBRE", fixe(-18), "VALIDEE");
        seanceEnBase(CENTRE_A, pa1, "A_13_NOVEMBRE", fixe(-49), "SIGNEE");
        seanceEnBase(CENTRE_B, pb1, "B_22_DECEMBRE", fixe(-10), "VALIDEE");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("du 15 decembre 2025 au 15 janvier 2026 (32 jours)", seancesDeLaVue(doGet(DIRECTION_API + "/overview" + periode(fixe(-17), fixe(14)),
                directionSe7e())));
        m.put("un seul jour, le 1er janvier 2026", seancesDeLaVue(doGet(DIRECTION_API + "/overview" + periode(fixe(0), fixe(0)), directionSe7e())));
        arbre("annee_et_jour_unique", m);
    }

    @Test
    void la_repartition_par_caisse_ne_compte_que_les_seances_dont_la_fiche_est_dans_le_centre_de_la_seance() throws Exception {
        jeuDuPilotage();
        UUID pa1 = UUID.nameUUIDFromBytes(("se1-patient-PA1-" + CENTRE_A).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID pa3 = patientSe1(CENTRE_A, "PA3");
        UUID pb1 = UUID.nameUUIDFromBytes(("se1-patient-PB1-" + CENTRE_B).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID payeurA = payeur(CENTRE_A, "CNAS", "CNAS du centre A");
        UUID payeurB = payeur(CENTRE_B, "CNAS-B", "CNAS du centre B");
        jdbc.update("UPDATE patients SET centre_payeur_id = ? WHERE id = ?", payeurA, pa1);
        // fiche du centre A rattachée à un centre payeur du centre B (aucun contrôle de centre sur la chaîne payeur, agence, caisse)
        jdbc.update("UPDATE patients SET centre_payeur_id = ? WHERE id = ?", payeurB, pa3);
        jdbc.update("UPDATE patients SET centre_payeur_id = ? WHERE id = ?", payeurB, pb1);
        seanceEnBase(CENTRE_A, pa3, "A_PAYEUR_DU_CENTRE_B", fixe(49), "VALIDEE");

        String chemin = periode(fixe(30), fixe(59));
        JsonNode r = corps(doGet(DIRECTION_API + "/breakdown" + chemin, directionSe7e()));
        List<String> caisses = new ArrayList<>();
        for (JsonNode c : r.path("caisses")) {
            caisses.add(c.path("centre").asString() + " | code=" + c.path("caisseCode").asString() + " | " + c.path("caisse").asString()
                    + " | patients=" + c.path("patients") + " | seances=" + c.path("seances"));
        }
        caisses.sort(Comparator.naturalOrder());
        List<String> totaux = new ArrayList<>();
        for (JsonNode t : r.path("caisseTotaux")) {
            totaux.add("code=" + t.path("caisseCode").asString() + " | patients=" + t.path("patients") + " | seances=" + t.path("seances"));
        }
        totaux.sort(Comparator.naturalOrder());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", r.path("from").asString());
        m.put("to", r.path("to").asString());
        m.put("caisses (triees par le test : a chiffre d'affaires egal, l'ordre servi suit l'ordre SQL)", caisses);
        m.put("caisseTotaux (tries par le test)", totaux);
        m.put("vue d'ensemble sur la meme periode (sans jointure patients)", seancesDeLaVue(doGet(DIRECTION_API + "/overview" + chemin, directionSe7e())));
        arbre("repartition_par_caisse", m);
    }

    private UUID payeur(UUID centre, String code, String nom) {
        UUID caisse = UUID.nameUUIDFromBytes(("se7e-caisse-" + code).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID agence = UUID.nameUUIDFromBytes(("se7e-agence-" + code).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID payeur = UUID.nameUUIDFromBytes(("se7e-payeur-" + code).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        jdbc.update("INSERT INTO caisse_assurance (id, center_id, code, nom) VALUES (?,?,?,?)", caisse, centre, code, nom);
        jdbc.update("INSERT INTO agence (id, center_id, caisse_id, code, nom) VALUES (?,?,?,?,?)", agence, centre, caisse, "AG-" + code, "Agence " + code);
        jdbc.update("INSERT INTO centre_payeur (id, center_id, agence_id, code, nom) VALUES (?,?,?,?,?)", payeur, centre, agence, "CP-" + code,
                "Payeur " + code);
        return payeur;
    }

    @Test
    void le_taux_d_absenteisme_rapporte_les_absences_aux_seances_realisees_du_centre() throws Exception {
        societeDeLaDirection();
        UUID pa1 = patientSe1(CENTRE_A, "PA1");
        UUID pa2 = patientSe1(CENTRE_A, "PA2");
        UUID pb1 = patientSe1(CENTRE_B, "PB1");
        // centre A : 5 absences (effectif publié) ; centre B : 1 absence (masqué, exclu du total)
        for (int i = 0; i < 5; i++) {
            absenceComplete(CENTRE_A, "ABS_A" + i, pa1, fixe(32 + i));
        }
        absenceComplete(CENTRE_B, "ABS_B", pb1, fixe(35));
        // séances du centre A sur février 2026 ({@code <D+31>} à {@code <D+58>})
        seanceEnBase(CENTRE_A, pa2, "A_DEBUT", fixe(31), "VALIDEE");
        seanceEnBase(CENTRE_A, pa2, "A_SIGNEE", fixe(40), "SIGNEE");
        seanceEnBase(CENTRE_A, pa2, "A_FIN", fixe(58), "FACTUREE");
        seanceEnBase(CENTRE_A, pa2, "A_DOUBLON_1", fixe(41), "VALIDEE");
        seanceEnBase(CENTRE_A, pa2, "A_DOUBLON_2", fixe(41), "VALIDEE");
        seanceEnBase(CENTRE_A, pb1, "A_FICHE_AU_CENTRE_B", fixe(42), "VALIDEE");
        seanceEnBase(CENTRE_A, pa2, "A_MINUSCULES", fixe(43), "validee");
        seanceEnBase(CENTRE_A, pa2, "A_ANNULEE", fixe(44), "ANNULEE");
        seanceEnBase(CENTRE_A, pa2, "A_ABSENT", fixe(45), "ABSENT");
        seanceEnBase(CENTRE_A, pa2, "A_VEILLE", fixe(30), "VALIDEE");
        seanceEnBase(CENTRE_A, pa2, "A_LENDEMAIN", fixe(59), "VALIDEE");
        seanceEnBase(CENTRE_B, pb1, "B_1", fixe(36), "VALIDEE");
        seanceEnBase(CENTRE_B, pb1, "B_2", fixe(37), "SIGNEE");

        JsonNode r = corps(doGet(DIRECTION_API + "/absences" + periode(fixe(31), fixe(58)), directionSe7e()));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", r.path("from").asString());
        m.put("to", r.path("to").asString());
        JsonNode t = r.path("total");
        m.put("total", "nbAbsences=" + t.path("nbAbsences") + " | nbSeances=" + t.path("nbSeances") + " | tauxAbsenteisme=" + t.path("tauxAbsenteisme"));
        List<String> centres = new ArrayList<>();
        for (JsonNode c : r.path("centres")) {
            JsonNode s = c.path("stats");
            centres.add(c.path("nom").asString() + " | nbAbsences=" + s.path("nbAbsences") + " | nbSeances=" + s.path("nbSeances")
                    + " | tauxAbsenteisme=" + s.path("tauxAbsenteisme"));
        }
        m.put("centres (ordre servi)", centres);
        m.put("centresMasques", r.path("centresMasques"));
        arbre("taux_d_absenteisme", m);
    }

    @Test
    void le_rapport_imprime_de_la_direction_reprend_les_seances_de_la_vue_d_ensemble() throws Exception {
        jeuDuPilotage();

        MockHttpServletResponse r = binaire(DIRECTION_API + "/report" + periode(fixe(30), fixe(59)), directionSe7e());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", r.getStatus());
        m.put("contentType", r.getContentType());
        List<String> lignes = new ArrayList<>();
        for (String ligne : texteNormalise(r.getContentAsByteArray())) {
            if (ligne.contains("Séance") || ligne.contains("Alpha") || ligne.contains("Beta") || ligne.contains("Gamma")
                    || ligne.contains("Total") || ligne.contains("période") || ligne.matches("^\\d{4}-\\d{2}\\b.*") || ligne.startsWith("pages=")) {
                lignes.add(ligne);
            }
        }
        m.put("lignes du PDF qui portent les seances, les centres ou la periode", lignes);
        arbre("rapport_pdf", m);
    }
}

package com.hemodialyse.backend.characterization.facturation.pilotage;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * F5 : part financière des lectures Direction ({@code /api/v1/direction/overview}, {@code /breakdown}, {@code /absences}) :
 * chiffre d'affaires HT/TTC, encaissé, reste à recouvrer, taux d'encaissement, CA par caisse, part du CA des absences.
 * Les parties patients/âge/anémie/motifs relèvent des autres contextes et ne sont pas caractérisées ici.
 */
class LecturesFinancieresDirectionCharacterizationIntegrationTest extends PilotageFacturationSupport {


    @Override
    protected String theme() {
        return "lectures-direction";
    }

    private RequestPostProcessor directionDeLaSociete2() {
        return user(new com.hemodialyse.backend.infrastructure.security.UserPrincipal(DIRECTEUR_2.toString(), null, "u-direction-2", "",
                List.of(new SimpleGrantedAuthority("ROLE_DIRECTION")), true, SOC_2.toString()));
    }

    /**
     * Société 1 (centres A, B, vide) : factures de février, janvier et décembre, règlements dont un hors période et un
     * remboursement ; société 2 (centre C) : une facture de février.
     */
    private void jeuDeLaSociete() {
        utilisateur(DIRECTEUR_2, "zt-f5-direction-2", "Directeur Deux");
        jdbc.update("INSERT INTO app_user_societe (user_id, societe_id) VALUES (?, ?)", DIRECTEUR_2, SOC_2);
        UUID f1 = facture(CENTRE_A, P_AMRANI, "FAC-0001", "2026-02-01", "19.00", "1000.00", "190.00", "1190.00", AGENCE_1, "PERMANENT");
        UUID f2 = facture(CENTRE_A, P_AMRANI, "FAC-0002", "2026-02-28", "19.00", "500.00", "95.00", "595.00", AGENCE_1, "PERMANENT");
        UUID f3 = facture(CENTRE_A, P_BELKACEM, "FAC-0003", "2026-02-15", "0.00", "800.00", "0.00", "800.00", AGENCE_2, "VACANCIER");
        facture(CENTRE_A, P_CHIKHI, "FAC-0004", "2026-02-10", "0.00", "100.50", "0.00", "100.50", null, null);
        UUID janvier = facture(CENTRE_A, P_AMRANI, "FAC-0000", "2026-01-15", "19.00", "200.00", "38.00", "238.00", AGENCE_1, "PERMANENT");
        UUID decembre = facture(CENTRE_A, P_AMRANI, "FAC-0099", "2025-12-20", "0.00", "70.00", "0.00", "70.00", AGENCE_1, "PERMANENT");
        UUID fb = facture(CENTRE_B, P_CENTRE_B, "FAC-B-0001", "2026-02-12", "19.00", "400.00", "76.00", "476.00", AGENCE_B, "PERMANENT");
        facture(CENTRE_C, P_CENTRE_B, "FAC-C-0001", "2026-02-12", "0.00", "9999.00", "0.00", "9999.00", null, "PERMANENT");
        paiement(f1, CENTRE_A, "1190.00", "2026-03-01");
        paiement(f2, CENTRE_A, "200.00", "2026-02-28");
        paiement(f3, CENTRE_A, "850.00", "2026-02-20");
        paiement(f3, CENTRE_A, "-50.00", "2026-02-21");
        paiement(janvier, CENTRE_A, "238.00", "2026-01-20");
        paiement(decembre, CENTRE_A, "10.00", "2026-01-02");
        paiement(fb, CENTRE_B, "100.00", "2026-02-13");
        seance(P_AMRANI, CENTRE_A, "2026-02-03", "FACTUREE", f1);
        seance(P_AMRANI, CENTRE_A, "2026-02-04", "SIGNEE", null);
        seance(P_BELKACEM, CENTRE_A, "2026-02-16", "VALIDEE", null);
        seance(P_BELKACEM, CENTRE_A, "2026-02-17", "PLANIFIEE", null);
        seance(P_CENTRE_B, CENTRE_B, "2026-02-12", "FACTUREE", fb);
        seance(P_AMRANI, CENTRE_A, "2026-01-15", "FACTUREE", janvier);
    }

    @Test
    void la_vue_d_ensemble_consolide_ca_encaisse_reste_et_taux_par_centre_et_pour_la_societe() throws Exception {
        jeuDeLaSociete();

        golden("overview_fevrier_2026", doGet(DIRECTION + "/overview?from=2026-02-01&to=2026-02-28", direction()));
    }

    @Test
    void la_vue_d_ensemble_donne_le_detail_mensuel_et_compare_a_la_periode_precedente_de_meme_duree() throws Exception {
        jeuDeLaSociete();

        golden("overview_janvier_fevrier_2026", doGet(DIRECTION + "/overview?from=2026-01-01&to=2026-02-28", direction()));
        golden("overview_un_jour_2026_02_28", doGet(DIRECTION + "/overview?from=2026-02-28&to=2026-02-28", direction()));
    }

    @Test
    void la_direction_d_une_autre_societe_ne_voit_que_ses_centres() throws Exception {
        jeuDeLaSociete();

        golden("overview_societe_2", doGet(DIRECTION + "/overview?from=2026-02-01&to=2026-02-28", directionDeLaSociete2()));
    }

    @Test
    void le_taux_d_encaissement_est_arrondi_au_dixieme_et_le_reste_ne_descend_pas_sous_zero() throws Exception {
        // 3 factures de 1,00 TTC : 1 centime encaissé sur 333,33 -> taux 0,0 ; trop-perçu -> reste 0 et taux > 100
        UUID a = facture(CENTRE_A, P_AMRANI, "FAC-1", "2026-06-01", "0.00", "333.33", "0.00", "333.33", AGENCE_1, "PERMANENT");
        paiement(a, CENTRE_A, "0.01", "2026-06-02");
        UUID b = facture(CENTRE_B, P_CENTRE_B, "FAC-2", "2026-06-01", "0.00", "10.00", "0.00", "10.00", AGENCE_B, "PERMANENT");
        paiement(b, CENTRE_B, "12.50", "2026-06-02");

        golden("taux_et_reste", doGet(DIRECTION + "/overview?from=2026-06-01&to=2026-06-30", direction()));
    }

    @Test
    void la_periode_est_controlee_debut_avant_fin_et_cinq_ans_au_plus() throws Exception {
        jeuDeLaSociete();
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("debut apres fin", resume(doGet(DIRECTION + "/overview?from=2026-03-01&to=2026-02-01", direction())));
        cas.put("cinq ans exactement", resume(doGet(DIRECTION + "/overview?from=2021-02-28&to=2026-02-28", direction())));
        cas.put("cinq ans et un jour", resume(doGet(DIRECTION + "/overview?from=2021-02-28&to=2026-03-01", direction())));
        cas.put("date illisible", resume(doGet(DIRECTION + "/overview?from=01-02-2026&to=2026-02-28", direction())));
        cas.put("seulement from futur", resume(doGet(DIRECTION + "/overview?from=2999-01-01", direction())));
        cas.put("breakdown debut apres fin", resume(doGet(DIRECTION + "/breakdown?from=2026-03-01&to=2026-02-01", direction())));
        cas.put("breakdown cinq ans et un jour", resume(doGet(DIRECTION + "/breakdown?from=2021-02-28&to=2026-03-01", direction())));
        cas.put("absences debut apres fin", resume(doGet(DIRECTION + "/absences?from=2026-03-01&to=2026-02-01", direction())));
        cas.put("absences cinq ans et un jour", resume(doGet(DIRECTION + "/absences?from=2021-02-28&to=2026-03-01", direction())));
        goldenNode("periodes_nouveau_code", mapper.valueToTree(cas));
    }

    @Test
    void sans_parametre_la_periode_va_du_1er_janvier_de_l_annee_a_aujourd_hui() throws Exception {
        jeuDeLaSociete();

        LocalDate avant = LocalDate.now();
        JsonNode o = json(doGet(DIRECTION + "/overview", direction()));
        LocalDate apres = LocalDate.now();

        // Tolère un passage à minuit entre l'appel et la lecture de l'horloge
        LocalDate fin = LocalDate.parse(o.path("to").asString());
        org.junit.jupiter.api.Assertions.assertTrue(fin.equals(avant) || fin.equals(apres), "to = " + fin);
        assertEquals(fin.getYear() + "-01-01", o.path("from").asString());
    }

    @Test
    void la_repartition_par_caisse_donne_le_ca_ht_des_factures_selon_l_agence_figee() throws Exception {
        jeuDeLaSociete();

        JsonNode r = json(doGet(DIRECTION + "/breakdown?from=2026-02-01&to=2026-02-28", direction()));

        goldenNode("breakdown_caisses_fevrier_2026", projeter(r, "from", "to", "caisses", "caisseTotaux"));
    }

    @Test
    void la_repartition_par_caisse_compte_les_factures_sans_caisse_dans_une_ligne_sans_code() throws Exception {
        UUID f = facture(CENTRE_A, P_CHIKHI, "FAC-1", "2026-07-01", "0.00", "55.55", "0.00", "55.55", null, null);
        seance(P_CHIKHI, CENTRE_A, "2026-07-01", "FACTUREE", f);

        JsonNode r = json(doGet(DIRECTION + "/breakdown?from=2026-07-01&to=2026-07-31", direction()));

        goldenNode("breakdown_caisses_sans_caisse", projeter(r, "caisses", "caisseTotaux"));
    }

    @Test
    void la_part_du_ca_des_absences_rapporte_la_valorisation_au_ca_ht_facture() throws Exception {
        jeuDeLaSociete();
        absence(CENTRE_A, P_AMRANI, "2026-02-05", "NON_JUSTIFIEE", "100.00", "119.00");
        absence(CENTRE_A, P_BELKACEM, "2026-02-06", "JUSTIFIEE", "50.00", "50.00");
        absence(CENTRE_A, P_CHIKHI, "2026-02-07", "RATTRAPEE", "999.00", "999.00");

        // KB-ABS-07 corrigé : 2 absences comptabilisées au centre A (< 5) -> chiffres d'absence masqués, dont la part du CA
        goldenNode("absences_part_du_ca_fevrier_2026_nouveau_code", vueDesAbsences());

        // Au-delà du seuil (5 absences comptabilisées au centre A) : la part du CA reste publiée et calculée comme avant
        // (valorisation HT 250,00 / CA HT 2 400,50 = 10,41 %)
        absence(CENTRE_A, P_AMRANI, "2026-02-08", "JUSTIFIEE", "30.00", "30.00");
        absence(CENTRE_A, P_BELKACEM, "2026-02-09", "A_QUALIFIER", "20.00", "20.00");
        absence(CENTRE_A, P_CHIKHI, "2026-02-10", "NON_JUSTIFIEE", "50.00", "59.50");
        goldenNode("absences_part_du_ca_au_dela_du_seuil", vueDesAbsences());
    }

    private ObjectNode vueDesAbsences() throws Exception {
        JsonNode r = json(doGet(DIRECTION + "/absences?from=2026-02-01&to=2026-02-28", direction()));

        ObjectNode vue = mapper.createObjectNode();
        vue.set("total", r.path("total"));
        ObjectNode parCentre = vue.putObject("centres");
        for (JsonNode c : r.path("centres")) {
            parCentre.set(new Scrubber().scrubString(c.path("centerId").asString()), c.path("stats"));
        }
        return vue;
    }

    @Test
    void les_lectures_financieres_de_la_direction_repondent_selon_le_profil() throws Exception {
        jeuDeLaSociete();
        Map<String, Object> cas = new LinkedHashMap<>();
        for (String route : List.of("/overview", "/breakdown", "/absences")) {
            String chemin = DIRECTION + route + "?from=2026-02-01&to=2026-02-28";
            cas.put(route + " DIRECTION", doGet(chemin, direction()).status());
            cas.put(route + " ADMIN", doGet(chemin, adminA()).status());
            cas.put(route + " SECRETAIRE", doGet(chemin, as(CENTRE_A, "SECRETAIRE")).status());
            cas.put(route + " SUPERADMIN", doGet(chemin, as(CENTRE_A, "SUPERADMIN")).status());
            cas.put(route + " anonyme", doGetAnonyme(chemin).status());
        }
        UUID detache = nom("DIRECTEUR_DETACHE");
        utilisateur(detache, "zt-f5-direction-detache", "Directeur détaché");
        RequestPostProcessor sansRattachement = user(new com.hemodialyse.backend.infrastructure.security.UserPrincipal(detache.toString(),
                null, "u-detache", "", List.of(new SimpleGrantedAuthority("ROLE_DIRECTION")), true, SOC_1.toString()));
        cas.put("/overview DIRECTION detache de sa societe", doGet(DIRECTION + "/overview?from=2026-02-01&to=2026-02-28", sansRattachement).status());
        cas.put("/overview DIRECTION sans societe dans le jeton", doGet(DIRECTION + "/overview",
                user(new com.hemodialyse.backend.infrastructure.security.UserPrincipal(DIRECTEUR.toString(), null, "u", "",
                        List.of(new SimpleGrantedAuthority("ROLE_DIRECTION")), true, null))).status());
        goldenNode("profils", mapper.valueToTree(cas));
    }

    @Test
    void une_societe_active_sans_aucune_facture_donne_des_zeros() throws Exception {
        golden("overview_sans_facture", doGet(DIRECTION + "/overview?from=2026-02-01&to=2026-02-28", direction()));
    }

    @Test
    void une_societe_sans_aucun_centre_donne_une_vue_vide() throws Exception {
        UUID societeVide = nom("SOCIETE_SANS_CENTRE");
        UUID directeur = nom("DIRECTEUR_SOCIETE_SANS_CENTRE");
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                societeVide, "ZT-F5-S3", "Société sans centre");
        utilisateur(directeur, "zt-f5-direction-3", "Directeur Trois");
        jdbc.update("INSERT INTO app_user_societe (user_id, societe_id) VALUES (?, ?)", directeur, societeVide);
        RequestPostProcessor direction3 = user(new com.hemodialyse.backend.infrastructure.security.UserPrincipal(directeur.toString(),
                null, "u-direction-3", "", List.of(new SimpleGrantedAuthority("ROLE_DIRECTION")), true, societeVide.toString()));

        golden("overview_societe_sans_centre", doGet(DIRECTION + "/overview?from=2026-02-01&to=2026-02-28", direction3));
        golden("breakdown_societe_sans_centre", doGet(DIRECTION + "/breakdown?from=2026-02-01&to=2026-02-28", direction3));
        golden("absences_societe_sans_centre_nouveau_code", doGet(DIRECTION + "/absences?from=2026-02-01&to=2026-02-28", direction3));
    }

    private void absence(UUID centre, UUID patient, String jour, String statut, String ht, String ttc) {
        jdbc.update("INSERT INTO absence_patient (id, center_id, patient_id, date_seance, source, statut, motif, prix_ttc, taux_tva, montant_ht) "
                        + "VALUES (?, ?, ?, ?, 'MANUELLE', ?, 'MALADIE', ?, 19.00, ?)",
                UUID.nameUUIDFromBytes(("f5:absence:" + centre + patient + jour).getBytes()), centre, patient, Date.valueOf(LocalDate.parse(jour)),
                statut, new BigDecimal(ttc), new BigDecimal(ht));
    }

    private ObjectNode resume(Resp r) {
        ObjectNode o = mapper.createObjectNode();
        o.put("status", r.status());
        if (r.raw() == null || r.raw().isBlank()) {
            o.put("corps", "(vide)");
            return o;
        }
        JsonNode n = mapper.readTree(r.raw());
        if (r.status() == 200) {
            o.put("from", n.path("from").asString());
            o.put("to", n.path("to").asString());
        } else {
            String code = n.path("code").asString("");
            o.put("code", code);
            if (!"INTERNAL_ERROR".equals(code)) o.put("detail", n.path("detail").asString());
        }
        return o;
    }
}

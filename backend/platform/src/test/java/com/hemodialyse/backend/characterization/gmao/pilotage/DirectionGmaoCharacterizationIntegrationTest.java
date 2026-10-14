package com.hemodialyse.backend.characterization.gmao.pilotage;

import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * Filet de caractérisation du pilotage direction de la GMAO : GET /api/v1/direction/gmao (coûts, état du parc,
 * indisponibilité, aide à la décision de réforme, alertes, par centre et consolidés) et la part GMAO de
 * GET /api/v1/direction/capacite (comptage des générateurs). Période à dates fixes (2026-01-01 -> 2026-06-30) :
 * toutes les valeurs sont exactes ; la société est celle du jeton (revérifiée en base à chaque appel).
 */
class DirectionGmaoCharacterizationIntegrationTest extends PilotageCharacterizationSupport {

    private static final String GMAO_DIRECTION = DIRECTION + "/gmao";
    private static final String CAPACITE = DIRECTION + "/capacite";
    private static final String PERIODE = "?from=2026-01-01&to=2026-06-30";

    private static final UUID SOC_VIDE = nom("SOCIETE_SANS_CENTRE");
    private static final UUID DIRECTEUR_2 = nom("DIRECTEUR_2");
    private static final UUID DIRECTEUR_VIDE = nom("DIRECTEUR_SOCIETE_VIDE");

    private static OffsetDateTime le(String iso) {
        return OffsetDateTime.parse(iso);
    }

    /** Parc, interventions, coûts et patients des centres A et B (société 1) et du centre C (société 2). */
    private void semerLePilotage() {
        // ---- centre A
        UUID e1 = equipement("DG_E1", CENTRE_A, e -> e.prix = new BigDecimal("10000.00"));
        UUID i1 = intervention("DG_I1", e1, CENTRE_A, x -> {
            x.debut = le("2026-03-10T08:00:00Z");
            x.fin = le("2026-03-10T10:00:00Z");
        });
        ligne(i1, "PIECE", "Pompe", "2", "1500.00");
        UUID i1b = intervention("DG_I1_ANCIENNE", e1, CENTRE_A, x -> {
            x.debut = le("2025-06-01T08:00:00Z");
            x.fin = le("2025-06-01T10:00:00Z");
            x.creation = le("2025-06-01T07:00:00Z");
        });
        ligne(i1b, "PIECE", "Joint", "1", "500.00");
        historique(e1, CENTRE_A, "EN_SERVICE", "HORS_SERVICE", le("2026-02-01T08:00:00Z"));
        historique(e1, CENTRE_A, "HORS_SERVICE", "EN_SERVICE", le("2026-02-01T11:00:00Z"));

        UUID e2 = equipement("DG_E2", CENTRE_A, e -> {
            e.statut = "EN_MAINTENANCE";
            e.prix = new BigDecimal("2000.00");
        });
        UUID i2 = intervention("DG_I2", e2, CENTRE_A, x -> {
            x.statut = "EN_COURS";
            x.debut = le("2026-03-20T08:00:00Z");
            x.fin = null;
        });
        ligne(i2, "MAIN_OEUVRE", "Réparation", "1", "1400.00");

        equipement("DG_E3", CENTRE_A, e -> e.statut = "A_REFORMER");
        UUID e4 = equipement("DG_E4", CENTRE_A, e -> {
            e.statut = "REFORME";
            e.prix = new BigDecimal("5000.00");
        });
        ligne(intervention("DG_I4", e4, CENTRE_A, x -> x.debut = le("2026-04-01T08:00:00Z")), "PIECE", "Carte", "1", "99999.00");
        UUID e5 = equipement("DG_E5", CENTRE_A, e -> e.statut = "HORS_SERVICE");
        equipement("DG_E6", CENTRE_A, e -> e.statut = "DESACTIF");
        equipement("DG_E7_SUPPRIME", CENTRE_A, e -> e.supprimeLe = le("2026-02-01T00:00:00Z"));
        int cout = 10;
        for (String label : List.of("DG_E9", "DG_E10", "DG_E11", "DG_E12")) {
            UUID e = equipement(label, CENTRE_A);
            UUID i = intervention("DGI_" + label, e, CENTRE_A, x -> x.debut = le("2026-05-02T08:00:00Z"));
            ligne(i, "PIECE", "Pièce " + label, "1", cout + ".00");
            cout = cout == 10 ? 20 : cout == 20 ? 30 : 5;
        }
        UUID planifiee = intervention("DG_I_PLANIFIEE", e1, CENTRE_A, x -> {
            x.statut = "PLANIFIEE";
            x.debut = le("2026-04-10T08:00:00Z");
            x.fin = null;
        });
        ligne(planifiee, "PIECE", "Prévue", "1", "777.00");
        patientSur(CENTRE_A, e2, "DG-P1");
        patientSur(CENTRE_A, e2, "DG-P2");
        patientSur(CENTRE_A, e5, "DG-P3");
        patientSur(CENTRE_A, e1, "DG-P4");
        patientSur(CENTRE_A, e4, "DG-P5");

        // ---- centre B : bornes de la période sur un équipement sans prix
        UUID eb = equipement("DG_EB", CENTRE_B);
        String[][] bornes = {{"2025-12-31T23:59:00Z", "1.00"}, {"2026-01-01T00:00:00Z", "2.00"},
                {"2026-06-30T23:59:00Z", "4.00"}, {"2026-07-01T00:00:00Z", "8.00"}};
        int n = 0;
        for (String[] b : bornes) {
            UUID i = intervention("DG_IB_" + (n++), eb, CENTRE_B, x -> {
                x.debut = le(b[0]);
                x.fin = le(b[0]).plusMinutes(30);
                x.creation = le(b[0]).minusHours(1);
            });
            ligne(i, "PIECE", "Borne " + b[0], "1", b[1]);
        }

        // ---- centre C (autre société)
        UUID ec = equipement("DG_EC", CENTRE_C, e -> e.prix = new BigDecimal("100.00"));
        ligne(intervention("DG_IC", ec, CENTRE_C, x -> x.debut = le("2026-03-10T08:00:00Z")), "PIECE", "Autre société", "1", "88888.00");
    }

    private RequestPostProcessor directionDe(UUID societe, UUID userId, String username) {
        utilisateur(userId, username, username);
        jdbc.update("INSERT INTO app_user_societe (user_id, societe_id) VALUES (?, ?)", userId, societe);
        return user(new UserPrincipal(userId.toString(), null, "u-" + username, "",
                List.of(new SimpleGrantedAuthority("ROLE_DIRECTION")), true, societe.toString()));
    }

    // ------------------------------------------------------------------ pilotage GMAO

    @Test
    void le_pilotage_gmao_consolide_couts_parc_indisponibilite_reforme_et_alertes_par_centre() throws Exception {
        semerLePilotage();

        Resp r = get(GMAO_DIRECTION + PERIODE, direction());

        assertEquals(200, r.status);
        assertEquals("no-store", r.header("Cache-Control"));
        golden("direction_gmao_periode_fixe", r);
    }

    @Test
    void le_classement_des_equipements_coute_est_limite_a_5_et_complete_par_les_reformes_recommandees() throws Exception {
        semerLePilotage();

        JsonNode centreA = json(get(GMAO_DIRECTION + PERIODE, direction())).get("centres").get(0);

        assertEquals("Centre Pilotage A", centreA.get("nom").asString());
        List<String> top = new ArrayList<>();
        centreA.get("topEquipements").forEach(n -> top.add(n.get("code").asString() + ":"
                + n.get("coutPeriode").decimalValue().stripTrailingZeros().toPlainString()
                + ":" + n.get("reformeRecommandee").asBoolean()));
        assertEquals(List.of("P6-DG_E4:99999:false", "P6-DG_E1:3000:false", "P6-DG_E2:1400:true", "P6-DG_E11:30:false",
                "P6-DG_E10:20:false", "P6-DG_E3:0:true"), top,
                "5 plus coûteux sur la période, puis les réformes recommandées absentes du classement");
    }

    @Test
    void la_periode_est_inclusive_a_ses_deux_bornes_sur_la_date_de_debut_de_l_intervention() throws Exception {
        semerLePilotage();

        JsonNode centreB = json(get(GMAO_DIRECTION + PERIODE, direction())).get("centres").get(1);

        assertEquals("Centre Pilotage B", centreB.get("nom").asString());
        assertEquals(0, new BigDecimal("6.00").compareTo(centreB.get("coutMaintenancePeriode").decimalValue()),
                "du 2026-01-01 00:00 inclus au 2026-07-01 00:00 exclu");
    }

    @Test
    void sans_dates_la_periode_va_du_premier_janvier_a_aujourd_hui_et_les_dates_partielles_se_completent() throws Exception {
        semerLePilotage();
        LocalDate avant = LocalDate.now();
        JsonNode defaut = json(get(GMAO_DIRECTION, direction()));
        LocalDate apres = LocalDate.now();
        assertEquals(LocalDate.of(avant.getYear(), 1, 1).toString(), defaut.get("from").asString());
        assertTrue(defaut.get("to").asString().equals(avant.toString()) || defaut.get("to").asString().equals(apres.toString()));

        JsonNode seulementDu = json(get(GMAO_DIRECTION + "?from=2026-03-01", direction()));
        assertEquals("2026-03-01", seulementDu.get("from").asString());
        assertTrue(seulementDu.get("to").asString().equals(avant.toString()) || seulementDu.get("to").asString().equals(apres.toString()));

        JsonNode seulementAu = json(get(GMAO_DIRECTION + "?to=2025-09-15", direction()));
        assertEquals("2025-01-01", seulementAu.get("from").asString(), "premier janvier de l'année de la date de fin");
        assertEquals("2025-09-15", seulementAu.get("to").asString());
    }

    @Test
    void la_periode_est_controlee_debut_apres_fin_et_au_plus_5_ans() throws Exception {
        semerLePilotage();
        List<String> lignes = new ArrayList<>();
        String[] periodes = {
                "?from=2026-06-30&to=2026-01-01",
                "?from=2026-01-01&to=2026-01-01",
                "?from=2021-01-01&to=2026-01-01",
                "?from=2020-12-31&to=2026-01-01",
                "?from=2020-01-01&to=2025-12-31",
                "?from=2024-02-29&to=2029-02-28",
                "?from=2024-02-29&to=2029-03-01",
                "?from=pas-une-date",
                "?from=2026-13-45",
                "?from=01/01/2026&to=30/06/2026",
                "?from=&to=",
        };
        for (String p : periodes) {
            Resp r = get(GMAO_DIRECTION + p, direction());
            String du = r.status == 200 ? json(r).get("from").asString() : null;
            String au = r.status == 200 ? json(r).get("to").asString() : null;
            if (p.equals("?from=&to=")) {   // dates vides = valeurs par défaut, qui dépendent du jour d'exécution
                LocalDate jour = LocalDate.now();
                du = du.equals(LocalDate.of(jour.getYear(), 1, 1).toString()) ? "<1er janvier de l'année>" : du;
                au = au.equals(jour.toString()) || au.equals(jour.minusDays(1).toString()) ? "<aujourd'hui>" : au;
            }
            lignes.add(p + " -> " + (r.status == 200 ? "200 from=" + du + " to=" + au : resume(r)));
        }
        goldenLignes("direction_gmao_controle_de_la_periode", lignes);
    }

    @Test
    void la_direction_ne_voit_que_les_centres_de_sa_societe() throws Exception {
        semerLePilotage();
        RequestPostProcessor direction2 = directionDe(SOC_2, DIRECTEUR_2, "zt-p6-direction-2");

        JsonNode un = json(get(GMAO_DIRECTION + PERIODE, direction()));
        JsonNode deux = json(get(GMAO_DIRECTION + PERIODE, direction2));

        assertEquals(SOC_1.toString(), un.get("societeId").asString());
        assertEquals(2, un.get("centres").size());
        assertEquals(SOC_2.toString(), deux.get("societeId").asString());
        assertEquals(1, deux.get("centres").size());
        assertEquals(CENTRE_C.toString(), deux.get("centres").get(0).get("centerId").asString());
        assertEquals(0, new BigDecimal("88888.00").compareTo(deux.get("totaux").get("coutMaintenancePeriode").decimalValue()));
        assertEquals(0, new BigDecimal("99999.00").compareTo(un.get("centres").get(0).get("topEquipements").get(0).get("coutPeriode").decimalValue()));
    }

    @Test
    void une_societe_sans_centre_a_un_pilotage_et_une_capacite_vides() throws Exception {
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOC_VIDE, "ZT-P6-S3", "Société sans centre");
        RequestPostProcessor vide = directionDe(SOC_VIDE, DIRECTEUR_VIDE, "zt-p6-direction-vide");

        golden("direction_gmao_societe_sans_centre", get(GMAO_DIRECTION + PERIODE, vide));
        golden("direction_capacite_societe_sans_centre", get(CAPACITE + PERIODE, vide));
    }

    @Test
    void le_pilotage_gmao_est_reserve_a_la_direction_dont_le_compte_est_revérifie_en_base() throws Exception {
        semerLePilotage();
        Map<String, RequestPostProcessor> qui = new LinkedHashMap<>();
        qui.put("DIRECTION de la société", direction());
        qui.put("ADMIN d'un centre de la société", adminA());
        qui.put("SUPERADMIN", roleSeul("SUPERADMIN"));
        qui.put("MEDECIN", roleSeul("MEDECIN"));
        qui.put("TECHNICIEN", roleSeul("TECHNICIEN"));
        qui.put("anonyme", null);
        qui.put("DIRECTION sans société dans le jeton", user(new UserPrincipal(DIRECTEUR.toString(), null, "u-d", "",
                List.of(new SimpleGrantedAuthority("ROLE_DIRECTION")), true, null)));
        qui.put("DIRECTION rattachée à une autre société que celle du jeton", user(new UserPrincipal(DIRECTEUR.toString(), null, "u-d", "",
                List.of(new SimpleGrantedAuthority("ROLE_DIRECTION")), true, SOC_2.toString())));
        qui.put("DIRECTION avec une société inconnue", user(new UserPrincipal(DIRECTEUR.toString(), null, "u-d", "",
                List.of(new SimpleGrantedAuthority("ROLE_DIRECTION")), true, INCONNU.toString())));

        List<String> lignes = new ArrayList<>();
        for (var q : qui.entrySet()) {
            Resp r = get(GMAO_DIRECTION + PERIODE, q.getValue());
            lignes.add(q.getKey() + " -> " + (r.status == 200 ? "200" : resume(r)));
        }
        jdbc.update("UPDATE app_user SET active = FALSE WHERE id = ?", DIRECTEUR);
        lignes.add("DIRECTION dont le compte vient d'être désactivé -> " + resume(get(GMAO_DIRECTION + PERIODE, direction())));
        jdbc.update("UPDATE app_user SET active = TRUE WHERE id = ?", DIRECTEUR);
        jdbc.update("UPDATE societes SET actif = FALSE WHERE id = ?", SOC_1);
        lignes.add("DIRECTION dont la société vient d'être désactivée -> " + resume(get(GMAO_DIRECTION + PERIODE, direction())));
        jdbc.update("UPDATE societes SET actif = TRUE WHERE id = ?", SOC_1);
        jdbc.update("DELETE FROM app_user_societe WHERE user_id = ?", DIRECTEUR);
        lignes.add("DIRECTION détachée de sa société -> " + resume(get(GMAO_DIRECTION + PERIODE, direction())));
        lignes.add("méthode POST -> " + resume(call(org.springframework.http.HttpMethod.POST, GMAO_DIRECTION, null, direction())));
        goldenLignes("direction_gmao_autorisations", lignes);
    }

    @Test
    void le_pilotage_suit_les_ecritures_du_nouveau_module() throws Exception {
        semerLePilotage();
        LocalDate aujourdhui = maintenant.toLocalDate();
        String periode = "?from=" + aujourdhui.minusDays(10) + "&to=" + aujourdhui;
        List<String> lignes = new ArrayList<>();
        lignes.add("départ -> " + synthese(get(GMAO_DIRECTION + periode, direction())));

        UUID e = nom("DG_E1");
        OffsetDateTime debut = maintenant.minusDays(3).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        UUID i = planifierParHttp(e, "EN_SERVICE", INTERVENANT_INTERNE, debut);
        demarrerParHttp(i);
        lignes.add("intervention démarrée -> " + synthese(get(GMAO_DIRECTION + periode, direction())));
        assertEquals(200, post(GMAO + "/interventions/" + i + "/lignes-cout",
                body("type", "PIECE", "libelle", "Pompe", "quantite", new BigDecimal("2"), "prixUnitaire", new BigDecimal("1500.00")),
                adminA()).status);
        terminerParHttp(i, "HORS_SERVICE", debut.plusHours(3));
        lignes.add("intervention terminée, équipement hors service -> " + synthese(get(GMAO_DIRECTION + periode, direction())));
        assertEquals(200, post(GMAO + "/equipements/" + nom("DG_E9") + "/reformer", body("motif", "Obsolète"), reformeur()).status);
        lignes.add("équipement réformé -> " + synthese(get(GMAO_DIRECTION + periode, direction())));
        goldenLignes("direction_gmao_ecritures_du_nouveau_module", lignes);
    }

    /** Totaux et alertes du centre A d'une réponse du pilotage (sans les valeurs dépendant de « maintenant »). */
    private String synthese(Resp r) {
        assertEquals(200, r.status, r.raw);
        JsonNode a = json(r).get("centres").get(0);
        List<String> alertes = new ArrayList<>();
        json(r).get("alertes").forEach(n -> alertes.add(n.get("code").asString() + "=" + n.get("valeur")));
        return "A: équipements=" + a.get("nbEquipements") + " horsService=" + a.get("nbHorsService") + " enMaintenance="
                + a.get("nbEnMaintenance") + " réformés=" + a.get("nbReformes") + " enCours=" + a.get("interventionsEnCours")
                + " coûtPériode=" + a.get("coutMaintenancePeriode") + " patientsSurIndisponible="
                + a.get("patientsSurEquipementIndisponible") + " réformeRecommandée=" + a.get("nbReformeRecommandee")
                + " alertes=" + alertes;
    }

    // ------------------------------------------------------------------ part GMAO de la capacité

    @Test
    void la_capacite_compte_les_generateurs_de_dialyse_installes_non_supprimes_ni_reformes_ni_desactives() throws Exception {
        List<String> statuts = List.of("EN_SERVICE", "EN_SERVICE", "EN_SERVICE", "EN_MAINTENANCE", "HORS_SERVICE", "A_REFORMER", "EN_ATTENTE_PIECE");
        for (int k = 0; k < statuts.size(); k++) {
            String statut = statuts.get(k);
            equipement("CAP_" + k + "_" + statut, CENTRE_A, e -> e.statut = statut);
        }
        equipement("CAP_REFORME", CENTRE_A, e -> e.statut = "REFORME");
        equipement("CAP_DESACTIF", CENTRE_A, e -> e.statut = "DESACTIF");
        equipement("CAP_SUPPRIME", CENTRE_A, e -> e.supprimeLe = le("2026-02-01T00:00:00Z"));
        equipement("CAP_EAU", CENTRE_A, e -> e.type = "STATION_TRAITEMENT_EAU");
        equipement("CAP_FUTUR", CENTRE_A, e -> e.installation = le("2026-07-01T00:00:00Z"));
        equipement("CAP_LIMITE", CENTRE_A, e -> e.installation = le("2026-06-30T23:59:00Z"));
        equipement("CAP_B", CENTRE_B);
        jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?,?,?,?)", UUID.randomUUID(), CENTRE_A, "M", "Matin");
        jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?,?,?,?)", UUID.randomUUID(), CENTRE_A, "A", "Après-midi");
        equipement("CAP_C", CENTRE_C);

        Resp r = get(CAPACITE + PERIODE, direction());

        assertEquals(200, r.status);
        golden("direction_capacite_generateurs", r);

        assertEquals(200, post(GMAO + "/equipements/" + nom("CAP_LIMITE") + "/reformer", body("motif", "Obsolète"), reformeur()).status);
        JsonNode apres = json(get(CAPACITE + PERIODE, direction()));
        assertEquals(json(r).get("total").get("generateurs").asInt() - 1, apres.get("total").get("generateurs").asInt(),
                "un générateur réformé par le nouveau module sort aussitôt de la capacité");
        UUID hs = nom("CAP_B");
        assertEquals(200, post(GMAO + "/equipements/" + hs + "/hors-service", body("raison", "Fuite"), adminB()).status);
        assertEquals(apres.get("total").get("generateurs").asInt(),
                json(get(CAPACITE + PERIODE, direction())).get("total").get("generateurs").asInt(),
                "un générateur hors service reste compté dans la capacité théorique");
    }

    @Test
    void la_capacite_exige_le_role_direction() throws Exception {
        List<String> lignes = new ArrayList<>();
        lignes.add("DIRECTION -> " + get(CAPACITE + PERIODE, direction()).status);
        lignes.add("ADMIN -> " + resume(get(CAPACITE + PERIODE, adminA())));
        lignes.add("anonyme -> " + resume(get(CAPACITE + PERIODE, null)));
        lignes.add("période inversée -> " + resume(get(CAPACITE + "?from=2026-06-30&to=2026-01-01", direction())));
        lignes.add("période de plus de 5 ans -> " + resume(get(CAPACITE + "?from=2020-12-31&to=2026-01-01", direction())));
        goldenLignes("direction_capacite_autorisations_et_periode", lignes);
    }
}

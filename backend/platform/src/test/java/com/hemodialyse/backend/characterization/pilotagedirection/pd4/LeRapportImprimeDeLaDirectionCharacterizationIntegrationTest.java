package com.hemodialyse.backend.characterization.pilotagedirection.pd4;

import com.hemodialyse.backend.characterization.pilotagedirection.PilotageDirectionCharacterizationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PD4 : rapport imprimé de la direction du LEGACY ({@code GET /api/v1/direction/report}, {@code GET /snapshots/{mois}/report},
 * {@code DirectionReportPdfService}, {@code SocieteLetterheadPort} → {@code DocumentIdentityProvider}, rendu {@code openhtmltopdf}) : en-tête et pied
 * de page de la société, toutes les sections, effectifs masqués conservés, rapport mensuel depuis l'instantané figé (y compris sans répartition),
 * bornes et droits. Observé par le TEXTE extrait du PDF (jamais les octets) ; dépend des polices du poste comme DM7/DM8. Rien n'est corrigé.
 */
class LeRapportImprimeDeLaDirectionCharacterizationIntegrationTest extends PilotageDirectionCharacterizationSupport {

    @Override
    protected String slice() {
        return "pd4";
    }

    @Override
    protected String dossier() {
        return "rapport";
    }

    private Map<String, Object> rapport(MockHttpServletResponse r) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", r.getStatus());
        m.put("contentType", r.getContentType());
        m.put("cacheControl", r.getHeader("Cache-Control"));
        m.put("contentDisposition", r.getHeader("Content-Disposition"));
        byte[] corps = r.getContentAsByteArray();
        if (r.getStatus() == 200) {
            m.put("sections", sections(texte(corps)));
        } else {
            m.put("corps", corps.length == 0 ? "" : new String(corps, java.nio.charset.StandardCharsets.UTF_8));
        }
        return m;
    }

    @Test
    void le_rapport_en_direct_porte_l_en_tete_de_la_societe_et_toutes_les_statistiques_masquees_comprises_nouveau_code() throws Exception {
        jeuComplet();
        for (int i = 0; i < 5; i++) {
            analyseEnBase(CENTRE_A, patientPd(CENTRE_A, "K" + i), "K_" + i, fixe(10), "kt_v_mensuel", i < 3 ? "1.30" : "1.00", "hb_g_dl", "10.8");
        }
        article(CENTRE_A, "ART", "2", "10", "100.00");
        fixe(-1);
        fixe(-101);
        // KB-DIR-01 / KB-MED-104 corriges le 2026-11-09 (L1) : « Total société » et la caisse sans code impriment « < seuil » des qu'un centre est masque.
        arbrePd("rapport_en_direct_nouveau_code", rapport(binaire(DIRECTION + "/report?from=" + fixe(0) + "&to=" + fixe(100), direction())));
    }

    @Test
    void le_rapport_mensuel_est_rendu_depuis_l_instantane_fige_meme_sans_repartition() throws Exception {
        YearMonth m1 = mois(-1);
        nommerLaPeriodePrecedenteDe(m1, "<PERIODE_AVANT_M-1>");
        for (int i = 1; i <= 5; i++) patientPd(CENTRE_A, "A" + i);
        doPost(DIRECTION + "/snapshots/" + m1, "", direction());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 rapport mensuel (titre, nom de fichier, periode = le mois, horodatage de l'instantane)", rapport(binaire(DIRECTION + "/snapshots/" + m1 + "/report", direction())));
        jdbc.update("UPDATE direction_snapshot SET payload = ? WHERE societe_id = ? AND mois = ?",
                "{\"overview\":{\"societeNom\":\"Ancien\",\"from\":\"" + m1.atDay(1) + "\",\"to\":\"" + m1.atEndOfMonth() + "\",\"seuilAnonymat\":5,"
                        + "\"totaux\":{\"patients\":null,\"seances\":3,\"factures\":1,\"caHt\":1000.00,\"caTtc\":1190.00,\"encaisse\":600.00,"
                        + "\"resteARecouvrer\":590.00,\"tauxEncaissement\":50.4},\"centres\":[],\"mensuel\":[]},\"indicators\":{\"centres\":[],"
                        + "\"totaux\":{\"clinique\":{},\"stock\":{}},\"alertes\":[{\"centre\":\"Centre A\",\"code\":\"LOTS_PERIMES\",\"severity\":\"CRITICAL\",\"valeur\":2},"
                        + "{\"centre\":\"Centre A\",\"code\":\"INCONNU_DU_LEGACY\",\"severity\":\"WARNING\",\"valeur\":null}]}}", SOCIETE, m1.toString());
        m.put("02 instantane ancien sans 'breakdown' : sections de repartition absentes, alertes libellees (code inconnu rendu brut), totaux masques « < seuil »",
                rapport(binaire(DIRECTION + "/snapshots/" + m1 + "/report", direction())));
        arbrePd("rapport_mensuel", m);
    }

    /** KB-DIR-01 corrige (L1, 2026-11-09) : le rapport d'un instantane ANCIEN aux totaux bruts imprime « < seuil » pour les totaux dont un centre est masque. */
    @Test
    void le_rapport_d_un_instantane_ancien_aux_totaux_bruts_imprime_les_totaux_masques_nouveau_code() throws Exception {
        YearMonth m1 = mois(-1);
        nommerLaPeriodePrecedenteDe(m1, "<PERIODE_AVANT_M-1>");
        doPost(DIRECTION + "/snapshots/" + m1, "", direction());
        jdbc.update("UPDATE direction_snapshot SET payload = ? WHERE societe_id = ? AND mois = ?",
                "{\"overview\":{\"societeNom\":\"Ancien\",\"from\":\"" + m1.atDay(1) + "\",\"to\":\"" + m1.atEndOfMonth() + "\",\"seuilAnonymat\":5,"
                        + "\"totaux\":{\"patients\":15,\"patientsSousKt\":15,\"seances\":6,\"factures\":2,\"caHt\":1000.00,\"caTtc\":1190.00,\"encaisse\":600.00,"
                        + "\"resteARecouvrer\":590.00,\"tauxEncaissement\":50.4},"
                        + "\"centres\":[{\"nom\":\"Centre A\",\"patients\":12,\"patientsSousKt\":15,\"seances\":4,\"factures\":2,\"caHt\":1000.00,\"caTtc\":1190.00,"
                        + "\"encaisse\":600.00,\"resteARecouvrer\":590.00,\"tauxEncaissement\":50.4},"
                        + "{\"nom\":\"Centre B\",\"patients\":null,\"patientsSousKt\":0,\"seances\":2,\"factures\":0,\"caHt\":0,\"caTtc\":0,\"encaisse\":0,"
                        + "\"resteARecouvrer\":0,\"tauxEncaissement\":null}],\"mensuel\":[]},"
                        + "\"indicators\":{\"centres\":[],\"totaux\":{\"clinique\":{},\"stock\":{}},\"alertes\":[]}}", SOCIETE, m1.toString());
        arbrePd("rapport_ancien_instantane_nouveau_code", rapport(binaire(DIRECTION + "/snapshots/" + m1 + "/report", direction())));
    }

    @Test
    void l_en_tete_sans_identite_retombe_sur_la_raison_sociale_et_le_rapport_controle_sa_periode_et_ses_droits() throws Exception {
        YearMonth m1 = mois(-1);
        nommerLaPeriodePrecedenteDe(m1, "<PERIODE_AVANT_M-1>");
        patientPd(CENTRE_AUTRE, "X1");
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> sansIdentite = rapport(binaire(DIRECTION + "/report?from=" + fixe(0) + "&to=" + fixe(100), directionAutre()));
        @SuppressWarnings("unchecked") Map<String, String> sections = (Map<String, String>) sansIdentite.get("sections");
        m.put("01 autre societe sans NIF, adresse ni pied de page : en-tete et pied = raison sociale", sections == null ? sansIdentite : sections.get("en-tete"));
        Map<String, Object> bornes = new LinkedHashMap<>();
        bornes.put("debut apres fin", refus(direction("/report?from=" + fixe(100) + "&to=" + fixe(0))));
        bornes.put("5 ans et 1 jour", refus(direction("/report?from=" + fixe(0) + "&to=" + alias(fixe(0).plusYears(5).plusDays(1), "<D+0 plus 5 ans et 1 jour>"))));
        bornes.put("date illisible", refus(direction("/report?from=hier")));
        m.put("02 bornes de la periode du rapport en direct", bornes);
        Map<String, Map<String, String>> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profils().entrySet()) {
            Map<String, String> parRoute = new LinkedHashMap<>();
            parRoute.put("GET /report", String.valueOf(binaire(DIRECTION + "/report?from=" + fixe(0) + "&to=" + fixe(100), p.getValue()).getStatus()));
            parRoute.put("GET /snapshots", refus(doGet(DIRECTION + "/snapshots", p.getValue())));
            parRoute.put("POST /snapshots/<M-1>", refus(doPost(DIRECTION + "/snapshots/" + m1, "", p.getValue())));
            parRoute.put("GET /alerts/history", refus(doGet(DIRECTION + "/alerts/history", p.getValue())));
            profils.put(p.getKey(), parRoute);
        }
        Map<String, String> anonyme = new LinkedHashMap<>();
        for (String route : List.of("/report", "/snapshots", "/snapshots/" + m1 + "/report", "/alerts/history")) {
            anonyme.put("GET " + route, refus(doGetAnonymous(DIRECTION + route)));
        }
        profils.put("anonyme", anonyme);
        m.put("03 onze profils et l'anonyme sur le rapport, les instantanes et l'historique", profils);
        m.put("04 lignes direction_snapshot creees par la matrice (seule la DIRECTION ecrit)",
                jdbc.queryForList("SELECT mois FROM direction_snapshot WHERE societe_id = ?", String.class, SOCIETE));
        arbrePd("en_tete_et_bornes_nouveau_code", m);
    }
}

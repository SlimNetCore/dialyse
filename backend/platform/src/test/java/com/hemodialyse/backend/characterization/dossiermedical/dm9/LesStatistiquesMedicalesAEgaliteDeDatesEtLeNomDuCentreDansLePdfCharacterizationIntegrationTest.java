package com.hemodialyse.backend.characterization.dossiermedical.dm9;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * DM9 : deux trous des statistiques médicales ({@code PatientStatsQueryService}, plateforme) non couverts par DM8
 * ({@code dossiermedical/dm8/stats}, 10 scénarios : agrégats, périodes, CSV, PDF, recherche, profils, cloisonnement, audit) :
 * (1) deux bilans du MÊME jour : les tendances et le CSV sont triés par la seule date de prélèvement ({@code ORDER BY ra.date_prelevement ASC}),
 * l'ordre des deux lignes n'est pas défini : le golden fige les lignes TRIÉES et leur nombre, jamais l'ordre servi ;
 * (2) le nom du centre, repris en en-tête et pied de page du PDF par {@code DocumentIdentityProvider.decorateHtml}, quand il contient des
 * balises et une esperluette : le golden fige la réponse HTTP et le texte extrait (jamais les octets du PDF).
 */
class LesStatistiquesMedicalesAEgaliteDeDatesEtLeNomDuCentreDansLePdfCharacterizationIntegrationTest extends Dm9CharacterizationSupport {

    @Override
    protected String dossier() {
        return "stats";
    }

    private String medical(UUID patient, UUID centre) {
        return statsApi(patient, "medical") + "?centerId=" + centre;
    }

    private String export(UUID patient, UUID centre, String format) {
        return statsApi(patient, "export") + "?centerId=" + centre + "&format=" + format;
    }

    /** Lignes de tendance en forme lisible et TRIÉES (l'ordre servi n'est pas défini à date égale). */
    private List<String> tendanceTriee(JsonNode trend) {
        TreeSet<String> lignes = new TreeSet<>();
        trend.forEach(n -> lignes.add("hb=" + n.path("HB_G_DL").asString() + " ktv=" + n.path("KT_V_MENSUEL").asString() + " ferritine="
                + n.path("FERRITINE_NG_ML").asString()));
        return new ArrayList<>(lignes);
    }

    private List<String> texte(byte[] pdf) throws Exception {
        List<String> lignes = new ArrayList<>();
        try (PDDocument doc = PDDocument.load(pdf)) {
            for (String ligne : new PDFTextStripper().getText(doc).split("\\R")) {
                if (!ligne.isBlank()) lignes.add(ligne.strip().replaceAll("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}", "<HORODATAGE_MINUTE>"));
            }
        }
        return lignes;
    }

    @Test
    void deux_bilans_du_meme_jour_ne_sont_pas_departages_dans_les_tendances_ni_dans_le_csv() throws Exception {
        UUID patient = patientDm9(CENTRE_A, "PAT_A");
        analyseEnBase(CENTRE_A, patient, "AN_1", fixe(10), "hb_g_dl", "10", "kt_v_mensuel", "1.0", "ferritine_ng_ml", "300", "phosphore_mg_dl", "5.5");
        analyseEnBase(CENTRE_A, patient, "AN_2", fixe(10), "hb_g_dl", "12", "ferritine_ng_ml", "500", "calcium_mg_dl", "9");
        analyseEnBase(CENTRE_A, patient, "AN_3", fixe(20), "hb_g_dl", "14");
        String avant = empreinteLecteurs();
        Resp r = doGet(medical(patient, CENTRE_A), medecinA());
        JsonNode c = corps(r);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 statut", r.status());
        m.put("02 moyennes (hb | ktv | ferritine)", resume(c, "avgHbGDl", "avgKtV", "avgFerritineNgMl"));
        m.put("03 nombre de lignes de tendance", c.path("hbTrend").size());
        m.put("04 lignes de tendance TRIEES (ordre servi non fige : les deux bilans du <D+10> sont indepartageables)", tendanceTriee(c.path("hbTrend")));
        m.put("05 la troisieme ligne servie est celle du <D+20> (seule la date tranche)", c.path("hbTrend").get(2).path("HB_G_DL").asString());
        MockHttpServletResponse csv = binaire(export(patient, CENTRE_A, "csv"), adminA2());
        List<String> lignes = List.of(csv.getContentAsString(StandardCharsets.UTF_8).split("\n", -1));
        m.put("06 csv : statut et type", csv.getStatus() + " " + csv.getContentType());
        m.put("07 csv : en-tete", lignes.get(0));
        m.put("08 csv : nombre de lignes de donnees (plus une ligne vide finale)", lignes.size() - 1);
        m.put("09 csv : lignes de donnees TRIEES", new TreeSet<>(lignes.subList(1, lignes.size() - 1)));
        m.put("10 csv : la troisieme ligne servie est celle du <D+20>", lignes.get(3));
        m.put("11 aucune ecriture", empreinteLecteurs().equals(avant));
        arbre("egalite_de_dates", m);
    }

    @Test
    void le_nom_du_centre_avec_balises_et_esperluette_est_repris_dans_le_pdf() throws Exception {
        UUID patient = patientDm9(CENTRE_A, "PAT_A");
        analyseEnBase(CENTRE_A, patient, "AN_1", fixe(10), "hb_g_dl", "10", "kt_v_mensuel", "1.0", "ferritine_ng_ml", "300");
        var existants = jdbc.queryForList("SELECT name FROM centers WHERE id = ?", CENTRE_A);
        String nom = "Centre <b>DM9</b> & Fils";
        if (existants.isEmpty()) {
            jdbc.update("INSERT INTO centers (id, code, name, actif) VALUES (?,?,?,TRUE)", CENTRE_A, "ZT-DM9-A", nom);
        } else {
            jdbc.update("UPDATE centers SET name = ? WHERE id = ?", nom, CENTRE_A);
        }
        try {
            MockHttpServletResponse r = binaire(export(patient, CENTRE_A, "pdf"), adminA2());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("01 nom du centre en base", nom);
            m.put("02 statut", r.getStatus());
            m.put("03 type", String.valueOf(r.getContentType()));
            if (r.getStatus() == 200) {
                m.put("04 signature du fichier", new String(r.getContentAsByteArray(), 0, 5, StandardCharsets.ISO_8859_1));
                m.put("05 texte", texte(r.getContentAsByteArray()));
            } else {
                m.put("04 refus", refusBrut(r.getStatus(), r.getContentAsString(StandardCharsets.UTF_8)));
            }
            arbre("nom_du_centre", m);
        } finally {
            if (existants.isEmpty()) {
                jdbc.update("DELETE FROM centers WHERE id = ?", CENTRE_A);
            } else {
                jdbc.update("UPDATE centers SET name = ? WHERE id = ?", existants.get(0).get("NAME"), CENTRE_A);
            }
        }
    }
}

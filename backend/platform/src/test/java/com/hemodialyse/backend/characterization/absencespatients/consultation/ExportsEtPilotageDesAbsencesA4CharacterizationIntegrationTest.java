package com.hemodialyse.backend.characterization.absencespatients.consultation;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Complement de la slice A4 (lectures publiees) : ce que A1 ne verrouillait pas dans les lectures de {@code absence_patient}
 * hors suivi. Exports PDF et XLSX du tableau de bord des seances (le chiffre d'absences y vient de la meme requete), format
 * d'export inconnu, mois futur (le futur est compte en absences), controle de la periode de la Direction, isolation entre
 * societes, centre inactif et societe sans centre. {@code /seances/calendar} ne lit PAS la table (jours feries et fermetures
 * seulement) : rien a verrouiller pour les absences. Mars 2026 entier (ancre) et mois 2099 : independants du jour d'execution.
 */
class ExportsEtPilotageDesAbsencesA4CharacterizationIntegrationTest extends ConsultationAbsencesCharacterizationSupport {

    private static final UUID SOC_2 = nom("SOCIETE_2", 0x5002);
    private static final UUID CENTRE_C = nom("CENTRE_C", 0xc1);
    private static final UUID CENTRE_INACTIF = nom("CENTRE_INACTIF", 0xc2);
    private static final UUID DIRECTEUR_2 = nom("DIRECTEUR_2", 0xd002);
    private static final UUID P_CENTRE_C = nom("P_CENTRE_C", 0xc101);

    @Override
    protected String theme() {
        return "lectures-publiees-a4";
    }

    @AfterEach
    void nettoyerSociete2() {
        for (UUID c : List.of(CENTRE_C, CENTRE_INACTIF)) {
            jdbc.update("DELETE FROM absence_patient WHERE center_id = ?", c);
            jdbc.update("DELETE FROM seances WHERE center_id = ?", c);
            jdbc.update("DELETE FROM patients WHERE center_id = ?", c);
        }
        jdbc.update("DELETE FROM centers WHERE id IN (?, ?)", CENTRE_C, CENTRE_INACTIF);
        jdbc.update("DELETE FROM app_user_societe WHERE user_id = ?", DIRECTEUR_2);
        jdbc.update("DELETE FROM app_user WHERE id = ?", DIRECTEUR_2);
        jdbc.update("DELETE FROM societes WHERE id = ?", SOC_2);
    }

    private void jeuDuCentreA() {
        int r = 0;
        for (int j = 1; j <= 3; j++) {
            abs(++r, P_AMRANI, ancre(j)).statut("JUSTIFIEE").motif("MALADIE").valeur("Forfait HD", "119.00", "19.00", "100.00").poser();
        }
        abs(++r, P_BELKACEM, ancre(4)).valeur("Forfait HD", "119.00", "19.00", "100.00").poser();
        abs(++r, P_CHIKHI, ancre(5)).statut("RATTRAPEE").motif("VOYAGE").rattrapee(ancre(12)).poser();
        abs(++r, P_DAHMANI, ancre(6)).statut("ANNULEE").commentaire("Erreur").poser();
        seance(P_AMRANI, CENTRE_A, ancre(8), "VALIDEE");
        seance(P_BELKACEM, CENTRE_A, ancre(8), "SIGNEE");
    }

    // ------------------------------------------------------------------ exports du tableau de bord

    private MockHttpServletResponse binaire(String path, RequestPostProcessor who) throws Exception {
        return mvc.perform(get(java.net.URI.create(path)).with(who)).andReturn().getResponse();
    }

    private String exportMars(String format) {
        return SEANCES + "/dashboard/export?centerId=" + CENTRE_A + "&year=2026&month=3" + format;
    }

    @Test
    void l_export_xlsx_du_tableau_de_bord_reprend_le_nombre_d_absences_non_annulees() throws Exception {
        jeuDuCentreA();
        MockHttpServletResponse r = binaire(exportMars("&format=XLSX"), adminA());

        ObjectNode o = mapper.createObjectNode();
        o.put("status", r.getStatus());
        o.put("contentType", r.getContentType());
        o.put("contentDisposition", r.getHeader("Content-Disposition"));
        ObjectNode feuilles = o.putObject("feuilles");
        try (XSSFWorkbook classeur = new XSSFWorkbook(new ByteArrayInputStream(r.getContentAsByteArray()))) {
            DataFormatter fmt = new DataFormatter();
            for (Sheet feuille : classeur) {
                var lignes = feuilles.putArray(feuille.getSheetName());
                for (Row ligne : feuille) {
                    lignes.add(fmt.formatCellValue(ligne.getCell(0)) + " | " + fmt.formatCellValue(ligne.getCell(1)));
                }
            }
        }
        goldenNode("export_xlsx_mars", o);
    }

    @Test
    void l_export_pdf_du_tableau_de_bord_affiche_les_absences_et_les_presences_du_mois() throws Exception {
        jeuDuCentreA();
        MockHttpServletResponse r = binaire(exportMars("&format=pdf"), adminA());

        ObjectNode o = mapper.createObjectNode();
        o.put("status", r.getStatus());
        o.put("contentType", r.getContentType());
        o.put("contentDisposition", r.getHeader("Content-Disposition"));
        try (PDDocument pdf = PDDocument.load(r.getContentAsByteArray())) {
            var lignes = o.putArray("texte");
            for (String ligne : new PDFTextStripper().getText(pdf).split("\\R")) {
                if (!ligne.isBlank()) lignes.add(ligne.strip());
            }
        }
        goldenNode("export_pdf_mars", o);
    }

    @Test
    void un_format_d_export_inconnu_est_refuse_et_les_formats_connus_sont_insensibles_a_la_casse() throws Exception {
        // KB-ABS-36 corrige : un format inconnu repond 400 (ancien comportement : retombait sur le CSV, voir _ecarts-legacy)
        jeuDuCentreA();
        Map<String, Object> cas = new LinkedHashMap<>();
        // « &format= » vide : Spring le remplace par la valeur par defaut (csv), comme le parametre absent
        for (String format : List.of("", "&format=", "&format=txt", "&format=CSV", "&format=Pdf")) {
            MockHttpServletResponse r = binaire(exportMars(format), adminA());
            cas.put("format" + format, r.getStatus() + " " + r.getContentType() + " " + r.getHeader("Content-Disposition"));
        }
        goldenNode("export_formats_nouveau_code", mapper.valueToTree(cas));
    }

    // ------------------------------------------------------------------ tableau de bord : le futur

    @Test
    void un_mois_futur_ne_compte_aucune_absence_et_garde_une_periode_coherente() throws Exception {
        // KB-ABS-35 corrige : pas d'absence future comptee, periodEnd >= periodStart (ancien comportement : voir _ecarts-legacy)
        int r = 0;
        abs(++r, P_AMRANI, LocalDate.of(2099, 1, 5)).poser();
        abs(++r, P_BELKACEM, LocalDate.of(2099, 1, 6)).statut("RATTRAPEE").motif("VOYAGE").rattrapee(LocalDate.of(2099, 1, 9)).poser();
        abs(++r, P_CHIKHI, LocalDate.of(2099, 1, 7)).statut("ANNULEE").commentaire("Erreur").poser();
        String q = "?centerId=" + CENTRE_A + "&year=2099&month=1";

        JsonNode tableau = json(doGet(SEANCES + "/dashboard" + q, adminA()));
        assertEquals("2099-01-01", tableau.path("absenceDetails").path("periodStart").asString());
        assertEquals("2099-01-01", tableau.path("absenceDetails").path("periodEnd").asString());
        goldenNode("tableau_de_bord_mois_futur_nouveau_code", tableau);

        golden("details_absences_mois_futur_nouveau_code", doGet(SEANCES + "/dashboard/details" + q + "&kind=absence", adminA()));
    }

    // ------------------------------------------------------------------ Direction

    private void societe2(boolean avecCentreInactif) {
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOC_2, "ZT-ABS-S2", "Societe Absences Deux");
        jdbc.update("INSERT INTO app_user (id, username, password_hash, email, full_name, active) VALUES (?,?,?,?,?,TRUE)",
                DIRECTEUR_2, "zt-abs-direction-2", "x", null, "Directeur Deux");
        jdbc.update("INSERT INTO app_user_societe (user_id, societe_id) VALUES (?, ?)", DIRECTEUR_2, SOC_2);
        if (avecCentreInactif) {
            jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", CENTRE_C, "ZT-ABS-C", "Centre Gamma", SOC_2);
            jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,FALSE)", CENTRE_INACTIF, "ZT-ABS-I", "Centre Inactif", SOC_2);
            patient(P_CENTRE_C, CENTRE_C, "ABS-C01", "Gamma", "Nadia");
            int r = 20;
            for (int j = 1; j <= 5; j++) {
                abs(++r, P_CENTRE_C, ancre(j)).centre(CENTRE_C).statut("NON_JUSTIFIEE").motif("NON_JUSTIFIEE").valeur("Forfait C", "119.00", "19.00", "100.00").poser();
            }
            patient(nom("P_INACTIF", 0xc201), CENTRE_INACTIF, "ABS-I01", "Inactif", "Omar");
            for (int j = 1; j <= 5; j++) {
                abs(++r, nom("P_INACTIF", 0xc201), ancre(j)).centre(CENTRE_INACTIF).valeur("Forfait I", "59.50", "19.00", "50.00").poser();
            }
            seance(P_CENTRE_C, CENTRE_C, ancre(8), "VALIDEE");
        }
    }

    private RequestPostProcessor direction2() {
        return user(new com.hemodialyse.backend.infrastructure.security.UserPrincipal(DIRECTEUR_2.toString(), null, "u-direction-2", "",
                List.of(new SimpleGrantedAuthority("ROLE_DIRECTION")), true, SOC_2.toString()));
    }

    @Test
    void la_direction_ne_voit_que_les_centres_de_sa_societe_et_publie_un_centre_inactif_des_cinq_absences() throws Exception {
        jeuDuCentreA();
        societe2(true);
        String periode = "?from=" + ancre(0) + "&to=" + ancre(30);

        golden("direction_societe_1_ignore_la_societe_2", doGet(DIRECTION + "/absences" + periode, direction()));
        golden("direction_societe_2_centre_actif_et_inactif", doGet(DIRECTION + "/absences" + periode, direction2()));
    }

    @Test
    void la_direction_d_une_societe_sans_centre_recoit_une_vue_vide() throws Exception {
        jeuDuCentreA();
        societe2(false);

        golden("direction_societe_sans_centre", doGet(DIRECTION + "/absences?from=" + ancre(0) + "&to=" + ancre(30), direction2()));
    }

    @Test
    void la_direction_refuse_une_periode_inversee_ou_de_plus_de_cinq_ans_et_accepte_cinq_ans_pile() throws Exception {
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("debut apres fin", doGet(DIRECTION + "/absences?from=" + ancre(10) + "&to=" + ancre(9), direction()));
        LocalDate debut = LocalDate.of(2020, 3, 1);
        cas.put("cinq ans pile", doGet(DIRECTION + "/absences?from=" + debut + "&to=" + debut.plusYears(5), direction()).status());
        cas.put("cinq ans et un jour", doGet(DIRECTION + "/absences?from=" + debut + "&to=" + debut.plusYears(5).plusDays(1), direction()));
        cas.put("date illisible", doGet(DIRECTION + "/absences?from=hier", direction()).status());
        // les autres routes de la Direction gardent la reponse du gestionnaire commun (500)
        cas.put("date illisible sur une autre route", doGet(DIRECTION + "/overview?from=hier", direction()).status());
        Map<String, Object> sortie = new LinkedHashMap<>();
        cas.forEach((k, v) -> sortie.put(k, v instanceof Resp resp ? resp.status() + " " + resp.raw() : v));
        // KB-ABS-33 corrige : une date illisible repond 400 (ancien comportement : 500, voir _ecarts-legacy)
        goldenNode("direction_controle_de_la_periode_nouveau_code", mapper.valueToTree(sortie));
        assertFalse(sortie.isEmpty());
        assertEquals(5, sortie.size());
    }

    @Test
    void la_direction_ne_compte_aucune_absence_posterieure_a_aujourd_hui_meme_avec_une_fin_de_periode_dans_le_futur() throws Exception {
        // KB-ABS-35 etendu a la Direction (decision 23/10) : le legacy comptait ces cinq absences de 2099 (voir _ecarts-legacy)
        for (int j = 5; j <= 9; j++) {
            abs(j, P_AMRANI, LocalDate.of(2099, 1, j)).statut("NON_JUSTIFIEE").motif("NON_JUSTIFIEE").poser();
        }

        golden("direction_periode_future_nouveau_code", doGet(DIRECTION + "/absences?from=2099-01-01&to=2099-12-31", direction()));
    }
}

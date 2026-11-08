package com.hemodialyse.backend.characterization.seance.se7b;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SE7b : l'export du tableau de bord mensuel ({@code GET /api/v1/seances/dashboard/export}, CSV par défaut, PDF, XLSX). A4 verrouille
 * déjà un export XLSX et un export PDF de mars 2026, les formats inconnus et la casse du format, l'export du centre B lu par
 * l'administrateur du centre A. Ici : le CSV complet (en-têtes, corps, encodage), les trois formats sur le jeu nominal de janvier 2026
 * (répartitions non nulles), le nom de fichier selon le mois, l'export d'un mois vide, la fuite éventuelle de données nominatives.
 * Décrit le comportement ACTUEL.
 */
class LExportDuTableauDeBordMensuelDesSeancesCharacterizationIntegrationTest extends Se7bCharacterizationSupport {

    @Override
    protected String dossier() {
        return "export";
    }

    @Test
    void l_export_csv_par_defaut_reprend_les_compteurs_et_les_repartitions_du_mois() throws Exception {
        jeuNominalDeJanvier();
        MockHttpServletResponse r = exporter(CENTRE_A, 2026, 1, null, infirmierA());
        Map<String, Object> m = new LinkedHashMap<>(enveloppe(r));
        m.put("lignes", lignesDuTexte(r.getContentAsString(StandardCharsets.UTF_8)));
        m.put("memes octets en csv explicite", java.util.Arrays.equals(r.getContentAsByteArray(),
                exporter(CENTRE_A, 2026, 1, "csv", infirmierA()).getContentAsByteArray()));
        arbre("csv", m);
    }

    @Test
    void l_export_xlsx_et_l_export_pdf_reprennent_les_memes_chiffres_que_le_csv() throws Exception {
        jeuNominalDeJanvier();
        MockHttpServletResponse x = exporter(CENTRE_A, 2026, 1, "xlsx", medecinA());
        MockHttpServletResponse p = exporter(CENTRE_A, 2026, 1, "pdf", secretaireA());
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> xlsx = new LinkedHashMap<>(enveloppe(x));
        xlsx.put("feuilles", feuillesDuClasseur(x.getContentAsByteArray()));
        m.put("xlsx", xlsx);
        Map<String, Object> pdf = new LinkedHashMap<>(enveloppe(p));
        pdf.put("debut du fichier", new String(p.getContentAsByteArray(), 0, 5, StandardCharsets.ISO_8859_1));
        pdf.put("texte", texteDuPdf(p.getContentAsByteArray()));
        m.put("pdf", pdf);
        arbre("xlsx_et_pdf", m);
    }

    @Test
    void le_nom_du_fichier_porte_l_annee_et_le_mois_sur_deux_chiffres_et_l_export_d_un_mois_vide_est_a_zero() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int mois : List.of(1, 9, 12)) {
            for (String format : List.of("csv", "pdf", "xlsx")) {
                MockHttpServletResponse r = exporter(CENTRE_B, 2025, mois, format, infirmierB());
                m.put(format + " " + mois + "/2025", r.getStatus() + " " + r.getContentType() + " " + r.getHeader("Content-Disposition"));
            }
        }
        m.put("csv du centre B sans donnee (decembre 2025)", lignesDuTexte(exporter(CENTRE_B, 2025, 12, "csv", infirmierB())
                .getContentAsString(StandardCharsets.UTF_8)));
        m.put("pdf du centre B sans donnee (decembre 2025)", texteDuPdf(exporter(CENTRE_B, 2025, 12, "pdf", infirmierB()).getContentAsByteArray()));
        m.put("xlsx du centre B sans donnee (decembre 2025)", feuillesDuClasseur(exporter(CENTRE_B, 2025, 12, "xlsx", infirmierB()).getContentAsByteArray()));
        arbre("noms_de_fichier_et_mois_vide", m);
    }

    @Test
    void l_export_ne_contient_aucune_donnee_nominative_ni_montant_meme_avec_des_patients_et_des_absences_valorisees() throws Exception {
        jeuNominalDeJanvier();
        jdbc.update("UPDATE absence_patient SET prix_ttc = 11900.00, montant_ht = 10000.00, taux_tva = 19.00, forfait_libelle = 'Forfait HD fictif' "
                + "WHERE center_id = ?", CENTRE_A);
        MockHttpServletResponse csv = exporter(CENTRE_A, 2026, 1, "csv", adminA());
        String texte = csv.getContentAsString(StandardCharsets.UTF_8);
        MockHttpServletResponse xlsx = exporter(CENTRE_A, 2026, 1, "xlsx", adminA());
        MockHttpServletResponse pdf = exporter(CENTRE_A, 2026, 1, "pdf", adminA());
        String tout = texte + feuillesDuClasseur(xlsx.getContentAsByteArray()) + texteDuPdf(pdf.getContentAsByteArray());
        Map<String, Object> m = new LinkedHashMap<>();
        for (String indice : List.of("Nom E0", "Pre E0", "Forfait", "11900", "10000", "19.00", "SE1-E0", "ASS-SE1")) {
            m.put("contient [" + indice + "]", tout.contains(indice));
        }
        Map<String, Object> tableau = new LinkedHashMap<>();
        tableau.put("cles du corps", mapper.readTree(tableau(CENTRE_A, 2026, 1, adminA()).raw()).propertyNames().stream().toList());
        // absenceDetails est construit par Map.of : l'ordre des cles change d'une JVM a l'autre, il est donc trie ici
        tableau.put("cles de absenceDetails (triees)", mapper.readTree(tableau(CENTRE_A, 2026, 1, adminA()).raw()).path("absenceDetails")
                .propertyNames().stream().sorted().toList());
        m.put("tableau de bord", tableau);
        arbre("aucune_donnee_nominative", m);
    }
}

package com.hemodialyse.backend.contexts.documents.infrastructure.rendu;

import com.hemodialyse.backend.contexts.documents.application.ConnexionDeRendu;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DOC5 (correction DOC5f) : chaque gabarit converti en jeu de données (sans requête) rend ses lignes sur le chemin « centre sans modèle »
 * du moteur ({@code rendreSansModele}, utilisé par {@code rendreParTypeSansProvisionner}), sans ouvrir de connexion. Avant la correction
 * ce chemin ne transformait pas {@code LIGNES} en source de données : le gabarit sortait une page blanche.
 */
class RenduSansModeleDesGabaritsConvertisTest {

    private static final ConnexionDeRendu AUCUNE_CONNEXION = new ConnexionDeRendu() {
        @Override
        public <T> T avecLaConnexion(Travail<T> travail) {
            throw new AssertionError("un gabarit qui reçoit ses lignes n'ouvre aucune connexion");
        }
    };

    private static Map<String, Object> uneLigneParChamp(String gabarit) throws Exception {
        JasperReport rapport;
        try (InputStream is = new ClassPathResource("reports/" + gabarit + ".jrxml").getInputStream()) {
            rapport = JasperCompileManager.compileReport(is);
        }
        Map<String, Object> ligne = new HashMap<>();
        for (JRField champ : rapport.getFields()) {
            Class<?> type = champ.getValueClass();
            Object valeur;
            if (type == java.sql.Date.class) valeur = java.sql.Date.valueOf("2026-01-02");
            else if (type == BigDecimal.class) valeur = new BigDecimal("12.50");
            else if (type == Integer.class) valeur = 3;
            else if (type == Boolean.class) valeur = Boolean.TRUE;
            else valeur = "MARQUEUR-" + champ.getName();
            ligne.put(champ.getName(), valeur);
        }
        return ligne;
    }

    private static List<String> cellules(byte[] octets) throws Exception {
        List<String> textes = new ArrayList<>();
        try (XSSFWorkbook classeur = new XSSFWorkbook(new ByteArrayInputStream(octets))) {
            DataFormatter format = new DataFormatter();
            for (Sheet feuille : classeur) {
                for (Row ligne : feuille) {
                    for (Cell cellule : ligne) textes.add(format.formatCellValue(cellule));
                }
            }
        }
        return textes;
    }

    @ParameterizedTest
    @ValueSource(strings = {"cahier_dialyse", "bon_intervention", "planning_optimise", "ordonnance", "planning_presence_infirmiers",
            "liste_patients"})
    void un_gabarit_converti_rend_ses_lignes_pour_un_centre_sans_modele(String gabarit) throws Exception {
        var moteur = new MoteurDeRenduJasper(null, new JasperReportService(AUCUNE_CONNEXION), null, null, null, null, null);
        Map<String, Object> parametres = new HashMap<>();
        parametres.put("LIGNES", List.of(uneLigneParChamp(gabarit)));

        byte[] classeur = moteur.rendreSansModele("reports/" + gabarit + ".jrxml", parametres, "EXCEL");

        assertThat(cellules(classeur)).as("cellules du gabarit " + gabarit).anyMatch(texte -> texte.contains("MARQUEUR-"));
    }
}

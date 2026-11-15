package com.hemodialyse.backend.characterization.planification.optimisation;

import com.hemodialyse.backend.characterization.planification.OptimisationCharacterizationSupport;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * DOC5c : filet de rendu du planning proposé par l'optimisation, TOUT le texte du PDF, avant la conversion du gabarit
 * {@code planning_optimise.jrxml} de son SQL embarqué (table {@code planification_calendrier_case} de la Planification) vers un jeu de
 * données fourni par la Planification. Observé par {@code GET /api/v1/planning/optimisations/{id}/impression} : une proposition à deux
 * semaines, trois salles et deux créneaux (lignes écrites dans le désordre, libellés accentués, cases vides, textes sur plusieurs lignes
 * et très longs, plusieurs pages), les lignes d'une AUTRE proposition et d'un AUTRE centre à ne pas imprimer ; puis une proposition à une
 * seule ligne dont les derniers jours sont absents. Le texte doit rester identique après la conversion. DONNÉES FICTIVES.
 */
class PlanningOptimise5cCharacterizationIntegrationTest extends OptimisationCharacterizationSupport {

    private static final LocalDate SEMAINE_1 = LocalDate.of(2026, 11, 8);
    private static final LocalDate SEMAINE_2 = LocalDate.of(2026, 11, 15);
    private static final String[] JOURS = {"Dim", "Lun", "Mar", "Mer", "Jeu", "Ven", "Sam"};

    @Override
    protected String theme() {
        return "p8-optimisation/doc5c";
    }

    private void ligne(UUID run, UUID centre, LocalDate semaine, String salle, int ordreSalle, String creneau, int ordreCreneau,
                       String[] entetes, String[] cellules) {
        Object[] args = new Object[24];
        args[0] = run;
        args[1] = centre;
        args[2] = java.sql.Date.valueOf(semaine);
        args[3] = UUID.nameUUIDFromBytes(("salle-" + salle).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        args[4] = salle;
        args[5] = ordreSalle;
        args[6] = UUID.nameUUIDFromBytes(("creneau-" + creneau).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        args[7] = creneau;
        args[8] = ordreCreneau;
        args[9] = "[]";
        System.arraycopy(entetes, 0, args, 10, 7);
        System.arraycopy(cellules, 0, args, 17, 7);
        jdbc.update("INSERT INTO planification_calendrier_case (run_id, center_id, semaine_debut, salle_id, salle_nom, salle_ordre, "
                + "creneau_id, creneau_libelle, creneau_ordre, jours, entete_0, entete_1, entete_2, entete_3, entete_4, entete_5, "
                + "entete_6, cell_0, cell_1, cell_2, cell_3, cell_4, cell_5, cell_6) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", args);
    }

    private static String[] entetes(LocalDate semaine) {
        String[] e = new String[7];
        for (int i = 0; i < 7; i++) {
            LocalDate d = semaine.plusDays(i);
            e[i] = JOURS[i] + " " + String.format("%02d/%02d", d.getDayOfMonth(), d.getMonthValue());
        }
        return e;
    }

    private static String[] cellules(String prefixe) {
        String[] c = new String[7];
        c[0] = "- " + prefixe + " Alpha - G1\n- " + prefixe + " Éclair (R)\nInfirmiers :\n  Amrani S.\n2 patient(s), 1 inf. affecté(s)";
        c[1] = "";
        c[2] = "FERMÉ\nMaintenance du générateur";
        c[3] = "- " + prefixe + " Zoé (dépl.) (temp.)\n1 patient(s), 0 inf. affecté(s) - MANQUE 1";
        c[4] = null;
        c[5] = "- " + prefixe + " Libellé très long qui dépasse largement la colonne de quatre-vingt-seize points de large et qui "
                + "continue encore sur plusieurs lignes pour vérifier l'étirement de la cellule\n3 inf. en trop";
        c[6] = "FERMÉ";
        return c;
    }

    private String textePdf(UUID run) throws Exception {
        // le modèle du centre est repris au défaut (PDF, actif) : d'autres filets de l'impression le désactivent ou changent son format
        jdbc.update("DELETE FROM modele_document WHERE center_id = ? AND UPPER(type_document) = 'PLANNING_OPTIMISE'", CENTRE_A);
        var reponse = mvc.perform(get(URI.create(OPTIMISATIONS + "/" + run + "/impression")).with(adminA())).andReturn().getResponse();
        assertEquals(200, reponse.getStatus(), reponse.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        try (PDDocument doc = PDDocument.load(reponse.getContentAsByteArray())) {
            return new PDFTextStripper().getText(doc).replace("\r\n", "\n");
        }
    }

    private List<String> lignesDe(String texte) {
        return new ArrayList<>(texte.lines().toList());
    }

    @Test
    void la_proposition_a_deux_semaines_imprime_tout_le_texte_dans_l_ordre_semaine_creneau_salle() throws Exception {
        UUID run = executionEcrite(CENTRE_A, "DOC5C_COMPLET", "TERMINEE", "COMPLET", 5, null);
        UUID autreRun = executionEcrite(CENTRE_A, "DOC5C_AUTRE", "TERMINEE", "COMPLET", 4, null);
        UUID runAutreCentre = executionEcrite(CENTRE_B, "DOC5C_ETRANGER", "TERMINEE", "COMPLET", 3, null);
        // lignes écrites dans le désordre ; ordre attendu : semaine, créneau, salle
        for (LocalDate semaine : List.of(SEMAINE_2, SEMAINE_1)) {
            String s = semaine.equals(SEMAINE_1) ? "S1" : "S2";
            ligne(run, CENTRE_A, semaine, "Salle Étoile", 3, "Soir", 2, entetes(semaine), cellules(s + " soir étoile"));
            ligne(run, CENTRE_A, semaine, "Salle 1", 1, "Matin", 1, entetes(semaine), cellules(s + " matin un"));
            ligne(run, CENTRE_A, semaine, "Salle 10", 2, "Soir", 2, entetes(semaine), cellules(s + " soir dix"));
            ligne(run, CENTRE_A, semaine, "Salle Étoile", 3, "Matin", 1, entetes(semaine), cellules(s + " matin étoile"));
            ligne(run, CENTRE_A, semaine, "Salle 1", 1, "Soir", 2, entetes(semaine), cellules(s + " soir un"));
            ligne(run, CENTRE_A, semaine, "Salle 10", 2, "Matin", 1, entetes(semaine), cellules(s + " matin dix"));
        }
        ligne(autreRun, CENTRE_A, SEMAINE_1, "Salle 1", 1, "Matin", 1, entetes(SEMAINE_1), cellules("AUTRE PROPOSITION"));
        ligne(runAutreCentre, CENTRE_B, SEMAINE_1, "Salle B", 1, "Matin", 1, entetes(SEMAINE_1), cellules("AUTRE CENTRE"));
        // même identifiant d'exécution mais rattaché à un autre centre : à ne pas imprimer pour le centre A
        ligne(run, CENTRE_B, SEMAINE_1, "Salle B", 1, "Matin", 1, entetes(SEMAINE_1), cellules("MEME RUN AUTRE CENTRE"));

        arbre("planning_doc5c_complet_texte", lignesDe(textePdf(run)));
    }

    @Test
    void la_proposition_a_une_seule_ligne_imprime_les_jours_absents_en_blanc() throws Exception {
        UUID run = executionEcrite(CENTRE_A, "DOC5C_MINIMAL", "TERMINEE", "ROULEMENT", 5, null);
        String[] e = entetes(SEMAINE_1);
        e[5] = null;
        e[6] = null;
        String[] c = new String[7];
        c[0] = "FERMÉ";
        c[1] = "- Patient seul\n1 patient(s), 1 inf. affecté(s)";
        arbre("planning_doc5c_une_ligne_texte", lignesDe(textePdf(run, e, c)));
    }

    private String textePdf(UUID run, String[] entetes, String[] cellules) throws Exception {
        ligne(run, CENTRE_A, SEMAINE_1, "Salle unique", 1, "Matin", 1, entetes, cellules);
        return textePdf(run);
    }
}

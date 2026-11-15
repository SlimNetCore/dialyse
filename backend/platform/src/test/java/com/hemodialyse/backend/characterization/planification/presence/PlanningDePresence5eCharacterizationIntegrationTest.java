package com.hemodialyse.backend.characterization.planification.presence;

import com.hemodialyse.backend.characterization.planification.JourSemaine;
import com.hemodialyse.backend.characterization.planification.PresenceCharacterizationSupport;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DOC5e : filet de rendu du planning de présence des infirmiers, TOUT le texte du PDF et le nombre de pages, avant la conversion du
 * gabarit {@code planning_presence_infirmiers.jrxml} de son SQL embarqué (tables {@code infirmier}, {@code infirmier_affectation},
 * {@code infirmier_absence}, {@code infirmier_remplacement} de la Planification, {@code salle} et {@code position_creneau} d'Organisation)
 * vers un jeu de données fourni par la Planification. Observé par {@code GET /api/v1/infirmiers/presence/imprimer} : une semaine riche
 * (plusieurs pages, salles et créneaux à l'ordre piégeux, noms accentués ou sans prénom, deux affectations d'un même infirmier, absences
 * aux bornes de la semaine, remplaçants dont un inactif, fiche inactive, données d'un autre centre) puis des champs {@code jours} écrits de
 * façon inhabituelle (recherche par sous-chaîne sensible à la casse). DONNÉES FICTIVES ; le texte doit rester identique après la conversion.
 */
class PlanningDePresence5eCharacterizationIntegrationTest extends PresenceCharacterizationSupport {

    @Override
    protected String theme() {
        return "p4-presence/doc5e";
    }

    private Map<String, Object> texteDuPdf(String requete) throws Exception {
        MockHttpServletResponse r = brute(PRESENCE + requete, adminA());
        assertEquals(200, r.getStatus(), r.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        Map<String, Object> m = new LinkedHashMap<>();
        try (PDDocument doc = PDDocument.load(r.getContentAsByteArray())) {
            m.put("pages", doc.getNumberOfPages());
            List<String> lignes = new ArrayList<>();
            for (String l : new PDFTextStripper().getText(doc).replace("\r\n", "\n").lines().toList()) {
                lignes.add(datesFr(l));
            }
            m.put("texte", lignes);
        }
        return m;
    }

    @Test
    void la_semaine_riche_imprime_tout_le_texte_dans_l_ordre_jour_creneau_salle_situation_infirmier() throws Exception {
        decor();
        semaine(0);
        semaine(1);
        semaine(2);
        UUID salle10 = salle(CENTRE_A, "SALLE_10", "Salle 10", "NON");
        UUID salleEtoile = salle(CENTRE_A, "SALLE_ETOILE", "Salle Étoile", "NON");
        UUID soir = creneau(CENTRE_A, "CRENEAU_SOIR", "Soir");
        UUID amrani = infirmierAffecte(CENTRE_A, "INF_AMRANI", "Amrani", "Sara", true, salle1, matin, tousLesJours());
        affectation(CENTRE_A, "AFF_AMRANI_SOIR", amrani, salle2, soir, "LUNDI");
        UUID eloi = infirmierAffecte(CENTRE_A, "INF_ELOI", "Éloi", "Émile", false, salle1, matin, tousLesJours());
        UUID zahi = infirmierAffecte(CENTRE_A, "INF_ZAHI", "Zahi", null, false, salle2, apresMidi, "LUNDI,MARDI,MERCREDI,JEUDI");
        infirmierAffecte(CENTRE_A, "INF_FONTAINE", "de la Fontaine", "Jean", false, salle10, soir, tousLesJours());
        UUID dupont = infirmierAffecte(CENTRE_A, "INF_DUPONT", "Dupont", "Léa", false, salleEtoile, matin, "DIMANCHE,SAMEDI");
        UUID benali = infirmierAffecte(CENTRE_A, "INF_BENALI", "Benali", "Karim", false, salle2, matin, tousLesJours());
        infirmierAffecte(CENTRE_A, "INF_CHERIF", "Cherif", "Lina", true, salleIsolement, apresMidi, tousLesJours());
        UUID inactif = fiche(CENTRE_A, "INF_INACTIF", "INF_INACTIF", "Inactif", "Ines", false);
        jdbc.update("UPDATE infirmier SET actif = FALSE WHERE id = ?", inactif);
        affectation(CENTRE_A, "AFF_INACTIF", inactif, salle1, matin, tousLesJours());
        UUID roux = inf(CENTRE_A, "INF_ROUX", "Roux", "Paul", false);

        absence(CENTRE_A, "ABS_AMRANI", amrani, semaine(1, JourSemaine.DIMANCHE), semaine(1, JourSemaine.DIMANCHE), "CONGE", null);
        absence(CENTRE_A, "ABS_BENALI", benali, semaine(1, JourSemaine.MARDI), semaine(1, JourSemaine.JEUDI), "MALADIE", "Grippe");
        absence(CENTRE_A, "ABS_DUPONT", dupont, semaine(1, JourSemaine.SAMEDI), semaine(2, JourSemaine.LUNDI), "CONGE", null);
        absence(CENTRE_A, "ABS_ZAHI", zahi, semaine(0, JourSemaine.LUNDI), semaine(1, JourSemaine.LUNDI), "CONGE", null);
        // hors de la semaine imprimée : ne change rien
        absence(CENTRE_A, "ABS_ELOI", eloi, semaine(2, JourSemaine.MARDI), semaine(2, JourSemaine.JEUDI), "CONGE", null);

        remplacement(CENTRE_A, "REMPL_ROUX_LUN", semaine(1, JourSemaine.LUNDI), salle1, matin, roux, amrani);
        remplacement(CENTRE_A, "REMPL_ROUX_MER", semaine(1, JourSemaine.MERCREDI), salle2, apresMidi, roux, zahi);
        remplacement(CENTRE_A, "REMPL_INACTIF_JEU", semaine(1, JourSemaine.JEUDI), salle1, matin, inactif, benali);
        remplacement(CENTRE_A, "REMPL_AMRANI_VEN", semaine(1, JourSemaine.VENDREDI), salle10, soir, amrani, null);
        remplacement(CENTRE_A, "REMPL_ROUX_HORS", semaine(2, JourSemaine.LUNDI), salle1, matin, roux, amrani);

        // un autre centre : rien ne doit apparaître
        UUID etranger = infirmierAffecte(CENTRE_B, "INF_ETRANGER", "Etranger", "Autre", false, salleB, creneauB, tousLesJours());
        absence(CENTRE_B, "ABS_ETRANGER", etranger, semaine(1, JourSemaine.LUNDI), semaine(1, JourSemaine.LUNDI), "CONGE", null);
        remplacement(CENTRE_B, "REMPL_ETRANGER", semaine(1, JourSemaine.LUNDI), salleB, creneauB, etranger, null);

        arbre("semaine_riche_texte", texteDuPdf("/imprimer?date=" + semaine(1, JourSemaine.MERCREDI)));
    }

    @Test
    void une_semaine_sans_aucune_ligne_imprime_l_entete_seule() throws Exception {
        decor();
        semaine(1);
        // un infirmier inactif et un autre centre : aucune ligne à imprimer
        UUID inactif = fiche(CENTRE_A, "INF_INACTIF", "INF_INACTIF", "Inactif", "Ines", false);
        affectation(CENTRE_A, "AFF_INACTIF", inactif, salle1, matin, tousLesJours());
        infirmierAffecte(CENTRE_B, "INF_ETRANGER", "Etranger", "Autre", false, salleB, creneauB, tousLesJours());
        arbre("semaine_vide_texte", texteDuPdf("/imprimer?date=" + semaine(1, JourSemaine.DIMANCHE)));
    }

    @Test
    void les_jours_ecrits_de_facon_inhabituelle_suivent_la_recherche_par_sous_chaine_sensible_a_la_casse() throws Exception {
        decor();
        semaine(1);
        infirmierAffecte(CENTRE_A, "INF_MINUSCULES", "Minuscule", "Marc", false, salle1, matin, "lundi,mardi");
        infirmierAffecte(CENTRE_A, "INF_COLLE", "Colle", "Anne", false, salle1, matin, "JEUDIVENDREDI");
        infirmierAffecte(CENTRE_A, "INF_SEPARATEUR", "Separateur", "Luc", false, salle2, matin, "LUNDI;SAMEDI");
        infirmierAffecte(CENTRE_A, "INF_VIDE", "Vide", "Eve", false, salle2, matin, "");
        arbre("jours_inhabituels_texte", texteDuPdf("/imprimer?date=" + semaine(1, JourSemaine.DIMANCHE)));
    }
}

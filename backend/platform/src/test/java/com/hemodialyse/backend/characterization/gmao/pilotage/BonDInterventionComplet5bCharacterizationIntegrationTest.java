package com.hemodialyse.backend.characterization.gmao.pilotage;

import org.junit.jupiter.api.Test;

import java.util.UUID;

/**
 * DOC5b : filet de rendu du bon d'intervention, TOUT le texte du PDF, avant la conversion du gabarit {@code bon_intervention.jrxml} de son
 * SQL embarqué (deux tables de la GMAO) vers un jeu de données fourni par la GMAO. Observé par
 * {@code GET /api/v1/gmao/interventions/{id}/bon} : une intervention complète à huit lignes de coût (quatre types, libellés qui
 * départagent majuscules, minuscules, accents et chiffres, quantité décimale, très grand montant, libellé long, lignes d'une AUTRE
 * intervention du centre à ne pas imprimer), puis une intervention sans aucune ligne (le bon s'imprime, totaux à zéro). Le texte doit
 * rester identique après la conversion. Date d'édition masquée. DONNÉES FICTIVES.
 */
class BonDInterventionComplet5bCharacterizationIntegrationTest extends PilotageCharacterizationSupport {

    private UUID generateur() {
        return equipement("G5B", CENTRE_A, e -> {
            e.designation = "Générateur 5b";
            e.fabricant = "Fresenius";
            e.modele = "5008S";
            e.serie = "SN-5B";
        });
    }

    @Test
    void le_bon_a_huit_lignes_de_cout_imprime_tout_le_texte_dans_l_ordre_des_libelles() throws Exception {
        UUID g = generateur();
        UUID autre = intervention("I_5B_AUTRE", g, CENTRE_A, x -> x.actions = "Autre intervention du centre");
        ligne(autre, "AUTRE", "Ligne d'une autre intervention", "1", "77777.00");

        UUID i = intervention("I_5B_COMPLET", g, CENTRE_A, x -> {
            x.intervenant = INTERVENANT_INTERNE;
            x.symptome = "Alarme conductivité";
            x.cause = "Cellule usée";
            x.actions = "Cellule remplacée";
            x.pieceRemplacee = "Cellule de conductivité";
            x.priorite = "HAUTE";
            x.echeance = T0.plusHours(6);
            x.etatAvant = "HORS_SERVICE";
            x.etatApres = "EN_SERVICE";
            x.demarrePar = TECH;
            x.demarreLe = T0.plusMinutes(10);
            x.cloturePar = TECH;
            x.clotureLe = T0.plusHours(2).plusMinutes(5);
        });
        ligne(i, "PIECE", "Zéro joint", "3", "100.50");
        ligne(i, "PIECE", "alpha minuscule", "1", "10.00");
        ligne(i, "MAIN_OEUVRE", "Alpha majuscule", "2.5", "1000.00");
        ligne(i, "INTERVENANT", "Éclair", "1", "5000.00");
        ligne(i, "AUTRE", "Joint 10", "0.0001", "10.00");
        ligne(i, "AUTRE", "Joint 2", "10", "0.50");
        ligne(i, "PIECE", "Transport", "1", "99999999.99");
        ligne(i, "PIECE", "Libellé très long qui dépasse largement la colonne de cent trente points de large", "2", "12.34");

        goldenTexte("bon_doc5b_complet_texte", textePdf(get(GMAO + "/interventions/" + i + "/bon", adminA())));
    }

    @Test
    void le_bon_sans_aucune_ligne_de_cout_s_imprime_avec_ses_totaux_a_zero_malgre_les_lignes_d_une_autre_intervention() throws Exception {
        UUID g = generateur();
        UUID autre = intervention("I_5B_AUTRE2", g, CENTRE_A, x -> x.actions = "Autre intervention du centre");
        ligne(autre, "PIECE", "Ligne d'une autre intervention", "1", "88888.00");
        UUID i = intervention("I_5B_VIDE", g, CENTRE_A, x -> x.actions = "Visite");

        goldenTexte("bon_doc5b_sans_ligne_texte", textePdf(get(GMAO + "/interventions/" + i + "/bon", adminA())));
    }
}

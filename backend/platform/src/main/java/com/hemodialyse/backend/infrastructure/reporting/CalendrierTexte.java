package com.hemodialyse.backend.infrastructure.reporting;

import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.optimisation.model.CalendrierProposition.InfirmierCalendrier;
import com.hemodialyse.backend.domain.planning.optimisation.model.CalendrierProposition.JourCalendrier;
import com.hemodialyse.backend.domain.planning.optimisation.model.CalendrierProposition.PatientCalendrier;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Textes imprimables du planning calendaire d'une proposition : en-tête d'une colonne (« Lun 28/09 ») et contenu d'une
 * case (patients et générateurs, infirmiers, effectif). Polices standard : aucun symbole spécial, des abréviations
 * expliquées dans la légende du modèle de document.
 */
public final class CalendrierTexte {

    private static final Map<JourSemaine, String> JOURS = Map.of(
            JourSemaine.DIMANCHE, "Dim", JourSemaine.LUNDI, "Lun", JourSemaine.MARDI, "Mar",
            JourSemaine.MERCREDI, "Mer", JourSemaine.JEUDI, "Jeu", JourSemaine.VENDREDI, "Ven",
            JourSemaine.SAMEDI, "Sam");
    private static final DateTimeFormatter JOUR_MOIS = DateTimeFormatter.ofPattern("dd/MM", Locale.FRANCE);

    private CalendrierTexte() {
    }

    public static String entete(JourCalendrier jour) {
        return JOURS.get(jour.jour()) + " " + JOUR_MOIS.format(jour.date());
    }

    public static String cellule(JourCalendrier jour) {
        if (jour.ferme()) {
            return jour.motifFermeture() == null || jour.motifFermeture().isBlank()
                    ? "FERMÉ" : "FERMÉ\n" + jour.motifFermeture();
        }
        if (jour.vide()) return "";
        List<String> lignes = new ArrayList<>();
        for (PatientCalendrier p : jour.patients()) lignes.add(patient(p));
        if (!jour.infirmiers().isEmpty()) {
            lignes.add("Infirmiers :");
            for (InfirmierCalendrier i : jour.infirmiers()) lignes.add("  " + infirmier(i));
        }
        String effectif = jour.patients().size() + " patient(s), " + jour.requis() + " inf. requis";
        lignes.add(jour.manque() > 0 ? effectif + " - MANQUE " + jour.manque() : effectif);
        return String.join("\n", lignes);
    }

    private static String patient(PatientCalendrier p) {
        StringBuilder texte = new StringBuilder("- ").append(p.nom());
        if (p.generateurCode() != null && !p.generateurCode().isBlank()) texte.append(" - ").append(p.generateurCode());
        if (p.aRisque()) texte.append(" (R)");
        if (p.deplace()) texte.append(" (dépl.)");
        if (p.temporaire()) texte.append(" (temp.)");
        return texte.toString();
    }

    private static String infirmier(InfirmierCalendrier i) {
        return switch (i.situation()) {
            case PREVU -> i.nom();
            case REMPLACANT -> i.nom() + " (rempl.)";
            case ABSENT -> i.nom() + " (absent)";
            case NOUVEAU -> i.nom() + " (nouveau)";
        };
    }
}

package com.hemodialyse.backend.contexts.planification.infrastructure.persistence;

import com.hemodialyse.backend.contexts.planification.domain.CalendrierDeLaProposition.InfirmierDuCalendrier;
import com.hemodialyse.backend.contexts.planification.domain.CalendrierDeLaProposition.JourDuCalendrier;
import com.hemodialyse.backend.contexts.planification.domain.CalendrierDeLaProposition.PatientDuCalendrier;
import com.hemodialyse.backend.contexts.planification.domain.CalendrierDeLaProposition.SituationInfirmier;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Textes imprimables du planning calendaire d'une proposition, écrits avec chaque ligne du calendrier (colonnes {@code entete_i} et
 * {@code cell_i}) pour le modèle de document : en-tête d'une colonne (« Lun 28/09 ») et contenu d'une case (patients et générateurs,
 * infirmiers, effectif). Polices standard : aucun symbole spécial, des abréviations expliquées dans la légende du modèle.
 */
final class CalendrierEnTexte {

    private static final Map<JourDeLaSemaine, String> JOURS = Map.of(
            JourDeLaSemaine.DIMANCHE, "Dim", JourDeLaSemaine.LUNDI, "Lun", JourDeLaSemaine.MARDI, "Mar",
            JourDeLaSemaine.MERCREDI, "Mer", JourDeLaSemaine.JEUDI, "Jeu", JourDeLaSemaine.VENDREDI, "Ven",
            JourDeLaSemaine.SAMEDI, "Sam");
    private static final DateTimeFormatter JOUR_MOIS = DateTimeFormatter.ofPattern("dd/MM", Locale.FRANCE);

    private CalendrierEnTexte() {
    }

    static String entete(JourDuCalendrier jour) {
        return JOURS.get(jour.jour()) + " " + JOUR_MOIS.format(jour.date());
    }

    static String cellule(JourDuCalendrier jour) {
        if (jour.ferme()) {
            return jour.motifFermeture() == null || jour.motifFermeture().isBlank()
                    ? "FERMÉ" : "FERMÉ\n" + jour.motifFermeture();
        }
        if (jour.vide()) return "";
        List<String> lignes = new ArrayList<>();
        for (PatientDuCalendrier p : jour.patients()) lignes.add(patient(p));
        if (!jour.infirmiers().isEmpty()) {
            lignes.add("Infirmiers :");
            for (InfirmierDuCalendrier i : jour.infirmiers()) lignes.add("  " + infirmier(i));
        }
        long affectes = jour.infirmiers().stream().filter(i -> i.situation() != SituationInfirmier.ABSENT).count();
        String effectif = jour.patients().size() + " patient(s), " + affectes + " inf. affecté(s)";
        lignes.add(jour.manque() > 0 ? effectif + " - MANQUE " + jour.manque() : effectif);
        if (jour.surplus() > 0) lignes.add(jour.surplus() + " inf. en trop");
        return String.join("\n", lignes);
    }

    private static String patient(PatientDuCalendrier p) {
        StringBuilder texte = new StringBuilder("- ").append(p.nom());
        if (p.generateurCode() != null && !p.generateurCode().isBlank()) texte.append(" - ").append(p.generateurCode());
        if (p.aRisque()) texte.append(" (R)");
        if (p.deplace()) texte.append(" (dépl.)");
        if (p.temporaire()) texte.append(" (temp.)");
        return texte.toString();
    }

    private static String infirmier(InfirmierDuCalendrier i) {
        return switch (i.situation()) {
            case PREVU -> i.nom();
            case REMPLACANT -> i.nom() + " (rempl.)";
            case ABSENT -> i.nom() + " (absent)";
            case NOUVEAU -> i.nom() + " (nouveau)";
        };
    }
}

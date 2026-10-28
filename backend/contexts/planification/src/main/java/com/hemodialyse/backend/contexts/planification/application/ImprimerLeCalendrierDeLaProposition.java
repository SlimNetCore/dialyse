package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.ExecutionDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.ExecutionsRepository;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.ExecutionId;
import com.hemodialyse.backend.contexts.planification.domain.Perimetre;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.Indicateurs;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.ImpressionDuCalendrierDeLaProposition;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.ImpressionDuCalendrierDeLaProposition.Document;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Imprime le planning calendaire figé d'une proposition (RG-PLN-101) avec le modèle de document du centre. Refus :
 * {@code OPTIMISATION_INTROUVABLE} (autre centre comprise) et {@code OPTIMISATION_CALENDRIER_ABSENT} (calcul sans planning
 * calendaire).
 */
public class ImprimerLeCalendrierDeLaProposition {

    private static final DateTimeFormatter DATE_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRANCE)
            .withZone(ZoneOffset.UTC);
    private static final Map<Perimetre, String> PERIMETRES = new EnumMap<>(Map.of(
            Perimetre.PATIENTS, "placement des patients",
            Perimetre.ROULEMENT, "roulement des infirmiers",
            Perimetre.COUVERTURE, "couverture des absences",
            Perimetre.COMPLET, "patients et roulement (complet)",
            Perimetre.MAINTENANCE, "maintenance des générateurs"));

    private final ExecutionsRepository executions;
    private final CalendriersDesPropositions calendriers;
    private final ImpressionDuCalendrierDeLaProposition impression;

    public ImprimerLeCalendrierDeLaProposition(ExecutionsRepository executions, CalendriersDesPropositions calendriers,
                                               ImpressionDuCalendrierDeLaProposition impression) {
        this.executions = executions;
        this.calendriers = calendriers;
        this.impression = impression;
    }

    static String titre(ExecutionDOptimisation e) {
        StringBuilder texte = new StringBuilder("Proposition du ").append(DATE_HEURE.format(e.creeLe()))
                .append(" UTC - périmètre : ").append(PERIMETRES.get(e.parametres().perimetre()));
        if (e.lancePar() != null && !e.lancePar().isBlank()) texte.append(" - lancée par ").append(e.lancePar());
        texte.append(e.appliqueeLe() == null ? " - non appliquée" : " - appliquée le " + DATE_HEURE.format(e.appliqueeLe()) + " UTC");
        return texte.toString();
    }

    static String resume(ExecutionDOptimisation e) {
        if (e.resume() == null || e.resume().avant() == null || e.resume().apres() == null) return "";
        Indicateurs a = e.resume().avant();
        Indicateurs b = e.resume().apres();
        return "Avant → après : générateurs utilisés " + a.generateursUtilises() + " → " + b.generateursUtilises()
                + " - salles ouvertes " + a.sallesOuvertes() + " → " + b.sallesOuvertes()
                + " - vacations requises " + a.vacationsRequises() + " → " + b.vacationsRequises()
                + " - patients non placés " + a.patientsNonPlaces() + " → " + b.patientsNonPlaces()
                + " - vacations non pourvues " + a.vacationsNonPourvues() + " → " + b.vacationsNonPourvues()
                + " - infirmiers mobilisés " + a.infirmiersMobilises() + " → " + b.infirmiersMobilises();
    }

    public Document executer(CentreId centre, ExecutionId id) {
        ExecutionDOptimisation execution = executions.parId(centre, id).orElseThrow(ConsulterLesOptimisations::introuvable);
        if (calendriers.semaines(centre, id).isEmpty()) {
            throw new RegleDePlanificationViolee("OPTIMISATION_CALENDRIER_ABSENT",
                    "Cette proposition n'a pas de planning calendaire : relancez le calcul");
        }
        return impression.imprimer(centre.valeur(), id.valeur(), titre(execution), resume(execution));
    }
}

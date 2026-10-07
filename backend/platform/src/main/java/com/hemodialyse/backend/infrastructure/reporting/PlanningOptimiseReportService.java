package com.hemodialyse.backend.infrastructure.reporting;

import com.hemodialyse.backend.application.planning.optimisation.OptimisationPlanningService;
import com.hemodialyse.backend.domain.planning.optimisation.model.PerimetreOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.Indicateurs;
import com.hemodialyse.backend.domain.planning.optimisation.model.RunOptimisation;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import net.sf.jasperreports.engine.JRParameter;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Planning calendaire d'une proposition d'optimisation : modèle de document
 * {@value ModeleDocumentCatalog#PLANNING_OPTIMISE}, imprimé via {@link ModeleDocumentPrinter}. Le modèle lit le
 * calendrier figé à la fin du calcul ; il ne dépend donc pas de l'état courant du centre.
 */
@Service
public class PlanningOptimiseReportService {

    private static final DateTimeFormatter DATE_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRANCE)
            .withZone(ZoneOffset.UTC);
    private static final Map<PerimetreOptimisation, String> PERIMETRES = new EnumMap<>(Map.of(
            PerimetreOptimisation.PATIENTS, "placement des patients",
            PerimetreOptimisation.ROULEMENT, "roulement des infirmiers",
            PerimetreOptimisation.COUVERTURE, "couverture des absences",
            PerimetreOptimisation.COMPLET, "patients et roulement (complet)",
            PerimetreOptimisation.MAINTENANCE, "maintenance des générateurs"));

    private final ModeleDocumentPrinter printer;
    private final OptimisationPlanningService planification;

    public PlanningOptimiseReportService(ModeleDocumentPrinter printer, OptimisationPlanningService planification) {
        this.printer = printer;
        this.planification = planification;
    }

    static String titre(RunOptimisation run) {
        StringBuilder texte = new StringBuilder("Proposition du ").append(DATE_HEURE.format(run.creeLe()))
                .append(" UTC - périmètre : ").append(PERIMETRES.get(run.parametres().perimetre()));
        if (run.lancePar() != null && !run.lancePar().isBlank()) texte.append(" - lancée par ").append(run.lancePar());
        texte.append(run.appliqueLe() == null ? " - non appliquée"
                : " - appliquée le " + DATE_HEURE.format(run.appliqueLe()) + " UTC");
        return texte.toString();
    }

    static String resume(RunOptimisation run) {
        if (run.resume() == null || run.resume().avant() == null || run.resume().apres() == null) return "";
        Indicateurs a = run.resume().avant();
        Indicateurs b = run.resume().apres();
        return "Avant → après : générateurs utilisés " + a.generateursUtilises() + " → " + b.generateursUtilises()
                + " - salles ouvertes " + a.sallesOuvertes() + " → " + b.sallesOuvertes()
                + " - vacations requises " + a.vacationsRequises() + " → " + b.vacationsRequises()
                + " - patients non placés " + a.patientsNonPlaces() + " → " + b.patientsNonPlaces()
                + " - vacations non pourvues " + a.vacationsNonPourvues() + " → " + b.vacationsNonPourvues()
                + " - infirmiers mobilisés " + a.infirmiersMobilises() + " → " + b.infirmiersMobilises();
    }

    static Map<String, Object> params(UUID runId, RunOptimisation run) {
        Map<String, Object> p = new HashMap<>();
        p.put(JRParameter.REPORT_LOCALE, Locale.FRANCE);
        p.put("RUN_ID", runId.toString());
        p.put("TITRE_PROPOSITION", titre(run));
        p.put("RESUME_INDICATEURS", resume(run));
        return p;
    }

    /**
     * @throws BusinessException {@code OPTIMISATION_INTROUVABLE} si l'exécution n'est pas celle du centre,
     *                           {@code OPTIMISATION_CALENDRIER_ABSENT} si elle n'a pas (encore) de planning calendaire
     */
    public ModeleDocumentPrinter.Document imprimer(UUID centreId, UUID runId) {
        RunOptimisation run = planification.consulter(centreId, runId);
        if (planification.semainesCalendrier(centreId, runId).isEmpty()) {
            throw new BusinessException("OPTIMISATION_CALENDRIER_ABSENT",
                    "Cette proposition n'a pas de planning calendaire : relancez le calcul");
        }
        return printer.print(centreId, ModeleDocumentCatalog.PLANNING_OPTIMISE, params(runId, run));
    }
}

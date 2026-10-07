package com.hemodialyse.backend.infrastructure.reporting;

import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.optimisation.model.CalendrierProposition.CaseCalendrier;
import com.hemodialyse.backend.domain.planning.optimisation.model.CalendrierProposition.InfirmierCalendrier;
import com.hemodialyse.backend.domain.planning.optimisation.model.CalendrierProposition.JourCalendrier;
import com.hemodialyse.backend.domain.planning.optimisation.model.CalendrierProposition.PatientCalendrier;
import com.hemodialyse.backend.domain.planning.optimisation.model.CalendrierProposition.SituationInfirmier;
import com.hemodialyse.backend.domain.planning.optimisation.model.ParametresOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.PerimetreOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.Indicateurs;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.Resume;
import com.hemodialyse.backend.domain.planning.optimisation.model.RunOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.port.CalendrierPropositionPort;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Planning calendaire d'une proposition, sur base réelle : persistance du calendrier figé (bornée au centre) et
 * impression par le modèle de document (une seule requête, sans calcul).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PlanningOptimiseReportIntegrationTest {

    private static final UUID CENTRE = UUID.fromString("99998300-0000-0000-0000-00000000000a");
    private static final UUID AUTRE_CENTRE = UUID.fromString("99998300-0000-0000-0000-00000000000b");
    private static final UUID RUN = UUID.fromString("99998300-0000-0000-0000-0000000000f1");
    private static final UUID RUN_AUTRE = UUID.fromString("99998300-0000-0000-0000-0000000000f2");
    private static final UUID SALLE = UUID.fromString("99998300-0000-0000-0000-0000000000a1");
    private static final UUID CRENEAU = UUID.fromString("99998300-0000-0000-0000-0000000000c1");
    private static final LocalDate DIMANCHE = LocalDate.of(2026, 9, 27);

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private CalendrierPropositionPort calendriers;

    private static CaseCalendrier ligne(LocalDate semaine, String salle, int ordre, String patient, String generateur) {
        List<JourCalendrier> jours = new java.util.ArrayList<>();
        for (JourSemaine jour : JourSemaine.values()) {
            LocalDate date = semaine.plusDays(jour.ordinal());
            jours.add(switch (jour) {
                case LUNDI -> new JourCalendrier(jour, date, false, null, 1, 0,
                        List.of(new PatientCalendrier(UUID.randomUUID(), patient, generateur, true, true, false,
                                "Salle B · Matin · B-G1")),
                        List.of(new InfirmierCalendrier("Sara Amrani", SituationInfirmier.NOUVEAU)));
                case MARDI -> new JourCalendrier(jour, date, true, "Jour férié", 0, 0, List.of(), List.of());
                default -> new JourCalendrier(jour, date, false, null, 0, 0, List.of(), List.of());
            });
        }
        return new CaseCalendrier(semaine, SALLE, salle, ordre, CRENEAU, "Matin", 1, jours);
    }

    @BeforeEach
    void seed() {
        cleanup();
        calendriers.enregistrer(CENTRE, RUN, List.of(ligne(DIMANCHE, "Salle A", 1, "Benali", "A-G1")));
        calendriers.enregistrer(AUTRE_CENTRE, RUN_AUTRE, List.of(ligne(DIMANCHE, "Salle Etrangere", 1, "Etranger", "Z-G1")));
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM planification_calendrier_case WHERE center_id IN (?, ?)", CENTRE, AUTRE_CENTRE);
        jdbc.update("DELETE FROM planification_optimisation WHERE center_id IN (?, ?)", CENTRE, AUTRE_CENTRE);
    }

    private List<String> textes(UUID centre, UUID run) throws Exception {
        JasperReport compiled;
        try (InputStream is = Files.newInputStream(Path.of("src/main/resources/reports/planning_optimise.jrxml"))) {
            compiled = JasperCompileManager.compileReport(is);
        }
        Map<String, Object> params = new HashMap<>();
        params.put("CENTER_ID", centre.toString());
        params.put("RUN_ID", run.toString());
        params.put("TITRE_PROPOSITION", "Proposition de test");
        params.put("RESUME_INDICATEURS", "Avant → après");
        try (Connection connection = dataSource.getConnection()) {
            JasperPrint print = JasperFillManager.fillReport(compiled, params, connection);
            return print.getPages().stream().map(JRPrintPage::getElements).flatMap(List::stream)
                    .filter(e -> e instanceof JRPrintText).map(e -> ((JRPrintText) e).getFullText())
                    .collect(Collectors.toList());
        }
    }

    @Test
    void the_calendar_is_persisted_and_read_back_by_week_for_its_center_only() {
        assertThat(calendriers.semaines(CENTRE, RUN)).containsExactly(DIMANCHE);
        List<CaseCalendrier> lues = calendriers.lire(CENTRE, RUN, DIMANCHE);
        assertThat(lues).hasSize(1);
        assertThat(lues.get(0).salleNom()).isEqualTo("Salle A");
        JourCalendrier lundi = lues.get(0).jours().get(1);
        assertThat(lundi.patients()).extracting("nom").containsExactly("Benali");
        assertThat(lundi.patients().get(0).deplace()).isTrue();
        assertThat(lundi.patients().get(0).avant()).isEqualTo("Salle B · Matin · B-G1");
        assertThat(lundi.infirmiers().get(0).situation()).isEqualTo(SituationInfirmier.NOUVEAU);
        assertThat(lues.get(0).jours().get(2).ferme()).isTrue();

        assertThat(calendriers.semaines(AUTRE_CENTRE, RUN)).as("run d'un autre centre : rien").isEmpty();
        assertThat(calendriers.lire(AUTRE_CENTRE, RUN, DIMANCHE)).isEmpty();
        assertThat(calendriers.lire(CENTRE, RUN_AUTRE, DIMANCHE)).isEmpty();
    }

    @Test
    void saving_again_replaces_the_previous_calendar_of_the_run() {
        calendriers.enregistrer(CENTRE, RUN, List.of(ligne(DIMANCHE.plusWeeks(1), "Salle B", 2, "Amrani", "B-G1")));

        assertThat(calendriers.semaines(CENTRE, RUN)).containsExactly(DIMANCHE.plusWeeks(1));
    }

    @Test
    void orphan_calendars_of_purged_runs_are_removed_without_touching_other_centers() {
        // aucune ligne planification_optimisation n'existe pour ces exécutions : elles sont orphelines
        calendriers.purgerOrphelins(CENTRE);

        assertThat(calendriers.semaines(CENTRE, RUN)).isEmpty();
        assertThat(calendriers.semaines(AUTRE_CENTRE, RUN_AUTRE)).as("l'autre centre est intact").containsExactly(DIMANCHE);
    }

    @Test
    void the_printed_planning_shows_patients_generators_nurses_closed_days_and_the_legend() throws Exception {
        List<String> textes = textes(CENTRE, RUN);

        assertThat(textes).contains("Salle A", "Matin", "Lun 28/09", "Mar 29/09", "Proposition de test");
        assertThat(textes.stream().filter(t -> t.contains("Benali - A-G1 (R) (dépl.)")).count()).isEqualTo(1);
        assertThat(textes.stream().anyMatch(t -> t.contains("Sara Amrani (nouveau)"))).isTrue();
        assertThat(textes.stream().anyMatch(t -> t.contains("1 patient(s), 1 inf. affecté(s)")))
                .as("l'effectif imprimé compte les infirmiers affectés, pas les requis").isTrue();
        assertThat(textes.stream().anyMatch(t -> t.strip().startsWith("FERMÉ") && t.contains("Jour férié"))).isTrue();
        assertThat(textes.stream().anyMatch(t -> t.strip().startsWith("Légende"))).as("légende imprimée").isTrue();
        assertThat(textes).doesNotContain("Etranger");
    }

    @Test
    void another_center_never_prints_the_calendar_of_a_run_that_is_not_its_own() throws Exception {
        assertThat(textes(AUTRE_CENTRE, RUN)).noneMatch(t -> t.contains("Benali"));
        assertThat(textes(CENTRE, RUN_AUTRE)).noneMatch(t -> t.contains("Etranger"));
    }

    @Test
    void the_title_and_the_summary_describe_the_run_and_its_indicators() {
        RunOptimisation run = RunOptimisation.demarrer(CENTRE, ParametresOptimisation.parDefaut(
                PerimetreOptimisation.COMPLET, DIMANCHE), "admin", "e", Instant.parse("2026-10-05T14:30:00Z"));
        Indicateurs avant = new Indicateurs(12, 20, 8, 3, 2, 4, 6, 3, 1);
        Indicateurs apres = new Indicateurs(10, 16, 7, 1, 0, 0, 6, 1, 0);
        RunOptimisation fini = new RunOptimisation(run.id(), run.centerId(), RunOptimisation.StatutRun.TERMINEE,
                run.parametres(), run.creeLe(), run.creeLe().plusSeconds(20), "admin", null, null, "e",
                new Resume(2, 0, 5, 0, avant, apres, 0, 0), null, null, null);

        String titre = PlanningOptimiseReportService.titre(fini);
        String resume = PlanningOptimiseReportService.resume(fini);

        assertThat(titre).contains("05/10/2026 14:30", "patients et roulement (complet)", "admin", "non appliquée");
        assertThat(resume).contains("générateurs utilisés 12 → 10", "salles ouvertes 20 → 16",
                "patients non placés 2 → 0", "vacations non pourvues 4 → 0");
        assertThat(PlanningOptimiseReportService.resume(run)).as("sans résultat : pas de synthèse").isEmpty();
        assertThat(PlanningOptimiseReportService.titre(new RunOptimisation(run.id(), run.centerId(),
                RunOptimisation.StatutRun.TERMINEE, run.parametres(), run.creeLe(), null, "admin", null, null, "e",
                null, null, null, Instant.parse("2026-10-06T08:00:00Z")))).contains("appliquée le 06/10/2026 08:00");
    }
}

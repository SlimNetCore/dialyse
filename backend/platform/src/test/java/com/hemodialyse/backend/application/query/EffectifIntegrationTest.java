package com.hemodialyse.backend.application.query;

import com.hemodialyse.backend.contexts.pilotagedirection.application.ConsulterLaCapaciteDesCentres;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection;
import com.hemodialyse.backend.contexts.patientcouverture.application.ConsulterLEffectif;
import com.hemodialyse.backend.contexts.patientcouverture.application.CriteresDeRecherche;
import com.hemodialyse.backend.contexts.patientcouverture.application.HorlogeDesCentres;
import com.hemodialyse.backend.contexts.patientcouverture.application.LectureDesPatients;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Effectif d'une période et filtres à choix multiple : la même règle (sortie avant la période = non compté, pendant ou
 * après = compté) pilote la synthèse des patients, le tableau de bord de la direction (patients, ventilations) et la
 * file active rapportée à la capacité ; la liste accepte plusieurs valeurs de sexe ou d'état.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class EffectifIntegrationTest {

    private static final UUID SOC = UUID.fromString("99997600-0000-0000-0000-000000000001");
    private static final UUID C1 = UUID.fromString("99997600-0000-0000-0000-0000000000c1");
    private static final LocalDate DEBUT = LocalDate.of(2026, 9, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 9, 30);
    private static final LocalDate ADMIS = LocalDate.of(2025, 1, 10);

    @Autowired
    private JdbcTemplate jdbc;
    /** Lecture du module Patient et couverture, sans garde de centre (la garde est éprouvée par le filet PC1). */
    @Autowired
    private LectureDesPatients lecture;
    private ConsulterLEffectif summary;
    @Autowired
    private TableauDeBordDeLaDirection tableau;
    @Autowired
    private ConsulterLaCapaciteDesCentres capacite;

    @BeforeEach
    void seed() {
        summary = new ConsulterLEffectif(demande -> new CentreId(C1), lecture, new HorlogeDesCentres() {
            @Override
            public LocalDate aujourdhui(CentreId centre) {
                return FIN;
            }

            @Override
            public java.time.Instant maintenant() {
                return java.time.Instant.parse("2026-09-30T12:00:00Z");
            }
        });
        cleanup();
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOC, "ZT-EF1", "Société EF1");
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", C1, "ZT-EF1-A", "C1", SOC);
        for (int i = 0; i < 3; i++) patient("M", "PERMANENT", null, ADMIS, false);
        for (int i = 0; i < 2; i++) patient("F", "PERMANENT", null, ADMIS, false);
        patient("M", "TRANSFERE", LocalDate.of(2026, 12, 1), ADMIS, false);      // sortie après la période : compté
        patient("M", "TRANSFERE", LocalDate.of(2026, 3, 1), ADMIS, false);       // sortie avant : non compté
        patient("F", "DECEDE", DEBUT, ADMIS, false);                              // décès le 1er : non compté
        patient("F", "PERMANENT", null, LocalDate.of(2026, 10, 5), false);        // admis après la période : non compté
        patient("M", "PERMANENT", null, ADMIS, true);                             // en sommeil : compté, hors file active
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM patients WHERE center_id = ?", C1);
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-EF1%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-EF1%'");
    }

    private void patient(String sexe, String etat, LocalDate evenement, LocalDate admission, boolean sommeil) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_naissance, numero_assurance, "
                        + "date_admission, type_patient, etat_patient, date_evenement_etat, qualite_assure, sous_kt, epo_enabled, "
                        + "en_sommeil, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,FALSE,?,CURRENT_TIMESTAMP)",
                id, C1, "EF-" + id.toString().substring(0, 8), "NOM", "Prenom", sexe, Date.valueOf("1970-05-05"),
                "ASS-" + id.toString().substring(0, 8), Date.valueOf(admission), "NON_VACANCIER", etat,
                evenement == null ? null : Date.valueOf(evenement), "ASSURE", false, sommeil);
    }

    private CriteresDeRecherche recherche(String sexe, String etat) {
        return new CriteresDeRecherche(0, 100, null, null, null, sexe, null, null, null, etat, null, null, null, null, null, null,
                null, null);
    }

    private long total(String sexe, String etat) {
        return lecture.rechercher(new CentreId(C1), recherche(sexe, etat)).total();
    }

    @Test
    void the_monthly_summary_counts_the_patients_present_during_the_month() {
        assertThat(summary.resume(C1, YearMonth.of(2026, 9)).totalPatients()).isEqualTo(7);
        assertThat(summary.resume(C1, YearMonth.of(2026, 3)).totalPatients())
                .as("mars : transféré du 1er mars et décédée de septembre encore présents, admis d'octobre non").isEqualTo(9);
        assertThat(summary.resume(C1, YearMonth.of(2026, 10)).totalPatients())
                .as("octobre : le décès et le transfert de mars sont sortis, l'admis du 5 octobre arrive").isEqualTo(8);
    }

    @Test
    void the_direction_dashboard_counts_the_headcount_of_the_period() {
        var overview = tableau.vueDEnsemble(SOC, DEBUT, FIN);

        assertThat(overview.centres().getFirst().patients()).isEqualTo(7);
        assertThat(tableau.vueDEnsemble(SOC, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31)).centres().getFirst().patients())
                .as("en 2027 le transfert de décembre 2026 est sorti : seuls restent les permanents (dont le sommeil)")
                .isEqualTo(7);
    }

    @Test
    void the_breakdown_by_sex_uses_the_same_headcount() {
        var sexe = tableau.repartition(SOC, DEBUT, FIN).sexe().getFirst();

        // M : 3 permanents + transféré de décembre + en sommeil = 5 ; F : 2 permanents (décédée et admise en octobre exclues)
        assertThat(sexe.masculin()).isEqualTo(5L);
        assertThat(sexe.feminin()).isNull();   // 2 patients : sous le seuil d'anonymat
    }

    @Test
    void the_active_queue_reported_to_the_capacity_ignores_exited_and_sleeping_patients() {
        var centre = capacite.execute(SOC, DEBUT, FIN).centres().getFirst().capacite();

        assertThat(centre.fileActive()).isEqualTo(6L);   // 7 de l'effectif, moins le patient en sommeil
    }

    @Test
    void the_list_accepts_several_values_for_the_sex_and_state_filters() {
        assertThat(total("M,F", null)).isEqualTo(10);
        assertThat(total("M", null)).isEqualTo(6);
        assertThat(total("F", null)).isEqualTo(4);
        assertThat(total(null, "PERMANENT,TRANSFERE")).isEqualTo(9);
        assertThat(total(null, "DECEDE,TRANSFERE")).isEqualTo(3);
        assertThat(total("m, f", "permanent,decede"))
                .as("insensible à la casse et aux espaces").isEqualTo(8);
        List<Object> sexes = lecture.rechercher(new CentreId(C1), recherche("F", null)).items().stream()
                .map(r -> (Object) r.sexe()).collect(Collectors.toList());
        assertThat(sexes).containsOnly("F");
    }
}

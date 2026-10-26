package com.hemodialyse.backend.application.infirmier;

import com.hemodialyse.backend.domain.infirmier.model.AbsenceInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.AffectationInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.Presence.CasePresence;
import com.hemodialyse.backend.domain.infirmier.model.Presence.SemainePresence;
import com.hemodialyse.backend.domain.infirmier.model.Presence.StatutCase;
import com.hemodialyse.backend.domain.infirmier.model.QualificationInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.RemplacementInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.TypeAbsence;
import com.hemodialyse.backend.domain.infirmier.port.AffectationInfirmierRepositoryPort;
import com.hemodialyse.backend.domain.infirmier.port.RemplacementInfirmierRepositoryPort;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.PlanningParametres;
import com.hemodialyse.backend.domain.planning.port.PlanningParametresPort;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Référentiel des infirmiers et planning de présence sur base réelle : isolation par centre, validation du roulement,
 * sous-effectif, absences, remplacements, alertes et charge mensuelle.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class InfirmierPresenceIntegrationTest {

    private static final UUID CENTRE = UUID.fromString("99998000-0000-0000-0000-00000000000a");
    private static final UUID AUTRE_CENTRE = UUID.fromString("99998000-0000-0000-0000-00000000000b");
    private static final UUID SALLE = UUID.fromString("99998000-0000-0000-0000-0000000000a1");
    private static final UUID SALLE_AUTRE = UUID.fromString("99998000-0000-0000-0000-0000000000b1");
    private static final UUID MATIN = UUID.fromString("99998000-0000-0000-0000-0000000000c1");
    private static final UUID CRENEAU_AUTRE = UUID.fromString("99998000-0000-0000-0000-0000000000c9");
    private static final LocalDate DIMANCHE = LocalDate.of(2026, 9, 27);
    private static final LocalDate LUNDI = DIMANCHE.plusDays(1);

    @Autowired
    private AffectationInfirmierRepositoryPort roulements;
    @Autowired
    private RemplacementInfirmierRepositoryPort remplacementsLegacy;
    @Autowired
    private PresenceInfirmierQueryService presence;
    @Autowired
    private PlanningParametresPort parametres;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        cleanup();
        jdbc.update("INSERT INTO salle (id, center_id, code, nom) VALUES (?, ?, 'IN-S1', 'Salle test')", SALLE, CENTRE);
        jdbc.update("INSERT INTO salle (id, center_id, code, nom) VALUES (?, ?, 'IN-S2', 'Salle autre')", SALLE_AUTRE, AUTRE_CENTRE);
        jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?, ?, 'IN1', 'Matin')", MATIN, CENTRE);
        jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?, ?, 'IN2', 'Autre')", CRENEAU_AUTRE, AUTRE_CENTRE);
    }

    @AfterEach
    void cleanup() {
        for (String table : List.of("infirmier_remplacement", "infirmier_absence", "infirmier_affectation", "infirmier",
                "planning_parametres", "patients", "position_creneau", "salle")) {
            jdbc.update("DELETE FROM " + table + " WHERE center_id IN (?, ?)", CENTRE, AUTRE_CENTRE);
        }
    }

    /**
     * Fiche posée directement dans le référentiel (les écritures du référentiel sont servies par le contexte Planification,
     * slice P2) ; {@code .infirmier().id()} garde la forme des anciens appels.
     */
    private FicheCreee infirmier(UUID centre, String matricule, String nom, boolean habilite) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO infirmier (id, center_id, matricule, nom, qualification, habilite_isolement, actif) "
                + "VALUES (?, ?, ?, ?, ?, ?, TRUE)", id, centre, matricule, nom, QualificationInfirmier.INFIRMIER.name(), habilite);
        return new FicheCreee(new Fiche(id));
    }

    private record Fiche(UUID id) {
    }

    private record FicheCreee(Fiche infirmier) {
    }

    private CasePresence caseLundi() {
        SemainePresence s = presence.semaine(CENTRE, DIMANCHE.plusDays(3));
        return s.cases().stream().filter(c -> c.jour() == JourSemaine.LUNDI && c.salleId().equals(SALLE)
                && c.creneauId().equals(MATIN)).findFirst().orElseThrow();
    }

    @Test
    void a_nurse_assigned_to_a_room_without_patients_is_reported_as_surplus_for_that_center_only() {
        UUID amrani = infirmier(CENTRE, "S1", "Amrani", false).infirmier().id();
        UUID autre = infirmier(AUTRE_CENTRE, "S2", "Etranger", false).infirmier().id();
        affecter(CENTRE, amrani, SALLE, MATIN, EnumSet.of(JourSemaine.LUNDI, JourSemaine.MARDI));
        affecter(AUTRE_CENTRE, autre, SALLE_AUTRE, CRENEAU_AUTRE, EnumSet.of(JourSemaine.LUNDI));

        var alertes = presence.alertesSureffectif(CENTRE, DIMANCHE, 7);
        List<JourSemaine> jours = presence.joursEnSureffectif(CENTRE, SALLE, MATIN,
                EnumSet.of(JourSemaine.LUNDI, JourSemaine.MERCREDI), DIMANCHE.plusDays(3));

        assertEquals(2, alertes.size(), "lundi et mardi, la salle n'a aucun patient");
        assertTrue(alertes.stream().allMatch(a -> a.salleId().equals(SALLE) && a.patients() == 0 && a.surplus() == 1));
        assertEquals(List.of(JourSemaine.LUNDI), jours, "mercredi : personne n'est prévu");
        assertEquals(1, presence.alertesSureffectif(AUTRE_CENTRE, DIMANCHE, 7).size(), "chaque centre voit les siens");
    }

    @Test
    void the_week_shows_understaffing_then_coverage_once_nurses_are_planned() {
        patients(5, JourSemaine.LUNDI);
        UUID a = infirmier(CENTRE, "M1", "Amrani", false).infirmier().id();
        UUID b = infirmier(CENTRE, "M2", "Benali", false).infirmier().id();
        affecter(CENTRE, a, SALLE, MATIN, EnumSet.of(JourSemaine.LUNDI));

        CasePresence seul = caseLundi();
        assertEquals(StatutCase.SOUS_EFFECTIF, seul.statut());
        assertEquals(2, seul.requis());
        assertEquals(5, seul.patients());

        affecter(CENTRE, b, SALLE, MATIN, EnumSet.of(JourSemaine.LUNDI));
        assertEquals(StatutCase.COUVERT, caseLundi().statut());
    }

    @Test
    void the_safety_ratio_of_the_center_changes_the_requirement() {
        patients(5, JourSemaine.LUNDI);
        com.hemodialyse.backend.application.planning.ParametrageDuPlanningPourLesTests.enregistrer(jdbc, CENTRE, new PlanningParametres(EnumSet.allOf(JourSemaine.class), Set.of(), 2));

        assertEquals(3, caseLundi().requis());
    }

    @Test
    void an_absence_uncovers_the_slot_and_an_eligible_replacement_covers_it_again() {
        patients(3, JourSemaine.LUNDI);
        UUID titulaire = infirmier(CENTRE, "M1", "Amrani", false).infirmier().id();
        UUID renfort = infirmier(CENTRE, "M2", "Benali", false).infirmier().id();
        affecter(CENTRE, titulaire, SALLE, MATIN, EnumSet.of(JourSemaine.LUNDI));
        assertEquals(StatutCase.COUVERT, caseLundi().statut());

        AbsenceInfirmier absence = declarerAbsence(CENTRE, titulaire, LUNDI, LUNDI, TypeAbsence.MALADIE, "Grippe");
        CasePresence decouverte = caseLundi();
        assertEquals(StatutCase.SOUS_EFFECTIF, decouverte.statut());
        assertEquals(1, decouverte.absents().size());
        assertEquals(1, presence.alertes(CENTRE, LUNDI, 1).size());
        assertEquals(List.of("Benali"),
                presence.remplacants(CENTRE, LUNDI, SALLE, MATIN).stream().map(c -> c.nom()).toList());

        RemplacementInfirmier r = remplacer(CENTRE, LUNDI, SALLE, MATIN, renfort, titulaire);
        assertEquals(StatutCase.COUVERT, caseLundi().statut());
        assertTrue(caseLundi().presents().get(0).remplacant());
        assertTrue(presence.alertes(CENTRE, LUNDI, 1).isEmpty());

        jdbc.update("DELETE FROM infirmier_remplacement WHERE id = ?", r.id());
        assertEquals(StatutCase.SOUS_EFFECTIF, caseLundi().statut());
        jdbc.update("DELETE FROM infirmier_absence WHERE id = ?", absence.id());
        assertEquals(StatutCase.COUVERT, caseLundi().statut());
    }

    @Test
    void nurses_and_replacements_of_another_center_never_cover_this_center() {
        patients(3, JourSemaine.LUNDI);
        infirmier(AUTRE_CENTRE, "M1", "Etranger", false);

        assertEquals(StatutCase.SOUS_EFFECTIF, caseLundi().statut());
        assertTrue(presence.remplacants(CENTRE, LUNDI, SALLE, MATIN).isEmpty());
    }

    /**
     * Écrit au roulement comme le fait l'optimisation (slice P8, encore dans le legacy) : les règles sont celles du contexte Planification.
     */
    private AffectationInfirmier affecter(UUID centre, UUID infirmier, UUID salle, UUID creneau, Set<JourSemaine> jours) {
        return roulements.save(AffectationInfirmier.creer(centre, infirmier, salle, creneau, jours));
    }

    private AbsenceInfirmier declarerAbsence(UUID centre, UUID infirmier, LocalDate debut, LocalDate fin, TypeAbsence type, String motif) {
        AbsenceInfirmier absence = AbsenceInfirmier.creer(centre, infirmier, debut, fin, type, motif);
        jdbc.update("INSERT INTO infirmier_absence (id, center_id, infirmier_id, date_debut, date_fin, type, motif) VALUES (?, ?, ?, ?, ?, ?, ?)",
                absence.id(), centre, infirmier, java.sql.Date.valueOf(debut), java.sql.Date.valueOf(fin), type.name(), motif);
        return absence;
    }

    private RemplacementInfirmier remplacer(UUID centre, LocalDate date, UUID salle, UUID creneau, UUID infirmier, UUID remplace) {
        return remplacementsLegacy.save(RemplacementInfirmier.creer(centre, date, salle, creneau, infirmier, remplace));
    }

    @Test
    void a_deactivated_nurse_leaves_the_planning_and_alert_horizon_is_bounded() {
        patients(2, JourSemaine.LUNDI);
        UUID a = infirmier(CENTRE, "M1", "Amrani", false).infirmier().id();
        affecter(CENTRE, a, SALLE, MATIN, EnumSet.of(JourSemaine.LUNDI));
        assertEquals(StatutCase.COUVERT, caseLundi().statut());

        jdbc.update("UPDATE infirmier SET actif = FALSE WHERE id = ?", a);

        assertEquals(StatutCase.SOUS_EFFECTIF, caseLundi().statut());
        assertThrows(IllegalArgumentException.class, () -> presence.alertes(CENTRE, LUNDI, 0));
        assertThrows(IllegalArgumentException.class,
                () -> presence.alertes(CENTRE, LUNDI, PresenceInfirmierQueryService.HORIZON_MAX_JOURS + 1));
    }

    @Test
    void the_monthly_workload_is_paginated_and_counts_rotation_and_replacements() {
        UUID a = infirmier(CENTRE, "M1", "Amrani", false).infirmier().id();
        UUID b = infirmier(CENTRE, "M2", "Benali", false).infirmier().id();
        affecter(CENTRE, a, SALLE, MATIN, EnumSet.of(JourSemaine.LUNDI));
        patients(1, JourSemaine.LUNDI);
        remplacer(CENTRE, LocalDate.of(2026, 10, 5), SALLE, MATIN, b, null);

        var page = presence.charge(CENTRE, YearMonth.of(2026, 10), 0, 1);
        var suite = presence.charge(CENTRE, YearMonth.of(2026, 10), 1, 1);

        assertEquals(2, page.page().total());
        assertEquals(1, page.page().items().size());
        assertEquals(4, page.page().items().get(0).total());
        assertEquals(1, suite.page().items().get(0).total());
        assertEquals(2.5, page.moyenne());
        assertFalse(page.page().items().isEmpty());
    }

    private void patients(int nombre, JourSemaine... jours) {
        Set<JourSemaine> choisis = EnumSet.copyOf(List.of(jours));
        for (int i = 0; i < nombre; i++) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, "
                            + "numero_assurance, type_patient, salle_id, position_id, jour_dimanche, jour_lundi, jour_mardi, "
                            + "jour_mercredi, jour_jeudi, jour_vendredi, jour_samedi, created_at) "
                            + "VALUES (?, ?, ?, 'Test', 'Pat', 'M', ?, ?, 'NON_VACANCIER', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, CENTRE, "IN-" + id.toString().substring(0, 8), LocalDate.of(2026, 1, 1),
                    "IN-ASS-" + id.toString().substring(0, 8), SALLE, MATIN,
                    choisis.contains(JourSemaine.DIMANCHE), choisis.contains(JourSemaine.LUNDI),
                    choisis.contains(JourSemaine.MARDI), choisis.contains(JourSemaine.MERCREDI),
                    choisis.contains(JourSemaine.JEUDI), choisis.contains(JourSemaine.VENDREDI),
                    choisis.contains(JourSemaine.SAMEDI), OffsetDateTime.now(ZoneOffset.UTC));
        }
    }
}

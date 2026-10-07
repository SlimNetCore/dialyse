package com.hemodialyse.backend.application.infirmier;

import com.hemodialyse.backend.application.infirmier.InfirmierService.InfirmierDetail;
import com.hemodialyse.backend.domain.infirmier.model.AbsenceInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.AffectationInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.Presence.CasePresence;
import com.hemodialyse.backend.domain.infirmier.model.Presence.SemainePresence;
import com.hemodialyse.backend.domain.infirmier.model.Presence.StatutCase;
import com.hemodialyse.backend.domain.infirmier.model.QualificationInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.RemplacementInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.TypeAbsence;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.PlanningParametres;
import com.hemodialyse.backend.domain.planning.port.PlanningParametresPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
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
    private InfirmierService infirmiers;
    @Autowired
    private AffectationInfirmierService affectations;
    @Autowired
    private AbsenceInfirmierService absences;
    @Autowired
    private RemplacementInfirmierService remplacements;
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

    private InfirmierDetail infirmier(UUID centre, String matricule, String nom, boolean habilite) {
        return infirmiers.creer(centre, matricule, nom, null, null, QualificationInfirmier.INFIRMIER, habilite);
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
        affectations.ajouter(CENTRE, amrani, SALLE, MATIN, EnumSet.of(JourSemaine.LUNDI, JourSemaine.MARDI));
        affectations.ajouter(AUTRE_CENTRE, autre, SALLE_AUTRE, CRENEAU_AUTRE, EnumSet.of(JourSemaine.LUNDI));

        var alertes = presence.alertesSureffectif(CENTRE, DIMANCHE, 7);
        List<JourSemaine> jours = presence.joursEnSureffectif(CENTRE, SALLE, MATIN,
                EnumSet.of(JourSemaine.LUNDI, JourSemaine.MERCREDI), DIMANCHE.plusDays(3));

        assertEquals(2, alertes.size(), "lundi et mardi, la salle n'a aucun patient");
        assertTrue(alertes.stream().allMatch(a -> a.salleId().equals(SALLE) && a.patients() == 0 && a.surplus() == 1));
        assertEquals(List.of(JourSemaine.LUNDI), jours, "mercredi : personne n'est prévu");
        assertEquals(1, presence.alertesSureffectif(AUTRE_CENTRE, DIMANCHE, 7).size(), "chaque centre voit les siens");
    }

    @Test
    void a_matricule_is_unique_per_center_and_each_center_only_sees_its_nurses() {
        infirmier(CENTRE, "M1", "Amrani", false);
        infirmier(AUTRE_CENTRE, "M1", "Benali", false);

        BusinessException doublon = assertThrows(BusinessException.class, () -> infirmier(CENTRE, "m1", "Cherif", false));
        PagedResult<InfirmierDetail> centre = infirmiers.lister(CENTRE, 0, 20);

        assertEquals("INFIRMIER_MATRICULE_EXISTANT", doublon.getCode());
        assertEquals(1, centre.total());
        assertEquals("Amrani", centre.items().get(0).infirmier().nom());
        assertEquals(1, infirmiers.lister(AUTRE_CENTRE, 0, 20).total());
    }

    @Test
    void a_nurse_of_another_center_cannot_be_read_modified_or_assigned() {
        UUID etranger = infirmier(AUTRE_CENTRE, "M9", "Etranger", false).infirmier().id();

        assertThrows(BusinessException.class, () -> infirmiers.desactiver(CENTRE, etranger));
        assertThrows(BusinessException.class,
                () -> affectations.ajouter(CENTRE, etranger, SALLE, MATIN, EnumSet.of(JourSemaine.LUNDI)));
        assertThrows(BusinessException.class,
                () -> absences.declarer(CENTRE, etranger, LUNDI, LUNDI, TypeAbsence.CONGE, null));
    }

    @Test
    void the_rotation_is_validated_against_the_center_and_overlaps() {
        UUID id = infirmier(CENTRE, "M1", "Amrani", false).infirmier().id();

        assertEquals("AFFECTATION_SALLE_INCONNUE", assertThrows(BusinessException.class,
                () -> affectations.ajouter(CENTRE, id, SALLE_AUTRE, MATIN, EnumSet.of(JourSemaine.LUNDI))).getCode());
        assertEquals("AFFECTATION_CRENEAU_INCONNU", assertThrows(BusinessException.class,
                () -> affectations.ajouter(CENTRE, id, SALLE, CRENEAU_AUTRE, EnumSet.of(JourSemaine.LUNDI))).getCode());

        AffectationInfirmier lmv = affectations.ajouter(CENTRE, id, SALLE, MATIN,
                EnumSet.of(JourSemaine.LUNDI, JourSemaine.MERCREDI));
        assertEquals("AFFECTATION_CHEVAUCHEMENT", assertThrows(BusinessException.class,
                () -> affectations.ajouter(CENTRE, id, SALLE, MATIN, EnumSet.of(JourSemaine.MERCREDI))).getCode());

        affectations.modifier(CENTRE, id, lmv.id(), SALLE, MATIN, EnumSet.of(JourSemaine.LUNDI));
        assertEquals(Set.of(JourSemaine.LUNDI),
                infirmiers.lister(CENTRE, 0, 20).items().get(0).affectations().get(0).jours());

        affectations.supprimer(CENTRE, id, lmv.id());
        assertTrue(infirmiers.lister(CENTRE, 0, 20).items().get(0).affectations().isEmpty());
    }

    @Test
    void the_week_shows_understaffing_then_coverage_once_nurses_are_planned() {
        patients(5, JourSemaine.LUNDI);
        UUID a = infirmier(CENTRE, "M1", "Amrani", false).infirmier().id();
        UUID b = infirmier(CENTRE, "M2", "Benali", false).infirmier().id();
        affectations.ajouter(CENTRE, a, SALLE, MATIN, EnumSet.of(JourSemaine.LUNDI));

        CasePresence seul = caseLundi();
        assertEquals(StatutCase.SOUS_EFFECTIF, seul.statut());
        assertEquals(2, seul.requis());
        assertEquals(5, seul.patients());

        affectations.ajouter(CENTRE, b, SALLE, MATIN, EnumSet.of(JourSemaine.LUNDI));
        assertEquals(StatutCase.COUVERT, caseLundi().statut());
    }

    @Test
    void the_safety_ratio_of_the_center_changes_the_requirement() {
        patients(5, JourSemaine.LUNDI);
        parametres.enregistrer(CENTRE, new PlanningParametres(EnumSet.allOf(JourSemaine.class), Set.of(), 2));

        assertEquals(3, caseLundi().requis());
    }

    @Test
    void an_absence_uncovers_the_slot_and_an_eligible_replacement_covers_it_again() {
        patients(3, JourSemaine.LUNDI);
        UUID titulaire = infirmier(CENTRE, "M1", "Amrani", false).infirmier().id();
        UUID renfort = infirmier(CENTRE, "M2", "Benali", false).infirmier().id();
        affectations.ajouter(CENTRE, titulaire, SALLE, MATIN, EnumSet.of(JourSemaine.LUNDI));
        assertEquals(StatutCase.COUVERT, caseLundi().statut());

        AbsenceInfirmier absence = absences.declarer(CENTRE, titulaire, LUNDI, LUNDI, TypeAbsence.MALADIE, "Grippe");
        CasePresence decouverte = caseLundi();
        assertEquals(StatutCase.SOUS_EFFECTIF, decouverte.statut());
        assertEquals(1, decouverte.absents().size());
        assertEquals(1, presence.alertes(CENTRE, LUNDI, 1).size());
        assertEquals(List.of("Benali"),
                presence.remplacants(CENTRE, LUNDI, SALLE, MATIN).stream().map(c -> c.nom()).toList());

        assertEquals("REMPLACEMENT_INELIGIBLE", assertThrows(BusinessException.class,
                () -> remplacements.affecter(CENTRE, LUNDI, SALLE, MATIN, titulaire, null)).getCode());
        RemplacementInfirmier r = remplacements.affecter(CENTRE, LUNDI, SALLE, MATIN, renfort, titulaire);
        assertEquals(StatutCase.COUVERT, caseLundi().statut());
        assertTrue(caseLundi().presents().get(0).remplacant());
        assertTrue(presence.alertes(CENTRE, LUNDI, 1).isEmpty());

        remplacements.annuler(CENTRE, r.id());
        assertEquals(StatutCase.SOUS_EFFECTIF, caseLundi().statut());
        absences.supprimer(CENTRE, absence.id());
        assertEquals(StatutCase.COUVERT, caseLundi().statut());
    }

    @Test
    void nurses_and_replacements_of_another_center_never_cover_this_center() {
        patients(3, JourSemaine.LUNDI);
        UUID etranger = infirmier(AUTRE_CENTRE, "M1", "Etranger", false).infirmier().id();

        assertEquals(StatutCase.SOUS_EFFECTIF, caseLundi().statut());
        assertTrue(presence.remplacants(CENTRE, LUNDI, SALLE, MATIN).isEmpty());
        assertThrows(BusinessException.class,
                () -> remplacements.affecter(CENTRE, LUNDI, SALLE, MATIN, etranger, null));
    }

    @Test
    void a_deactivated_nurse_leaves_the_planning_and_alert_horizon_is_bounded() {
        patients(2, JourSemaine.LUNDI);
        UUID a = infirmier(CENTRE, "M1", "Amrani", false).infirmier().id();
        affectations.ajouter(CENTRE, a, SALLE, MATIN, EnumSet.of(JourSemaine.LUNDI));
        assertEquals(StatutCase.COUVERT, caseLundi().statut());

        infirmiers.desactiver(CENTRE, a);

        assertEquals(StatutCase.SOUS_EFFECTIF, caseLundi().statut());
        assertThrows(IllegalArgumentException.class, () -> presence.alertes(CENTRE, LUNDI, 0));
        assertThrows(IllegalArgumentException.class,
                () -> presence.alertes(CENTRE, LUNDI, PresenceInfirmierQueryService.HORIZON_MAX_JOURS + 1));
    }

    @Test
    void the_monthly_workload_is_paginated_and_counts_rotation_and_replacements() {
        UUID a = infirmier(CENTRE, "M1", "Amrani", false).infirmier().id();
        UUID b = infirmier(CENTRE, "M2", "Benali", false).infirmier().id();
        affectations.ajouter(CENTRE, a, SALLE, MATIN, EnumSet.of(JourSemaine.LUNDI));
        patients(1, JourSemaine.LUNDI);
        remplacements.affecter(CENTRE, LocalDate.of(2026, 10, 5), SALLE, MATIN, b, null);

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

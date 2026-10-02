package com.hemodialyse.backend.domain.planning.service;

import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.Planning.Alerte;
import com.hemodialyse.backend.domain.planning.model.Planning.CaseGrille;
import com.hemodialyse.backend.domain.planning.model.Planning.CreneauRef;
import com.hemodialyse.backend.domain.planning.model.Planning.DemandePlacement;
import com.hemodialyse.backend.domain.planning.model.Planning.DonneesPlanning;
import com.hemodialyse.backend.domain.planning.model.Planning.Fermeture;
import com.hemodialyse.backend.domain.planning.model.Planning.GenerateurRef;
import com.hemodialyse.backend.domain.planning.model.Planning.Occupation;
import com.hemodialyse.backend.domain.planning.model.Planning.Proposition;
import com.hemodialyse.backend.domain.planning.model.Planning.Raison;
import com.hemodialyse.backend.domain.planning.model.Planning.ResultatProposition;
import com.hemodialyse.backend.domain.planning.model.Planning.SalleRef;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contraintes de l'aide au placement : jours d'ouverture du centre, isolement des patients à risque infectieux et
 * fermetures datées.
 */
class PlanificationAffectationContraintesTest {

    private static final SalleRef SALLE = new SalleRef(UUID.randomUUID(), "Salle A");
    private static final SalleRef ISO = new SalleRef(UUID.randomUUID(), "Salle ISO");
    private static final CreneauRef MATIN = new CreneauRef(UUID.randomUUID(), "Matin", 1);
    private static final GenerateurRef G_SALLE = new GenerateurRef(UUID.randomUUID(), "G01", SALLE.id());
    private static final GenerateurRef G_SALLE_2 = new GenerateurRef(UUID.randomUUID(), "G02", SALLE.id());
    private static final GenerateurRef G_ISO = new GenerateurRef(UUID.randomUUID(), "G90", ISO.id());
    private static final GenerateurRef G_ISO_2 = new GenerateurRef(UUID.randomUUID(), "G91", ISO.id());

    private static final Set<JourSemaine> SIX_JOURS = EnumSet.complementOf(EnumSet.of(JourSemaine.VENDREDI));

    private static DonneesPlanning donnees(Set<JourSemaine> ouverts, Set<UUID> isolement, List<Occupation> occupations,
                                           List<Fermeture> fermetures) {
        return new DonneesPlanning(List.of(SALLE, ISO), List.of(MATIN), List.of(G_SALLE, G_SALLE_2, G_ISO, G_ISO_2),
                occupations, ouverts, isolement, fermetures);
    }

    private static Occupation occupation(SalleRef salle, GenerateurRef g, boolean aRisque, Set<JourSemaine> jours) {
        return new Occupation(UUID.randomUUID(), salle.id(), MATIN.id(), g.id(), jours, aRisque);
    }

    private static DemandePlacement demande(boolean aRisque) {
        return new DemandePlacement(3, Set.of(), null, null, aRisque);
    }

    // ───────────────────────────── Jours d'ouverture ─────────────────────────────

    @Test
    void should_never_propose_a_weekly_closed_day() {
        DonneesPlanning d = donnees(SIX_JOURS, Set.of(), List.of(), List.of());

        List<Proposition> p = PlanificationAffectationService.proposer(d, demande(false), 20);

        assertFalse(p.isEmpty());
        assertTrue(p.stream().allMatch(x -> !x.jours().contains(JourSemaine.VENDREDI)));
    }

    @Test
    void should_alert_and_propose_nothing_when_an_imposed_day_is_closed() {
        DonneesPlanning d = donnees(SIX_JOURS, Set.of(), List.of(), List.of());
        DemandePlacement imposes = new DemandePlacement(3,
                EnumSet.of(JourSemaine.LUNDI, JourSemaine.MERCREDI, JourSemaine.VENDREDI), null, null, false);

        ResultatProposition r = PlanificationAffectationService.rechercher(d, imposes, 10);

        assertTrue(r.propositions().isEmpty());
        assertEquals(List.of(Alerte.JOURS_IMPOSES_FERMES), r.alertes());
    }

    @Test
    void the_grid_shows_no_capacity_on_a_closed_day() {
        DonneesPlanning d = donnees(SIX_JOURS, Set.of(), List.of(), List.of());

        List<CaseGrille> grille = PlanificationAffectationService.grille(d);

        assertTrue(grille.stream().filter(c -> c.jour() == JourSemaine.VENDREDI).allMatch(c -> c.capacite() == 0));
        assertTrue(grille.stream().filter(c -> c.jour() == JourSemaine.LUNDI).anyMatch(c -> c.capacite() > 0));
    }

    // ───────────────────────────── Isolement ─────────────────────────────

    @Test
    void a_patient_at_risk_is_only_placed_in_an_isolation_room() {
        DonneesPlanning d = donnees(EnumSet.allOf(JourSemaine.class), Set.of(ISO.id()), List.of(), List.of());

        List<Proposition> p = PlanificationAffectationService.proposer(d, demande(true), 20);

        assertFalse(p.isEmpty());
        assertTrue(p.stream().allMatch(x -> x.salle().equals(ISO)));
        assertTrue(p.get(0).raisons().contains(Raison.SALLE_ISOLEMENT));
    }

    @Test
    void a_patient_without_risk_is_never_placed_in_an_isolation_room() {
        DonneesPlanning d = donnees(EnumSet.allOf(JourSemaine.class), Set.of(ISO.id()), List.of(), List.of());

        List<Proposition> p = PlanificationAffectationService.proposer(d, demande(false), 20);

        assertFalse(p.isEmpty());
        assertTrue(p.stream().noneMatch(x -> x.salle().equals(ISO)));
    }

    @Test
    void should_alert_when_a_patient_at_risk_has_no_isolation_room_configured() {
        DonneesPlanning d = donnees(EnumSet.allOf(JourSemaine.class), Set.of(), List.of(), List.of());

        ResultatProposition r = PlanificationAffectationService.rechercher(d, demande(true), 10);

        assertTrue(r.propositions().isEmpty());
        assertEquals(List.of(Alerte.AUCUNE_SALLE_ISOLEMENT), r.alertes());
    }

    @Test
    void a_generator_used_by_a_patient_at_risk_is_closed_to_patients_without_risk_and_vice_versa() {
        // G_SALLE sert déjà un patient à risque (cas historique) ; G_ISO sert un patient sans risque
        DonneesPlanning d = donnees(EnumSet.allOf(JourSemaine.class), Set.of(ISO.id()),
                List.of(occupation(SALLE, G_SALLE, true, EnumSet.of(JourSemaine.LUNDI)),
                        occupation(ISO, G_ISO, false, EnumSet.of(JourSemaine.MARDI))), List.of());

        List<Proposition> sain = PlanificationAffectationService.proposer(d, demande(false), 30);
        List<Proposition> risque = PlanificationAffectationService.proposer(d, demande(true), 30);

        assertTrue(sain.stream().noneMatch(x -> x.generateur().equals(G_SALLE)
                || x.generateursAlternatifs().contains(G_SALLE)), "générateur dédié au risque proposé à un patient sain");
        assertTrue(sain.stream().anyMatch(x -> x.generateur().equals(G_SALLE_2)));
        assertTrue(risque.stream().noneMatch(x -> x.generateur().equals(G_ISO)
                || x.generateursAlternatifs().contains(G_ISO)), "générateur d'un patient sain proposé à un patient à risque");
        assertTrue(risque.stream().anyMatch(x -> x.generateur().equals(G_ISO_2)));
    }

    // ───────────────────────────── Fermetures datées ─────────────────────────────

    @Test
    void should_report_the_next_dated_closure_falling_on_a_proposed_day() {
        // 2026-11-01 est un dimanche, 2026-11-02 un lundi, 2026-11-09 un lundi
        List<Fermeture> fermetures = List.of(
                new Fermeture(LocalDate.of(2026, 11, 9), "Férié bis"),
                new Fermeture(LocalDate.of(2026, 11, 2), "Fermeture exceptionnelle"));
        DonneesPlanning d = new DonneesPlanning(List.of(SALLE), List.of(MATIN), List.of(G_SALLE), List.of(),
                EnumSet.allOf(JourSemaine.class), Set.of(), fermetures);
        DemandePlacement lmv = new DemandePlacement(3,
                EnumSet.of(JourSemaine.LUNDI, JourSemaine.MERCREDI, JourSemaine.VENDREDI), null, null, false);

        Proposition p = PlanificationAffectationService.proposer(d, lmv, 5).get(0);

        assertEquals(1, p.fermetures().size());
        assertEquals(JourSemaine.LUNDI, p.fermetures().get(0).jour());
        assertEquals(LocalDate.of(2026, 11, 2), p.fermetures().get(0).date());
        assertEquals("Fermeture exceptionnelle", p.fermetures().get(0).motif());
    }

    @Test
    void a_proposal_without_any_closure_on_its_days_has_no_closure_notice() {
        DonneesPlanning d = new DonneesPlanning(List.of(SALLE), List.of(MATIN), List.of(G_SALLE), List.of(),
                EnumSet.allOf(JourSemaine.class), Set.of(), List.of(new Fermeture(LocalDate.of(2026, 11, 7), "Samedi")));
        DemandePlacement lmv = new DemandePlacement(3,
                EnumSet.of(JourSemaine.LUNDI, JourSemaine.MERCREDI, JourSemaine.VENDREDI), null, null, false);

        assertTrue(PlanificationAffectationService.proposer(d, lmv, 5).get(0).fermetures().isEmpty());
    }
}

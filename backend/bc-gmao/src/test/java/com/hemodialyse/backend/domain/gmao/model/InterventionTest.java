package com.hemodialyse.backend.domain.gmao.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de domaine purs (JUnit 5, sans Spring/JPA — AGENTS.md §3) pour l'agrégat Intervention.
 */
class InterventionTest {

    @Test
    void demarrer_then_terminer_should_transition_statut() {
        Intervention i = intervention();

        i.demarrer(UUID.randomUUID());
        assertEquals(StatutIntervention.EN_COURS, i.getStatut());

        i.terminer("Pièce remplacée", StatutEquipement.EN_SERVICE, OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1), UUID.randomUUID());
        assertEquals(StatutIntervention.TERMINEE, i.getStatut());
        assertEquals("Pièce remplacée", i.getActions());
        assertNotNull(i.getDateFin());
    }

    @Test
    void creer_should_require_a_valid_equipment_state_at_intervention_time() {
        assertThrows(IllegalArgumentException.class, () -> Intervention.creer(
                UUID.randomUUID(), UUID.randomUUID(), TypeIntervention.CURATIVE, OffsetDateTime.now(ZoneOffset.UTC), "Panne",
                null, null, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> Intervention.creer(
                UUID.randomUUID(), UUID.randomUUID(), TypeIntervention.CURATIVE, OffsetDateTime.now(ZoneOffset.UTC), "Panne",
                null, StatutEquipement.REFORME, UUID.randomUUID()));
    }

    @Test
    void terminer_should_record_the_state_after_and_allow_a_reform_proposal() {
        Intervention i = intervention();
        i.demarrer(UUID.randomUUID());

        i.terminer("Irréparable", StatutEquipement.A_REFORMER, OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1), UUID.randomUUID());

        assertEquals(StatutEquipement.EN_MAINTENANCE, i.getEtatEquipementAvant());
        assertEquals(StatutEquipement.A_REFORMER, i.getEtatEquipementApres());
    }

    @Test
    void terminer_should_reject_a_missing_or_final_state_after() {
        Intervention i = intervention();
        i.demarrer(UUID.randomUUID());

        assertThrows(IllegalArgumentException.class, () -> i.terminer("Fait", null, OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1), UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,
                () -> i.terminer("Fait", StatutEquipement.REFORME, OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1), UUID.randomUUID()));
        assertEquals(StatutIntervention.EN_COURS, i.getStatut());
    }

    @Test
    void demarrer_should_keep_the_start_date_and_time_entered_in_the_sheet() {
        OffsetDateTime saisie = OffsetDateTime.of(2026, 3, 10, 8, 30, 0, 0, ZoneOffset.UTC);
        Intervention i = Intervention.creer(UUID.randomUUID(), UUID.randomUUID(), TypeIntervention.CURATIVE,
                saisie, "Panne", null, StatutEquipement.EN_MAINTENANCE, UUID.randomUUID());

        i.demarrer(UUID.randomUUID());

        assertEquals(saisie, i.getDateDebut());
    }

    @Test
    void terminer_should_use_the_end_date_entered_and_reject_one_before_the_start() {
        OffsetDateTime debut = OffsetDateTime.of(2026, 3, 10, 8, 30, 0, 0, ZoneOffset.UTC);
        Intervention i = Intervention.creer(UUID.randomUUID(), UUID.randomUUID(), TypeIntervention.CURATIVE,
                debut, "Panne", null, StatutEquipement.EN_MAINTENANCE, UUID.randomUUID());
        i.demarrer(UUID.randomUUID());

        assertThrows(IllegalArgumentException.class, () -> i.terminer(
                "Fait", StatutEquipement.EN_SERVICE, debut.minusMinutes(1), UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> i.terminer(
                "Fait", StatutEquipement.EN_SERVICE, null, UUID.randomUUID()));

        OffsetDateTime fin = debut.plusHours(2).plusMinutes(15);
        i.terminer("Fait", StatutEquipement.EN_SERVICE, fin, UUID.randomUUID());
        assertEquals(fin, i.getDateFin());
    }

    @Test
    void cost_lines_can_still_be_added_once_finished_but_not_when_cancelled() {
        Intervention terminee = terminee(OffsetDateTime.of(2026, 1, 5, 8, 0, 0, 0, ZoneOffset.UTC), OffsetDateTime.of(2026, 1, 5, 10, 0, 0, 0, ZoneOffset.UTC));
        terminee.ajouterLigneCout(LigneCoutIntervention.creer(
                        TypeLigneCout.PIECE, "Facture pièce reçue après coup", BigDecimal.ONE, new BigDecimal("900"), null),
                UUID.randomUUID());
        assertEquals(1, terminee.getLignesCout().size());

        Intervention annulee = intervention();
        annulee.annuler("Doublon", UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> annulee.ajouterLigneCout(LigneCoutIntervention.creer(
                TypeLigneCout.PIECE, "x", BigDecimal.ONE, BigDecimal.TEN, null), UUID.randomUUID()));
    }

    @Test
    void terminer_should_reject_an_intervention_not_in_progress() {
        Intervention i = intervention();

        assertThrows(IllegalStateException.class, () -> i.terminer("x", StatutEquipement.EN_SERVICE, OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1), UUID.randomUUID()));
    }

    @Test
    void annuler_should_reject_an_already_finished_intervention() {
        Intervention i = intervention();
        i.demarrer(UUID.randomUUID());
        i.terminer("Fait", StatutEquipement.EN_SERVICE, OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1), UUID.randomUUID());

        assertThrows(IllegalStateException.class, () -> i.annuler("raison", UUID.randomUUID()));
    }

    @Test
    void ajouterLigneCout_should_accumulate_and_compute_coutTotal() {
        Intervention i = intervention();

        i.ajouterLigneCout(LigneCoutIntervention.creer(
                        TypeLigneCout.PIECE, "Filtre RO", new BigDecimal("2"), new BigDecimal("1500.00"), null),
                UUID.randomUUID());
        i.ajouterLigneCout(LigneCoutIntervention.creer(
                        TypeLigneCout.MAIN_OEUVRE, "2h de main d'œuvre", new BigDecimal("2"), new BigDecimal("2000.00"), null),
                UUID.randomUUID());

        assertEquals(2, i.getLignesCout().size());
        assertEquals(new BigDecimal("7000.00"), i.coutTotal());
    }

    @Test
    void coutTotal_should_be_zero_without_any_cost_line() {
        assertEquals(BigDecimal.ZERO, intervention().coutTotal());
    }

    @Test
    void appliquerTarifIntervenant_should_add_labour_line_from_duration_and_rate() {
        Intervention i = terminee(OffsetDateTime.of(2026, 1, 5, 8, 0, 0, 0, ZoneOffset.UTC), OffsetDateTime.of(2026, 1, 5, 10, 30, 0, 0, ZoneOffset.UTC));

        assertTrue(i.appliquerTarifIntervenant(new BigDecimal("2000"), TypeIntervenant.EXTERNE, UUID.randomUUID()));

        assertEquals(1, i.getLignesCout().size());
        assertEquals(TypeLigneCout.INTERVENANT, i.getLignesCout().get(0).getType());
        assertEquals(0, new BigDecimal("5000").compareTo(i.coutTotal()));
    }

    @Test
    void appliquerTarifIntervenant_should_be_idempotent_and_skip_missing_rate() {
        Intervention i = terminee(OffsetDateTime.of(2026, 1, 5, 8, 0, 0, 0, ZoneOffset.UTC), OffsetDateTime.of(2026, 1, 5, 10, 0, 0, 0, ZoneOffset.UTC));

        assertFalse(i.appliquerTarifIntervenant(null, TypeIntervenant.EXTERNE, UUID.randomUUID()));
        assertTrue(i.appliquerTarifIntervenant(new BigDecimal("1000"), TypeIntervenant.EXTERNE, UUID.randomUUID()));
        assertFalse(i.appliquerTarifIntervenant(new BigDecimal("1000"), TypeIntervenant.EXTERNE, UUID.randomUUID()));
        assertEquals(1, i.getLignesCout().size());
    }

    @Test
    void appliquerTarifIntervenant_should_ignore_an_intervention_not_finished() {
        assertFalse(intervention().appliquerTarifIntervenant(new BigDecimal("1000"), TypeIntervenant.EXTERNE, UUID.randomUUID()));
    }

    @Test
    void appliquerTarifIntervenant_should_bill_fees_for_an_external_and_labour_for_an_internal() {
        Intervention externe = terminee(OffsetDateTime.of(2026, 1, 5, 8, 0, 0, 0, ZoneOffset.UTC), OffsetDateTime.of(2026, 1, 5, 10, 0, 0, 0, ZoneOffset.UTC));
        Intervention interne = terminee(OffsetDateTime.of(2026, 1, 5, 8, 0, 0, 0, ZoneOffset.UTC), OffsetDateTime.of(2026, 1, 5, 10, 0, 0, 0, ZoneOffset.UTC));

        externe.appliquerTarifIntervenant(new BigDecimal("1000"), TypeIntervenant.EXTERNE, UUID.randomUUID());
        interne.appliquerTarifIntervenant(new BigDecimal("1000"), TypeIntervenant.INTERNE, UUID.randomUUID());

        assertEquals(TypeLigneCout.INTERVENANT, externe.getLignesCout().get(0).getType());
        assertTrue(externe.getLignesCout().get(0).isAutomatique());
        assertEquals(TypeLigneCout.MAIN_OEUVRE, interne.getLignesCout().get(0).getType());
        assertTrue(interne.getLignesCout().get(0).isAutomatique());
    }

    @Test
    void appliquerTarifIntervenant_should_not_double_count_a_manual_labour_line_for_an_internal() {
        Intervention i = terminee(OffsetDateTime.of(2026, 1, 5, 8, 0, 0, 0, ZoneOffset.UTC), OffsetDateTime.of(2026, 1, 5, 10, 0, 0, 0, ZoneOffset.UTC));
        i.ajouterLigneCout(LigneCoutIntervention.creer(
                TypeLigneCout.MAIN_OEUVRE, "Main d'œuvre saisie", new BigDecimal("3"), new BigDecimal("800"), null), UUID.randomUUID());

        assertFalse(i.appliquerTarifIntervenant(new BigDecimal("1000"), TypeIntervenant.INTERNE, UUID.randomUUID()));
        assertEquals(1, i.getLignesCout().size());
    }

    @Test
    void rectifier_should_reopen_a_finished_intervention_with_a_traced_reason() {
        UUID auteur = UUID.randomUUID();
        Intervention i = terminee(OffsetDateTime.of(2026, 1, 5, 8, 0, 0, 0, ZoneOffset.UTC), OffsetDateTime.of(2026, 1, 5, 10, 0, 0, 0, ZoneOffset.UTC));
        i.appliquerTarifIntervenant(new BigDecimal("1000"), TypeIntervenant.INTERNE, UUID.randomUUID());
        i.ajouterLigneCout(LigneCoutIntervention.creer(
                TypeLigneCout.PIECE, "Joint", BigDecimal.ONE, new BigDecimal("500"), null), UUID.randomUUID());

        i.rectifier("  Heure de fin erronée ", null, auteur);

        assertEquals(StatutIntervention.EN_COURS, i.getStatut());
        assertNull(i.getDateFin());
        assertNull(i.getEtatEquipementApres());
        assertEquals(1, i.getLignesCout().size());
        assertEquals(TypeLigneCout.PIECE, i.getLignesCout().get(0).getType());
        assertEquals(1, i.getRectifications().size());
        assertEquals("Heure de fin erronée", i.getRectifications().get(0).motif());
        var events = i.chronologie();
        assertEquals(EvenementIntervention.Type.RECTIFIEE, events.get(events.size() - 1).type());
        assertEquals("Heure de fin erronée", events.get(events.size() - 1).detail());
        assertEquals(auteur, events.get(events.size() - 1).par());
    }

    @Test
    void rectifier_then_close_again_should_recompute_the_automatic_line_on_the_new_dates() {
        OffsetDateTime debut = OffsetDateTime.of(2026, 1, 5, 8, 0, 0, 0, ZoneOffset.UTC);
        Intervention i = terminee(debut, debut.plusHours(2));
        i.appliquerTarifIntervenant(new BigDecimal("1000"), TypeIntervenant.INTERNE, UUID.randomUUID());
        assertEquals(0, new BigDecimal("2000").compareTo(i.coutTotal()));

        i.rectifier("Durée sous-estimée", debut.minusHours(1), UUID.randomUUID());
        i.terminer("Refait", StatutEquipement.EN_SERVICE, debut.plusHours(2), UUID.randomUUID());
        i.appliquerTarifIntervenant(new BigDecimal("1000"), TypeIntervenant.INTERNE, UUID.randomUUID());

        assertEquals(StatutIntervention.TERMINEE, i.getStatut());
        assertEquals(0, new BigDecimal("3000").compareTo(i.coutTotal()));
    }

    @Test
    void rectifier_should_only_apply_to_a_finished_intervention_and_require_a_reason() {
        assertThrows(IllegalStateException.class, () -> intervention().rectifier("x", null, UUID.randomUUID()));
        Intervention i = terminee(OffsetDateTime.of(2026, 1, 5, 8, 0, 0, 0, ZoneOffset.UTC), OffsetDateTime.of(2026, 1, 5, 10, 0, 0, 0, ZoneOffset.UTC));
        assertThrows(IllegalArgumentException.class, () -> i.rectifier("  ", null, UUID.randomUUID()));
        assertEquals(StatutIntervention.TERMINEE, i.getStatut());
    }

    @Test
    void supprimerLigneCout_should_only_work_on_an_open_intervention() {
        Intervention i = intervention();
        LigneCoutIntervention ligne = LigneCoutIntervention.creer(
                TypeLigneCout.PIECE, "Joint", BigDecimal.ONE, new BigDecimal("500"), null);
        i.ajouterLigneCout(ligne, UUID.randomUUID());
        i.demarrer(UUID.randomUUID());

        i.supprimerLigneCout(ligne.getId(), UUID.randomUUID());
        assertTrue(i.getLignesCout().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> i.supprimerLigneCout(UUID.randomUUID(), UUID.randomUUID()));

        Intervention fermee = terminee(OffsetDateTime.of(2026, 1, 5, 8, 0, 0, 0, ZoneOffset.UTC), OffsetDateTime.of(2026, 1, 5, 10, 0, 0, 0, ZoneOffset.UTC));
        fermee.ajouterLigneCout(ligne, UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> fermee.supprimerLigneCout(ligne.getId(), UUID.randomUUID()));
    }

    @Test
    void creer_should_keep_symptom_priority_and_deadline_and_reject_a_deadline_before_the_start() {
        OffsetDateTime debut = OffsetDateTime.of(2026, 3, 10, 8, 0, 0, 0, ZoneOffset.UTC);
        Intervention i = Intervention.creer(UUID.randomUUID(), UUID.randomUUID(), TypeIntervention.URGENTE, debut,
                "Alarme", null, StatutEquipement.HORS_SERVICE, "  Fuite côté dialysat  ",
                PrioriteIntervention.URGENTE, debut.plusHours(4), UUID.randomUUID());

        assertEquals("Fuite côté dialysat", i.getSymptome());
        assertEquals(PrioriteIntervention.URGENTE, i.getPriorite());
        assertEquals(debut.plusHours(4), i.getEcheance());
        assertThrows(IllegalArgumentException.class, () -> Intervention.creer(
                UUID.randomUUID(), UUID.randomUUID(), TypeIntervention.URGENTE, debut, "x", null,
                StatutEquipement.HORS_SERVICE, null, PrioriteIntervention.HAUTE, debut.minusMinutes(1), UUID.randomUUID()));
    }

    @Test
    void enRetard_and_echeanceDepassee_should_flag_only_open_interventions() {
        OffsetDateTime debut = OffsetDateTime.of(2026, 3, 10, 8, 0, 0, 0, ZoneOffset.UTC);
        Intervention planifiee = Intervention.creer(UUID.randomUUID(), UUID.randomUUID(), TypeIntervention.PREVENTIVE,
                debut, "Contrôle", null, StatutEquipement.EN_SERVICE, null, PrioriteIntervention.NORMALE,
                debut.plusHours(2), UUID.randomUUID());

        assertFalse(planifiee.enRetard(debut.minusMinutes(1)));
        assertTrue(planifiee.enRetard(debut.plusMinutes(1)));
        assertFalse(planifiee.echeanceDepassee(debut.plusHours(1)));
        assertTrue(planifiee.echeanceDepassee(debut.plusHours(3)));

        planifiee.annuler("Doublon", UUID.randomUUID());
        assertFalse(planifiee.enRetard(debut.plusDays(1)));
        assertFalse(planifiee.echeanceDepassee(debut.plusDays(1)));
    }

    @Test
    void chronologie_should_record_who_created_started_closed_with_the_cause() {
        UUID createur = UUID.randomUUID();
        UUID technicien = UUID.randomUUID();
        Intervention i = Intervention.creer(UUID.randomUUID(), UUID.randomUUID(), TypeIntervention.CURATIVE,
                OffsetDateTime.now(ZoneOffset.UTC), "Panne", null, StatutEquipement.EN_MAINTENANCE, createur);
        i.demarrer(technicien);
        i.terminer("Pompe changée", StatutEquipement.EN_SERVICE, OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5),
                "Usure de la pompe", technicien);

        var events = i.chronologie();

        assertEquals("Usure de la pompe", i.getCause());
        assertEquals(java.util.List.of(EvenementIntervention.Type.CREEE, EvenementIntervention.Type.DEMARREE,
                EvenementIntervention.Type.TERMINEE), events.stream().map(EvenementIntervention::type).toList());
        assertEquals(createur, events.get(0).par());
        assertEquals(technicien, events.get(1).par());
        assertEquals(technicien, events.get(2).par());
    }

    private Intervention terminee(OffsetDateTime debut, OffsetDateTime fin) {
        return Intervention.reconstruct(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), TypeIntervention.CURATIVE,
                StatutIntervention.TERMINEE, debut, fin, UUID.randomUUID(), "Panne", "Fait", null, null,
                debut, fin, UUID.randomUUID(), UUID.randomUUID(), null,
                StatutEquipement.EN_MAINTENANCE, StatutEquipement.EN_SERVICE);
    }

    private Intervention intervention() {
        return Intervention.creer(
                UUID.randomUUID(), UUID.randomUUID(), TypeIntervention.CURATIVE,
                OffsetDateTime.now(ZoneOffset.UTC), "Panne pompe", UUID.randomUUID(), StatutEquipement.EN_MAINTENANCE, UUID.randomUUID());
    }
}

package com.hemodialyse.backend.domain.gmao.service;

import com.hemodialyse.backend.domain.gmao.model.EquipementStatutHistorique;
import com.hemodialyse.backend.domain.gmao.model.Intervention;
import com.hemodialyse.backend.domain.gmao.model.StatutEquipement;
import com.hemodialyse.backend.domain.gmao.model.StatutIntervention;
import com.hemodialyse.backend.domain.gmao.model.TypeIntervention;
import com.hemodialyse.backend.domain.gmao.service.IndicateursIntervention.Resultat;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IndicateursInterventionTest {

    private final OffsetDateTime maintenant = OffsetDateTime.of(2026, 6, 1, 12, 0, 0, 0, ZoneOffset.UTC);
    private final UUID equipementId = UUID.randomUUID();

    @Test
    void should_compare_the_intervention_downtime_to_the_equipment_downtime_over_12_months() {
        OffsetDateTime debut = maintenant.minusDays(10);
        OffsetDateTime fin = debut.plusHours(4);
        Intervention i = terminee(debut.minusHours(6), debut, fin);
        List<EquipementStatutHistorique> historique = List.of(
                entree(StatutEquipement.EN_SERVICE, StatutEquipement.EN_MAINTENANCE, debut),
                entree(StatutEquipement.EN_MAINTENANCE, StatutEquipement.EN_SERVICE, fin),
                entree(StatutEquipement.EN_SERVICE, StatutEquipement.HORS_SERVICE, maintenant.minusDays(3)),
                entree(StatutEquipement.HORS_SERVICE, StatutEquipement.EN_SERVICE, maintenant.minusDays(3).plusHours(12)));

        Resultat r = IndicateursIntervention.calculer(i, historique, StatutEquipement.EN_SERVICE, maintenant);

        assertEquals(6 * 60, r.delaiPriseEnChargeMinutes());
        assertEquals(4 * 60, r.dureeInterventionMinutes());
        assertEquals(4 * 60, r.indisponibiliteInterventionMinutes());
        assertEquals(16 * 60, r.indisponibiliteEquipement12MoisMinutes());
        assertEquals(25.0, r.partIndisponibilite12MoisPct(), 0.001);
    }

    @Test
    void should_have_no_share_when_the_equipment_was_never_unavailable() {
        OffsetDateTime debut = maintenant.minusDays(1);
        Intervention i = terminee(debut, debut, debut.plusHours(1));

        Resultat r = IndicateursIntervention.calculer(i, List.of(), StatutEquipement.EN_SERVICE, maintenant);

        assertEquals(0, r.indisponibiliteInterventionMinutes());
        assertNull(r.partIndisponibilite12MoisPct());
    }

    @Test
    void should_never_report_a_negative_lead_time_for_a_backdated_intervention() {
        OffsetDateTime debut = maintenant.minusDays(5);
        Intervention i = terminee(maintenant.minusDays(1), debut, debut.plusHours(2));

        assertEquals(0, IndicateursIntervention.calculer(i, List.of(), StatutEquipement.EN_SERVICE, maintenant)
                .delaiPriseEnChargeMinutes());
    }

    private Intervention terminee(OffsetDateTime creation, OffsetDateTime debut, OffsetDateTime fin) {
        return Intervention.reconstruct(
                UUID.randomUUID(), equipementId, UUID.randomUUID(), TypeIntervention.CURATIVE,
                StatutIntervention.TERMINEE, debut, fin, null, "Panne", "Fait", null, null,
                creation, fin, UUID.randomUUID(), UUID.randomUUID(), null,
                StatutEquipement.EN_MAINTENANCE, StatutEquipement.EN_SERVICE);
    }

    private EquipementStatutHistorique entree(StatutEquipement before, StatutEquipement after, OffsetDateTime at) {
        return EquipementStatutHistorique.reconstruct(
                UUID.randomUUID(), equipementId, UUID.randomUUID(), before, after, "test", at, UUID.randomUUID());
    }
}

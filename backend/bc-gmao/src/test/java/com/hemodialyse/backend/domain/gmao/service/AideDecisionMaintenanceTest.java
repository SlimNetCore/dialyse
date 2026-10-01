package com.hemodialyse.backend.domain.gmao.service;

import com.hemodialyse.backend.domain.gmao.model.StatutEquipement;
import com.hemodialyse.backend.domain.gmao.service.AideDecisionMaintenance.AnalyseCout;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AideDecisionMaintenanceTest {

    private final AideDecisionMaintenance aide = new AideDecisionMaintenance(new BigDecimal("0.60"));

    @Test
    void should_compute_possession_cost_ratio_and_cost_per_downtime_hour() {
        AnalyseCout a = aide.analyser(StatutEquipement.EN_SERVICE, new BigDecimal("1000000"),
                new BigDecimal("300000"), new BigDecimal("100000"), 50);

        assertEquals(0, new BigDecimal("1300000").compareTo(a.coutPossession()));
        assertEquals(0, new BigDecimal("0.3").compareTo(a.ratioMaintenance()));
        assertEquals(0, new BigDecimal("2000").compareTo(a.coutParHeureIndisponibilite()));
        assertFalse(a.reformeRecommandee());
    }

    @Test
    void should_recommend_reforme_when_ratio_reaches_threshold() {
        AnalyseCout a = aide.analyser(StatutEquipement.EN_SERVICE, new BigDecimal("1000000"),
                new BigDecimal("600000"), BigDecimal.ZERO, 0);

        assertTrue(a.reformeRecommandee());
        assertNull(a.coutParHeureIndisponibilite());
    }

    @Test
    void should_not_recommend_reforme_for_already_retired_or_unpriced_equipment() {
        assertFalse(aide.analyser(StatutEquipement.REFORME, new BigDecimal("1000"), new BigDecimal("5000"),
                BigDecimal.ZERO, 0).reformeRecommandee());
        AnalyseCout sansPrix = aide.analyser(StatutEquipement.EN_SERVICE, null, new BigDecimal("5000"),
                BigDecimal.ZERO, 0);
        assertNull(sansPrix.ratioMaintenance());
        assertFalse(sansPrix.reformeRecommandee());
    }

    @Test
    void should_always_recommend_reforme_when_state_is_a_reformer() {
        assertTrue(aide.analyser(StatutEquipement.A_REFORMER, new BigDecimal("1000000"), BigDecimal.ZERO,
                BigDecimal.ZERO, 0).reformeRecommandee());
    }

    @Test
    void should_reject_non_positive_threshold() {
        assertThrows(IllegalArgumentException.class, () -> new AideDecisionMaintenance(BigDecimal.ZERO));
    }
}

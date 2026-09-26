package com.hemodialyse.backend.application.direction;

import com.hemodialyse.backend.application.direction.DirectionAlertPolicy.Alert;
import com.hemodialyse.backend.application.direction.DirectionAlertPolicy.Inputs;
import com.hemodialyse.backend.application.direction.DirectionIndicatorsQueryService.Marker;
import com.hemodialyse.backend.domain.medical.kdigo.service.KdigoEvaluationPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectionAlertPolicyTest {

    private static final UUID CENTRE = UUID.randomUUID();

    private static List<String> codes(Inputs in) {
        return DirectionAlertPolicy.evaluate(in).stream().map(Alert::code).toList();
    }

    @Test
    void a_healthy_centre_raises_no_alert() {
        assertTrue(codes(new Inputs(CENTRE, "C", new BigDecimal("95"), new BigDecimal("70"), false, 0, 0, 0)).isEmpty());
    }

    @Test
    void low_kdigo_rates_and_stock_problems_raise_alerts() {
        assertEquals(List.of("KTV_CONFORMITE_BASSE", "HB_HORS_CIBLE", "OBSERVANCE_EN_RETARD", "STOCK_SOUS_SEUIL",
                        "LOTS_PERIMES", "LOTS_PEREMPTION_PROCHE"),
                codes(new Inputs(CENTRE, "C", new BigDecimal("79.9"), new BigDecimal("49.9"), true, 2, 1, 3)));
    }

    @Test
    void masked_rates_never_raise_a_rate_alert() {
        assertTrue(codes(new Inputs(CENTRE, "C", null, null, false, 0, 0, 0)).isEmpty());
    }

    @Test
    void thresholds_are_inclusive_of_the_minimum() {
        assertTrue(codes(new Inputs(CENTRE, "C", DirectionAlertPolicy.KTV_CONFORMITE_MIN,
                DirectionAlertPolicy.HB_DANS_CIBLE_MIN, false, 0, 0, 0)).isEmpty());
    }

    @Test
    void markers_below_the_anonymity_threshold_are_fully_masked() {
        Marker m = DirectionIndicatorsQueryService.marker(
                List.of(new BigDecimal("1.3"), new BigDecimal("1.0")), KdigoEvaluationPolicy.KT_V);
        assertNull(m.evalues());
        assertNull(m.pctDansCible());
    }

    @Test
    void markers_use_the_kdigo_target() {
        List<BigDecimal> values = List.of(new BigDecimal("1.3"), new BigDecimal("1.2"), new BigDecimal("1.5"),
                new BigDecimal("1.1"), new BigDecimal("0.9"));
        Marker m = DirectionIndicatorsQueryService.marker(values, KdigoEvaluationPolicy.KT_V);
        assertEquals(5L, m.evalues());
        assertEquals(new BigDecimal("60.0"), m.pctDansCible());
        assertEquals(new BigDecimal("40.0"), m.pctSousCible());
        assertEquals(new BigDecimal("0.0"), m.pctAuDessus());
    }
}

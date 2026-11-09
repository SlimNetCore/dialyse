package com.hemodialyse.backend.application.direction;

import com.hemodialyse.backend.application.direction.DirectionAlertPolicy.Alert;
import com.hemodialyse.backend.application.direction.DirectionAlertPolicy.Inputs;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Alertes de la Direction. Les deux cas « markers » (masque sous le seuil, taux face à la cible KDIGO) sont joués depuis DM9 (ADR 0101)
 * par {@code RepartitionFaceALaCibleTest} du contexte Dossier médical, qui possède le calcul.
 */
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
}

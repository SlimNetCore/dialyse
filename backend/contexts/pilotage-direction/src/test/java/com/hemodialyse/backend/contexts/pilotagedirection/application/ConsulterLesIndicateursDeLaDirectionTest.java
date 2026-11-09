package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.CentreDeLaSociete;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.CliniquesDesCentres;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.IndicateursCliniquesDuCentre;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.MarqueurClinique;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RegleDuPilotageViolee;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.SocieteSuivie;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.StockDuCentre;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Consulter les indicateurs de la direction (PD6), avec des ports en mémoire : période validée avant toute lecture, stock à la fin. */
class ConsulterLesIndicateursDeLaDirectionTest {

    private static final UUID SOCIETE = UUID.fromString("00000000-0000-0000-0000-0000000000a0");
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final HorlogeDuPilotage HORLOGE = new HorlogeDuPilotage(Clock.fixed(Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC));

    private final List<String> lectures = new ArrayList<>();
    private SocieteSuivie societe = new SocieteSuivie(SOCIETE, "Société", List.of(new CentreDeLaSociete(A, "A", true)));

    private static MarqueurClinique marqueur() {
        return new MarqueurClinique(0L, null, null, null);
    }

    private static final IndicateursCliniquesDuCentre ZERO = new IndicateursCliniquesDuCentre(marqueur(), marqueur(), marqueur(), marqueur(),
            marqueur(), 0L, 0L, 0L, 0L, false, 0L, 0L, 0L);

    private ConsulterLesIndicateursDeLaDirection cas() {
        SocietesSuivies societes = id -> {
            lectures.add("societe");
            return societe;
        };
        IndicateursCliniquesDesCentres cliniques = (centres, periode) -> {
            lectures.add("cliniques " + periode.debut() + ".." + periode.fin());
            Map<UUID, IndicateursCliniquesDuCentre> parCentre = new HashMap<>();
            centres.forEach(c -> parCentre.put(c, ZERO));
            return new CliniquesDesCentres(parCentre, ZERO);
        };
        StockDesCentres stocks = (centres, date) -> {
            lectures.add("stock " + date);
            return Map.of(A, new StockDuCentre(1, 0, 0, 0, new BigDecimal("2.50")));
        };
        return new ConsulterLesIndicateursDeLaDirection(societes, cliniques, stocks, HORLOGE);
    }

    @Test
    void la_periode_par_defaut_va_du_premier_janvier_a_aujourd_hui_et_le_stock_est_evalue_a_la_fin_de_la_periode() {
        var i = cas().execute(SOCIETE, null, null);

        assertEquals(LocalDate.of(2026, 1, 1), i.periode().debut());
        assertEquals(LocalDate.of(2026, 11, 9), i.periode().fin());
        assertEquals(List.of("societe", "cliniques 2026-01-01..2026-11-09", "stock 2026-11-09"), lectures);
        assertEquals(new BigDecimal("2.50"), i.totaux().stock().valeurStock());
    }

    @Test
    void le_stock_est_lu_a_la_date_de_fin_demandee_et_non_a_la_date_du_jour() {
        cas().execute(SOCIETE, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 30));

        assertTrue(lectures.contains("stock 2025-06-30"));
    }

    @Test
    void une_periode_inversee_ou_de_plus_de_cinq_ans_est_refusee_avant_toute_lecture() {
        var inversee = assertThrows(RegleDuPilotageViolee.class, () -> cas().execute(SOCIETE, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)));
        var trop = assertThrows(RegleDuPilotageViolee.class, () -> cas().execute(SOCIETE, LocalDate.of(2020, 1, 1), LocalDate.of(2026, 1, 2)));

        assertEquals("PERIODE_INVALIDE", inversee.code());
        assertEquals("PERIODE_TROP_LONGUE", trop.code());
        assertTrue(lectures.isEmpty());
        assertEquals(LocalDate.of(2026, 1, 1), cas().execute(SOCIETE, LocalDate.of(2021, 1, 1), LocalDate.of(2026, 1, 1)).periode().fin());
    }

    @Test
    void une_societe_sans_centre_ne_lit_pas_le_stock_mais_demande_le_consolide_clinique() {
        societe = new SocieteSuivie(SOCIETE, "Société", List.of());

        var i = cas().execute(SOCIETE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1));

        assertEquals(List.of("societe", "cliniques 2026-01-01..2026-02-01"), lectures);
        assertTrue(i.centres().isEmpty());
        assertTrue(i.alertes().isEmpty());
    }
}

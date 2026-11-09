package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.domain.MesuresDeSeance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.VoletParamedical;
import com.hemodialyse.backend.contexts.seance.domain.VoletsParamedicaux;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contrat des volets paramédicaux (SE7f, ADR 0100) : joué contre le double en mémoire, H2 et PostgreSQL 16 (les trois sous-classes). */
abstract class VoletsParamedicauxContrat {

    static final CentreId A = new CentreId(new UUID(0, 0xA));
    static final CentreId B = new CentreId(new UUID(0, 0xB));
    static final SeanceId SEANCE = new SeanceId(new UUID(2, 1));
    static final Instant T0 = Instant.parse("2026-06-15T10:00:00Z");
    static final Instant T1 = Instant.parse("2026-06-15T11:30:00Z");

    protected abstract VoletsParamedicaux volets();

    /** L'exception de la base (ou du double) sur l'unicité par séance. */
    protected abstract Class<? extends RuntimeException> violationDUnicite();

    static MesuresDeSeance completes() {
        return MesuresDeSeance.saisies(new BigDecimal("70.50"), new BigDecimal("68.00"), "130/80", "120/70", 240, 300, new BigDecimal("2000.00"),
                "HEPARINE", "STANDARD", "RAS");
    }

    @Test
    void should_find_nothing_for_a_session_without_a_sheet_or_for_a_sheet_of_another_centre() {
        var v = volets();
        v.enregistrer(VoletParamedical.ouvrir(new UUID(8, 1), A, SEANCE, completes(), T0));

        assertThat(v.deLaSeance(A, new SeanceId(new UUID(2, 2)))).isEmpty();
        assertThat(v.deLaSeance(B, SEANCE)).isEmpty();
    }

    @Test
    void should_store_a_first_sheet_with_its_ten_measures_and_its_dates() {
        var v = volets();
        v.enregistrer(VoletParamedical.ouvrir(new UUID(8, 1), A, SEANCE, completes(), T0));

        var lu = v.deLaSeance(A, SEANCE).orElseThrow();
        assertThat(lu.id()).isEqualTo(new UUID(8, 1));
        assertThat(lu.mesures()).isEqualTo(completes());
        assertThat(lu.creeLe()).isEqualTo(T0);
        assertThat(lu.modifieLe()).isEqualTo(T0);
    }

    @Test
    void should_replace_every_measure_and_the_modification_date_but_never_the_creation_date_nor_the_identity() {
        var v = volets();
        v.enregistrer(VoletParamedical.ouvrir(new UUID(8, 1), A, SEANCE, completes(), T0));
        var lu = v.deLaSeance(A, SEANCE).orElseThrow();
        var seulement = MesuresDeSeance.saisies(new BigDecimal("71.20"), null, null, null, null, null, null, null, null, null);
        lu.remplacerLesMesures(seulement, T1);
        v.enregistrer(lu);

        var apres = v.deLaSeance(A, SEANCE).orElseThrow();
        assertThat(apres.id()).isEqualTo(new UUID(8, 1));
        assertThat(apres.mesures()).isEqualTo(seulement);
        assertThat(apres.creeLe()).isEqualTo(T0);
        assertThat(apres.modifieLe()).isEqualTo(T1);
    }

    /** KB-SEA-94, reproduit : l'unicité par séance ne regarde pas le centre. */
    @Test
    void should_refuse_a_second_sheet_for_the_same_session_even_under_another_centre_and_leave_the_first_intact() {
        var v = volets();
        v.enregistrer(VoletParamedical.ouvrir(new UUID(8, 1), B, SEANCE, MesuresDeSeance.saisies(new BigDecimal("80.00"), null, "140/90", null,
                null, null, null, null, null, null), T0));

        assertThatThrownBy(() -> v.enregistrer(VoletParamedical.ouvrir(new UUID(8, 2), A, SEANCE, completes(), T0))).isInstanceOf(violationDUnicite());

        assertThat(v.deLaSeance(A, SEANCE)).isEmpty();
        assertThat(v.deLaSeance(B, SEANCE).orElseThrow().mesures().poidsAvantKg()).isEqualByComparingTo("80.00");
    }

    @Test
    void should_erase_the_sheet_of_the_session_in_the_centre_only_and_ignore_a_missing_one() {
        var v = volets();
        v.enregistrer(VoletParamedical.ouvrir(new UUID(8, 1), A, SEANCE, completes(), T0));
        v.enregistrer(VoletParamedical.ouvrir(new UUID(8, 2), A, new SeanceId(new UUID(2, 2)), completes(), T0));

        v.effacer(B, SEANCE);
        assertThat(v.deLaSeance(A, SEANCE)).isPresent();
        v.effacer(A, SEANCE);
        v.effacer(A, SEANCE);

        assertThat(v.deLaSeance(A, SEANCE)).isEmpty();
        assertThat(v.deLaSeance(A, new SeanceId(new UUID(2, 2)))).isPresent();
    }

    @Test
    void should_keep_a_blank_pressure_and_unbounded_duration_debit_and_ultrafiltration_as_entered() {
        var v = volets();
        v.enregistrer(VoletParamedical.ouvrir(new UUID(8, 1), A, SEANCE, MesuresDeSeance.saisies(null, null, "   ", "", -5, 100000,
                new BigDecimal("-100.00"), null, null, null), T0));

        var m = v.deLaSeance(A, SEANCE).orElseThrow().mesures();
        assertThat(m.tensionAvant()).isEqualTo("   ");
        assertThat(m.tensionApres()).isEmpty();
        assertThat(m.dureeMinutes()).isEqualTo(-5);
        assertThat(m.debitSangMlMin()).isEqualTo(100000);
        assertThat(m.ultrafiltrationMl()).isEqualByComparingTo("-100.00");
    }
}

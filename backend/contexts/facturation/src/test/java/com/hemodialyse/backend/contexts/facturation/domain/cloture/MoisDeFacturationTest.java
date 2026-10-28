package com.hemodialyse.backend.contexts.facturation.domain.cloture;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoisDeFacturationTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99997100-0000-0000-0000-00000000000a"));
    private static final YearMonth AOUT = YearMonth.of(2026, 8);
    private static final UUID ADMIN = UUID.fromString("99997100-0000-0000-0000-00000000c001");
    private static final UUID AUTRE_ADMIN = UUID.fromString("99997100-0000-0000-0000-00000000c002");
    private static final Instant LE_1 = Instant.parse("2026-09-01T08:00:00Z");
    private static final Instant LE_2 = Instant.parse("2026-09-02T09:30:00Z");

    @Test
    void should_only_accept_a_finished_month_without_any_grace_day() {
        MoisDeFacturation.exigerTermine(YearMonth.of(2026, 9), java.time.LocalDate.of(2026, 10, 1));
        MoisDeFacturation.exigerTermine(YearMonth.of(2025, 12), java.time.LocalDate.of(2026, 1, 1));

        assertThatThrownBy(() -> MoisDeFacturation.exigerTermine(YearMonth.of(2026, 10), java.time.LocalDate.of(2026, 10, 31)))
                .isInstanceOf(MoisDeFacturationNonTermine.class);
        assertThatThrownBy(() -> MoisDeFacturation.exigerTermine(YearMonth.of(2026, 11), java.time.LocalDate.of(2026, 10, 1)))
                .isInstanceOf(MoisDeFacturationNonTermine.class);
    }

    @Test
    void should_validate_a_reopening_reason_on_its_own_strip_it_and_bound_it_to_500_characters() {
        assertThat(MoisDeFacturation.motifDeReouvertureValide("  facture oubliee  ")).isEqualTo("facture oubliee");
        assertThat(MoisDeFacturation.motifDeReouvertureValide("x".repeat(500))).hasSize(500);
        assertThatThrownBy(() -> MoisDeFacturation.motifDeReouvertureValide(null)).isInstanceOf(MotifDeReouvertureObligatoire.class);
        assertThatThrownBy(() -> MoisDeFacturation.motifDeReouvertureValide("   ")).isInstanceOf(MotifDeReouvertureObligatoire.class);
        assertThatThrownBy(() -> MoisDeFacturation.motifDeReouvertureValide("x".repeat(501))).isInstanceOf(MotifDeReouvertureObligatoire.class);
    }

    @Test
    void should_be_open_when_it_has_never_been_closed() {
        MoisDeFacturation mois = MoisDeFacturation.ouvert(CENTRE, AOUT);

        assertThat(mois.estClos()).isFalse();
        mois.exigerOuvert();
    }

    @Test
    void should_record_who_and_when_and_the_warning_when_closing() {
        MoisDeFacturation mois = MoisDeFacturation.ouvert(CENTRE, AOUT);

        EntreeDuJournal entree = mois.cloturer(LE_1, ADMIN, 3);

        assertThat(mois.estClos()).isTrue();
        assertThat(mois.closPar()).isEqualTo(ADMIN);
        assertThat(mois.closLe()).isEqualTo(LE_1);
        assertThat(entree).isEqualTo(new EntreeDuJournal(EntreeDuJournal.Type.CLOTURE, CENTRE, AOUT, LE_1, ADMIN, null, 3));
    }

    @Test
    void should_refuse_changes_of_a_closed_month() {
        MoisDeFacturation mois = MoisDeFacturation.ouvert(CENTRE, AOUT);
        mois.cloturer(LE_1, ADMIN, 0);

        assertThatThrownBy(mois::exigerOuvert).isInstanceOf(MoisDeFacturationClos.class)
                .hasMessageContaining("2026-08").hasMessageContaining("cloturee");
    }

    @Test
    void should_refuse_to_close_a_month_twice_and_keep_the_first_closure() {
        MoisDeFacturation mois = MoisDeFacturation.ouvert(CENTRE, AOUT);
        mois.cloturer(LE_1, ADMIN, 0);

        assertThatThrownBy(() -> mois.cloturer(LE_2, AUTRE_ADMIN, 5)).isInstanceOf(MoisDejaClos.class)
                .hasMessageContaining("2026-08");

        assertThat(mois.closPar()).isEqualTo(ADMIN);
        assertThat(mois.closLe()).isEqualTo(LE_1);
    }

    @Test
    void should_close_even_with_eligible_seances_left_because_the_administrator_decides() {
        MoisDeFacturation mois = MoisDeFacturation.ouvert(CENTRE, AOUT);

        EntreeDuJournal entree = mois.cloturer(LE_1, ADMIN, 120);

        assertThat(mois.estClos()).isTrue();
        assertThat(entree.seancesEligiblesRestantes()).isEqualTo(120);
    }

    @Test
    void should_reopen_a_closed_month_with_a_reason_and_trace_it() {
        MoisDeFacturation mois = MoisDeFacturation.ouvert(CENTRE, AOUT);
        mois.cloturer(LE_1, ADMIN, 0);

        EntreeDuJournal entree = mois.rouvrir(LE_2, AUTRE_ADMIN, "  Facture oubliee du patient X  ");

        assertThat(mois.estClos()).isFalse();
        assertThat(mois.rouvertPar()).isEqualTo(AUTRE_ADMIN);
        assertThat(mois.rouvertLe()).isEqualTo(LE_2);
        assertThat(mois.motifDeReouverture()).isEqualTo("Facture oubliee du patient X");
        assertThat(mois.closPar()).isEqualTo(ADMIN);
        assertThat(entree).isEqualTo(new EntreeDuJournal(EntreeDuJournal.Type.REOUVERTURE, CENTRE, AOUT, LE_2, AUTRE_ADMIN,
                "Facture oubliee du patient X", null));
        mois.exigerOuvert();
    }

    @Test
    void should_refuse_to_reopen_without_a_reason() {
        MoisDeFacturation mois = MoisDeFacturation.ouvert(CENTRE, AOUT);
        mois.cloturer(LE_1, ADMIN, 0);

        assertThatThrownBy(() -> mois.rouvrir(LE_2, ADMIN, null)).isInstanceOf(MotifDeReouvertureObligatoire.class);
        assertThatThrownBy(() -> mois.rouvrir(LE_2, ADMIN, "   ")).isInstanceOf(MotifDeReouvertureObligatoire.class);
        assertThat(mois.estClos()).isTrue();
    }

    @Test
    void should_refuse_a_reason_longer_than_the_journal_accepts() {
        MoisDeFacturation mois = MoisDeFacturation.ouvert(CENTRE, AOUT);
        mois.cloturer(LE_1, ADMIN, 0);

        assertThatThrownBy(() -> mois.rouvrir(LE_2, ADMIN, "x".repeat(EntreeDuJournal.MOTIF_MAX + 1)))
                .isInstanceOf(MotifDeReouvertureObligatoire.class).hasMessageContaining("500");
        mois.rouvrir(LE_2, ADMIN, "x".repeat(EntreeDuJournal.MOTIF_MAX));
        assertThat(mois.estClos()).isFalse();
    }

    @Test
    void should_refuse_to_reopen_a_month_that_is_not_closed() {
        MoisDeFacturation mois = MoisDeFacturation.ouvert(CENTRE, AOUT);

        assertThatThrownBy(() -> mois.rouvrir(LE_2, ADMIN, "motif")).isInstanceOf(MoisNonClos.class);
    }

    @Test
    void should_allow_closing_again_after_a_reopening() {
        MoisDeFacturation mois = MoisDeFacturation.ouvert(CENTRE, AOUT);
        mois.cloturer(LE_1, ADMIN, 0);
        mois.rouvrir(LE_2, ADMIN, "correction");

        mois.cloturer(Instant.parse("2026-09-03T10:00:00Z"), AUTRE_ADMIN, 1);

        assertThat(mois.estClos()).isTrue();
        assertThat(mois.closPar()).isEqualTo(AUTRE_ADMIN);
    }

    @Test
    void should_require_an_author_and_an_instant_for_every_act() {
        MoisDeFacturation mois = MoisDeFacturation.ouvert(CENTRE, AOUT);

        assertThatThrownBy(() -> mois.cloturer(LE_1, null, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mois.cloturer(null, ADMIN, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(mois.estClos()).isFalse();
    }

    @Test
    void should_require_a_centre_and_a_month() {
        assertThatThrownBy(() -> MoisDeFacturation.ouvert(null, AOUT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MoisDeFacturation.ouvert(CENTRE, null)).isInstanceOf(IllegalArgumentException.class);
    }
}

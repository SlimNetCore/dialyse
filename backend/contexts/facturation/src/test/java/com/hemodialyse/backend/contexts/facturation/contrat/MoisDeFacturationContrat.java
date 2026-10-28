package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.EntreeDuJournal;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDeFacturationRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat partagé du dépôt des mois de facturation (clôture mensuelle, slice F7) : double en mémoire, JDBC sur H2 et PostgreSQL 16.
 * Le verrou entre transactions est prouvé par {@code MoisDeFacturationConcurrentContrat} (JDBC seulement).
 */
public abstract class MoisDeFacturationContrat {

    protected static final CentreId CENTRE_A = new CentreId(UUID.fromString("99997100-0000-0000-0000-0000000000a1"));
    protected static final CentreId CENTRE_B = new CentreId(UUID.fromString("99997100-0000-0000-0000-0000000000b1"));
    protected static final UUID ADMIN = UUID.fromString("99997100-0000-0000-0000-00000000c001");
    protected static final UUID AUTRE_ADMIN = UUID.fromString("99997100-0000-0000-0000-00000000c002");
    protected static final YearMonth AOUT = YearMonth.of(2026, 8);
    protected static final YearMonth SEPTEMBRE = YearMonth.of(2026, 9);
    protected static final Instant LE_1 = Instant.parse("2026-09-01T08:00:00Z");
    protected static final Instant LE_2 = Instant.parse("2026-09-02T09:30:00Z");

    protected abstract MoisDeFacturationRepository mois();

    @Test
    void should_read_an_open_month_when_there_is_no_line() {
        MoisDeFacturation lu = mois().duMois(CENTRE_A, AOUT);

        assertThat(lu.estClos()).isFalse();
        assertThat(lu.mois()).isEqualTo(AOUT);
        assertThat(lu.centre()).isEqualTo(CENTRE_A);
        assertThat(mois().deLAnnee(CENTRE_A, 2026)).isEmpty();
        assertThat(mois().journal(CENTRE_A, AOUT)).isEmpty();
    }

    @Test
    void should_create_the_open_line_once_when_locking_a_month_that_has_none() {
        assertThat(mois().verrouiller(CENTRE_A, AOUT).estClos()).isFalse();
        assertThat(mois().verrouiller(CENTRE_A, AOUT).estClos()).isFalse();

        assertThat(mois().deLAnnee(CENTRE_A, 2026)).extracting(MoisDeFacturation::mois).containsExactly(AOUT);
    }

    @Test
    void should_persist_a_closure_with_its_author_instant_and_journal_entry() {
        MoisDeFacturation courant = mois().verrouiller(CENTRE_A, AOUT);
        EntreeDuJournal entree = courant.cloturer(LE_1, ADMIN, 4);

        mois().enregistrer(courant, entree);

        MoisDeFacturation relu = mois().duMois(CENTRE_A, AOUT);
        assertThat(relu.estClos()).isTrue();
        assertThat(relu.closPar()).isEqualTo(ADMIN);
        assertThat(relu.closLe()).isEqualTo(LE_1);
        assertThat(relu.rouvertPar()).isNull();
        assertThat(mois().journal(CENTRE_A, AOUT)).containsExactly(entree);
        assertThat(entree.seancesEligiblesRestantes()).isEqualTo(4);
    }

    @Test
    void should_persist_a_reopening_keep_the_last_closure_and_append_to_the_journal_in_order() {
        MoisDeFacturation courant = mois().verrouiller(CENTRE_A, AOUT);
        EntreeDuJournal cloture = courant.cloturer(LE_1, ADMIN, 0);
        mois().enregistrer(courant, cloture);
        courant = mois().verrouiller(CENTRE_A, AOUT);
        EntreeDuJournal reouverture = courant.rouvrir(LE_1, AUTRE_ADMIN, "Facture oubliee");
        mois().enregistrer(courant, reouverture);

        MoisDeFacturation relu = mois().duMois(CENTRE_A, AOUT);
        assertThat(relu.estClos()).isFalse();
        assertThat(relu.closPar()).isEqualTo(ADMIN);
        assertThat(relu.rouvertPar()).isEqualTo(AUTRE_ADMIN);
        assertThat(relu.rouvertLe()).isEqualTo(LE_1);
        assertThat(relu.motifDeReouverture()).isEqualTo("Facture oubliee");
        assertThat(mois().journal(CENTRE_A, AOUT)).containsExactly(cloture, reouverture);
    }

    @Test
    void should_close_again_after_a_reopening_and_journal_the_three_acts() {
        MoisDeFacturation courant = mois().verrouiller(CENTRE_A, AOUT);
        mois().enregistrer(courant, courant.cloturer(LE_1, ADMIN, 0));
        courant = mois().verrouiller(CENTRE_A, AOUT);
        mois().enregistrer(courant, courant.rouvrir(LE_1, ADMIN, "motif"));
        courant = mois().verrouiller(CENTRE_A, AOUT);
        mois().enregistrer(courant, courant.cloturer(LE_2, AUTRE_ADMIN, 2));

        assertThat(mois().duMois(CENTRE_A, AOUT).estClos()).isTrue();
        assertThat(mois().duMois(CENTRE_A, AOUT).closPar()).isEqualTo(AUTRE_ADMIN);
        assertThat(mois().journal(CENTRE_A, AOUT)).extracting(EntreeDuJournal::type).containsExactly(
                EntreeDuJournal.Type.CLOTURE, EntreeDuJournal.Type.REOUVERTURE, EntreeDuJournal.Type.CLOTURE);
        assertThat(mois().deLAnnee(CENTRE_A, 2026)).hasSize(1);
    }

    @Test
    void should_keep_months_years_and_centres_apart() {
        MoisDeFacturation aout = mois().verrouiller(CENTRE_A, AOUT);
        mois().enregistrer(aout, aout.cloturer(LE_1, ADMIN, 0));
        mois().verrouiller(CENTRE_A, SEPTEMBRE);
        mois().verrouiller(CENTRE_A, YearMonth.of(2027, 1));

        assertThat(mois().duMois(CENTRE_A, SEPTEMBRE).estClos()).isFalse();
        assertThat(mois().duMois(CENTRE_B, AOUT).estClos()).isFalse();
        List<MoisDeFacturation> de2026 = mois().deLAnnee(CENTRE_A, 2026);
        assertThat(de2026).extracting(MoisDeFacturation::mois).containsExactly(AOUT, SEPTEMBRE);
        assertThat(mois().deLAnnee(CENTRE_A, 2027)).hasSize(1);
        assertThat(mois().deLAnnee(CENTRE_B, 2026)).isEmpty();
        assertThat(mois().journal(CENTRE_B, AOUT)).isEmpty();
        assertThat(mois().journal(CENTRE_A, SEPTEMBRE)).isEmpty();
    }

    @Test
    void should_lock_a_range_of_months_in_ascending_order_creating_only_the_missing_open_lines() {
        MoisDeFacturation aout = mois().verrouiller(CENTRE_A, AOUT);
        mois().enregistrer(aout, aout.cloturer(LE_1, ADMIN, 0));
        mois().verrouiller(CENTRE_B, SEPTEMBRE);

        List<MoisDeFacturation> verrouilles = mois().verrouillerDe(CENTRE_A, YearMonth.of(2026, 7), YearMonth.of(2027, 2));

        assertThat(verrouilles).extracting(MoisDeFacturation::mois).containsExactly(YearMonth.of(2026, 7), AOUT, SEPTEMBRE,
                YearMonth.of(2026, 10), YearMonth.of(2026, 11), YearMonth.of(2026, 12), YearMonth.of(2027, 1), YearMonth.of(2027, 2));
        assertThat(verrouilles).extracting(MoisDeFacturation::estClos).containsExactly(false, true, false, false, false, false, false, false);
        assertThat(verrouilles.get(1).closPar()).isEqualTo(ADMIN);
        assertThat(mois().deLAnnee(CENTRE_A, 2026)).hasSize(6);
        assertThat(mois().deLAnnee(CENTRE_A, 2027)).hasSize(2);
        assertThat(mois().deLAnnee(CENTRE_B, 2026)).extracting(MoisDeFacturation::mois).containsExactly(SEPTEMBRE);
        assertThat(mois().journal(CENTRE_A, AOUT)).hasSize(1);
    }

    @Test
    void should_lock_a_single_month_range_and_be_idempotent() {
        assertThat(mois().verrouillerDe(CENTRE_A, AOUT, AOUT)).extracting(MoisDeFacturation::mois).containsExactly(AOUT);
        assertThat(mois().verrouillerDe(CENTRE_A, AOUT, SEPTEMBRE)).extracting(MoisDeFacturation::mois).containsExactly(AOUT, SEPTEMBRE);

        assertThat(mois().deLAnnee(CENTRE_A, 2026)).extracting(MoisDeFacturation::mois).containsExactly(AOUT, SEPTEMBRE);
    }

    @Test
    void should_refuse_a_reversed_range() {
        assertThatThrownBy(() -> mois().verrouillerDe(CENTRE_A, SEPTEMBRE, AOUT))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

package com.hemodialyse.backend.contexts.absencespatients.contrat;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.domain.EntreeDuJournalDuMois;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.UtilisateurId;
import com.hemodialyse.backend.contexts.absencespatients.domain.MoisAbsences;
import com.hemodialyse.backend.contexts.absencespatients.domain.MoisAbsencesRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat partagé du dépôt des mois d'absences (clôture mensuelle, slice A6) : double en mémoire, JDBC sur H2 et PostgreSQL 16.
 * Le verrou entre transactions est prouvé par {@code MoisAbsencesConcurrentContrat} (JDBC seulement).
 */
public abstract class MoisAbsencesContrat {

    protected static final CentreId CENTRE_A = new CentreId(UUID.fromString("99997200-0000-0000-0000-0000000000a1"));
    protected static final CentreId CENTRE_B = new CentreId(UUID.fromString("99997200-0000-0000-0000-0000000000b1"));
    protected static final UtilisateurId ADMIN = new UtilisateurId(UUID.fromString("99997200-0000-0000-0000-00000000c001"));
    protected static final UtilisateurId AUTRE_ADMIN = new UtilisateurId(UUID.fromString("99997200-0000-0000-0000-00000000c002"));
    protected static final YearMonth AOUT = YearMonth.of(2026, 8);
    protected static final YearMonth SEPTEMBRE = YearMonth.of(2026, 9);
    protected static final Instant LE_1 = Instant.parse("2026-10-01T08:00:00Z");
    protected static final Instant LE_2 = Instant.parse("2026-10-02T09:30:00Z");

    protected abstract MoisAbsencesRepository mois();

    @Test
    void should_read_an_open_month_when_there_is_no_line() {
        MoisAbsences lu = mois().duMois(CENTRE_A, AOUT);

        assertThat(lu.estClos()).isFalse();
        assertThat(lu.mois()).isEqualTo(AOUT);
        assertThat(lu.centre()).isEqualTo(CENTRE_A);
        assertThat(mois().estOuvert(CENTRE_A, AOUT)).isTrue();
        assertThat(mois().deLAnnee(CENTRE_A, 2026)).isEmpty();
        assertThat(mois().moisClos(CENTRE_A)).isEmpty();
        assertThat(mois().journal(CENTRE_A, AOUT)).isEmpty();
    }

    @Test
    void should_create_the_open_line_once_when_locking_a_month_that_has_none() {
        assertThat(mois().verrouiller(CENTRE_A, AOUT).estClos()).isFalse();
        assertThat(mois().estOuvertSousVerrou(CENTRE_A, AOUT)).isTrue();

        assertThat(mois().deLAnnee(CENTRE_A, 2026)).extracting(MoisAbsences::mois).containsExactly(AOUT);
    }

    @Test
    void should_persist_a_closure_with_its_author_instant_warning_and_journal_entry() {
        MoisAbsences courant = mois().verrouiller(CENTRE_A, AOUT);
        EntreeDuJournalDuMois entree = courant.cloturer(LE_1, ADMIN, 4, 3);

        mois().enregistrer(courant, entree);

        MoisAbsences relu = mois().duMois(CENTRE_A, AOUT);
        assertThat(relu.estClos()).isTrue();
        assertThat(relu.closPar()).isEqualTo(ADMIN);
        assertThat(relu.closLe()).isEqualTo(LE_1);
        assertThat(relu.rouvertPar()).isNull();
        assertThat(mois().estOuvert(CENTRE_A, AOUT)).isFalse();
        assertThat(mois().estOuvertSousVerrou(CENTRE_A, AOUT)).isFalse();
        assertThat(mois().moisClos(CENTRE_A)).containsExactly(AOUT);
        assertThat(mois().journal(CENTRE_A, AOUT)).containsExactly(entree);
        assertThat(entree.aQualifierRestantes()).isEqualTo(4L);
        assertThat(entree.enRetard()).isEqualTo(3L);
    }

    @Test
    void should_persist_a_reopening_keep_the_last_closure_and_append_to_the_journal_in_order() {
        MoisAbsences courant = mois().verrouiller(CENTRE_A, AOUT);
        EntreeDuJournalDuMois cloture = courant.cloturer(LE_1, ADMIN, 0, 0);
        mois().enregistrer(courant, cloture);
        courant = mois().verrouiller(CENTRE_A, AOUT);
        EntreeDuJournalDuMois reouverture = courant.rouvrir(LE_2, AUTRE_ADMIN, "Absence oubliee");
        mois().enregistrer(courant, reouverture);

        MoisAbsences relu = mois().duMois(CENTRE_A, AOUT);
        assertThat(relu.estClos()).isFalse();
        assertThat(relu.closPar()).isEqualTo(ADMIN);
        assertThat(relu.rouvertPar()).isEqualTo(AUTRE_ADMIN);
        assertThat(relu.rouvertLe()).isEqualTo(LE_2);
        assertThat(relu.motifDeReouverture()).isEqualTo("Absence oubliee");
        assertThat(mois().moisClos(CENTRE_A)).isEmpty();
        assertThat(mois().journal(CENTRE_A, AOUT)).containsExactly(cloture, reouverture);
    }

    @Test
    void should_close_again_after_a_reopening_and_journal_the_three_acts() {
        MoisAbsences courant = mois().verrouiller(CENTRE_A, AOUT);
        mois().enregistrer(courant, courant.cloturer(LE_1, ADMIN, 0, 0));
        courant = mois().verrouiller(CENTRE_A, AOUT);
        mois().enregistrer(courant, courant.rouvrir(LE_1.plusSeconds(1), ADMIN, "motif"));
        courant = mois().verrouiller(CENTRE_A, AOUT);
        mois().enregistrer(courant, courant.cloturer(LE_2, AUTRE_ADMIN, 2, 1));

        assertThat(mois().duMois(CENTRE_A, AOUT).estClos()).isTrue();
        assertThat(mois().duMois(CENTRE_A, AOUT).closPar()).isEqualTo(AUTRE_ADMIN);
        assertThat(mois().journal(CENTRE_A, AOUT)).extracting(EntreeDuJournalDuMois::type).containsExactly(
                EntreeDuJournalDuMois.Type.CLOTURE, EntreeDuJournalDuMois.Type.REOUVERTURE, EntreeDuJournalDuMois.Type.CLOTURE);
        assertThat(mois().deLAnnee(CENTRE_A, 2026)).hasSize(1);
    }

    @Test
    void should_keep_months_years_and_centres_apart() {
        MoisAbsences aout = mois().verrouiller(CENTRE_A, AOUT);
        mois().enregistrer(aout, aout.cloturer(LE_1, ADMIN, 0, 0));
        mois().verrouiller(CENTRE_A, SEPTEMBRE);
        mois().verrouiller(CENTRE_A, YearMonth.of(2027, 1));

        assertThat(mois().duMois(CENTRE_A, SEPTEMBRE).estClos()).isFalse();
        assertThat(mois().duMois(CENTRE_B, AOUT).estClos()).isFalse();
        List<MoisAbsences> de2026 = mois().deLAnnee(CENTRE_A, 2026);
        assertThat(de2026).extracting(MoisAbsences::mois).containsExactly(AOUT, SEPTEMBRE);
        assertThat(mois().deLAnnee(CENTRE_A, 2027)).hasSize(1);
        assertThat(mois().deLAnnee(CENTRE_B, 2026)).isEmpty();
        assertThat(mois().moisClos(CENTRE_B)).isEmpty();
        assertThat(mois().journal(CENTRE_B, AOUT)).isEmpty();
        assertThat(mois().journal(CENTRE_A, SEPTEMBRE)).isEmpty();
    }

    @Test
    void should_list_the_closed_months_of_a_centre_in_chronological_order() {
        for (YearMonth m : List.of(SEPTEMBRE, AOUT, YearMonth.of(2025, 12))) {
            MoisAbsences courant = mois().verrouiller(CENTRE_A, m);
            mois().enregistrer(courant, courant.cloturer(LE_1, ADMIN, 0, 0));
        }
        mois().verrouiller(CENTRE_A, YearMonth.of(2026, 10));

        assertThat(mois().moisClos(CENTRE_A)).containsExactly(YearMonth.of(2025, 12), AOUT, SEPTEMBRE);
    }
}

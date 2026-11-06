package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Constantes;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ConstantesDeSeance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.HistoriqueDesConstantes;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDeConstantes;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Pagination;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SeanceId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat du port {@link HistoriqueDesConstantes} : le même jeu de tests est rejoué sur le double en mémoire et sur la base
 * (H2 et PostgreSQL 16, en S2), ce qui garantit la fidélité du double.
 */
public abstract class HistoriqueDesConstantesContrat {

    protected static final CentreId CENTRE_A = new CentreId(new UUID(0, 0xA));
    protected static final CentreId CENTRE_B = new CentreId(new UUID(0, 0xB));
    protected static final PatientId PATIENT = new PatientId(new UUID(1, 1));
    protected static final PatientId AUTRE_PATIENT = new PatientId(new UUID(1, 2));
    private static final Instant T0 = Instant.parse("2026-01-01T08:00:00Z");

    /** Banc de données du port testé : sème des séances et des volets (colonnes {@code seances} et {@code volet_paramedical}). */
    public interface Banc {
        void seance(CentreId centre, PatientId patient, SeanceId seance, LocalDate date, Instant creeLe);

        void volet(CentreId centre, SeanceId seance, Constantes constantes);

        HistoriqueDesConstantes historique();
    }

    protected abstract Banc nouveauBanc();

    private static SeanceId s(int n) {
        return new SeanceId(new UUID(2, n));
    }

    private static LocalDate jour(int decalage) {
        return LocalDate.of(2026, 6, 15).plusDays(decalage);
    }

    private static Constantes complet() {
        return new Constantes(new BigDecimal("70.50"), new BigDecimal("68.00"), "130/80", "120/70", 300,
                new BigDecimal("2000.00"), 240);
    }

    private static PageDeConstantes lire(Banc banc, CentreId centre, PatientId patient, int page, int taille) {
        return banc.historique().dePatient(centre, patient, new Pagination(page, taille));
    }

    @Test
    void should_return_all_constants_of_a_session_with_raw_values() {
        Banc banc = nouveauBanc();
        banc.seance(CENTRE_A, PATIENT, s(1), jour(-3), T0);
        Constantes brutes = new Constantes(new BigDecimal("0.00"), new BigDecimal("-3.00"), "n/a", "12/8", -5,
                new BigDecimal("-100.00"), 0);
        banc.volet(CENTRE_A, s(1), brutes);

        PageDeConstantes page = lire(banc, CENTRE_A, PATIENT, 0, 20);

        assertThat(page.items()).containsExactly(new ConstantesDeSeance(s(1), jour(-3), brutes));
        assertThat(page.items().get(0).constantes().poidsApresKg()).isEqualByComparingTo("-3");
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
    }

    @Test
    void should_return_empty_constants_for_a_session_without_paramedical_sheet() {
        Banc banc = nouveauBanc();
        banc.seance(CENTRE_A, PATIENT, s(1), jour(-3), T0);

        PageDeConstantes page = lire(banc, CENTRE_A, PATIENT, 0, 20);

        assertThat(page.items()).containsExactly(ConstantesDeSeance.sansSaisie(s(1), jour(-3)));
        assertThat(page.items().get(0).constantes().sontVides()).isTrue();
    }

    @Test
    void should_keep_partial_constants_with_missing_measures_null() {
        Banc banc = nouveauBanc();
        banc.seance(CENTRE_A, PATIENT, s(1), jour(-3), T0);
        Constantes partielles = new Constantes(new BigDecimal("71.00"), null, null, null, null, null, null);
        banc.volet(CENTRE_A, s(1), partielles);

        assertThat(lire(banc, CENTRE_A, PATIENT, 0, 20).items().get(0).constantes()).isEqualTo(partielles);
    }

    @Test
    void should_list_most_recent_session_first_then_most_recently_created_on_equal_dates() {
        Banc banc = nouveauBanc();
        banc.seance(CENTRE_A, PATIENT, s(1), jour(-9), T0);
        banc.seance(CENTRE_A, PATIENT, s(2), jour(-3), T0.plusSeconds(10));
        banc.seance(CENTRE_A, PATIENT, s(3), jour(-3), T0.plusSeconds(20));
        banc.seance(CENTRE_A, PATIENT, s(4), jour(-1), T0.plusSeconds(5));

        List<SeanceId> ordre = lire(banc, CENTRE_A, PATIENT, 0, 20).items().stream().map(ConstantesDeSeance::seance).toList();

        assertThat(ordre).containsExactly(s(4), s(3), s(2), s(1));
    }

    @Test
    void should_page_the_history_and_return_an_empty_page_with_the_total_beyond_the_last_one() {
        Banc banc = nouveauBanc();
        for (int i = 1; i <= 5; i++) banc.seance(CENTRE_A, PATIENT, s(i), jour(i), T0.plusSeconds(i));

        assertThat(lire(banc, CENTRE_A, PATIENT, 0, 2).items()).extracting(ConstantesDeSeance::seance).containsExactly(s(5), s(4));
        assertThat(lire(banc, CENTRE_A, PATIENT, 1, 2).items()).extracting(ConstantesDeSeance::seance).containsExactly(s(3), s(2));
        PageDeConstantes derniere = lire(banc, CENTRE_A, PATIENT, 2, 2);
        assertThat(derniere.items()).extracting(ConstantesDeSeance::seance).containsExactly(s(1));
        assertThat(derniere.total()).isEqualTo(5);
        PageDeConstantes audela = lire(banc, CENTRE_A, PATIENT, 3, 2);
        assertThat(audela.items()).isEmpty();
        assertThat(audela.total()).isEqualTo(5);
        assertThat(audela.page()).isEqualTo(3);
        assertThat(audela.size()).isEqualTo(2);
        assertThat(lire(banc, CENTRE_A, PATIENT, 0, 100).items()).hasSize(5);
    }

    @Test
    void should_ignore_sessions_and_sheets_of_another_centre() {
        Banc banc = nouveauBanc();
        banc.seance(CENTRE_A, PATIENT, s(1), jour(-5), T0);
        banc.volet(CENTRE_B, s(1), complet()); // volet posé sous un autre centre : ignoré
        banc.seance(CENTRE_B, PATIENT, s(2), jour(-4), T0.plusSeconds(1)); // séance du patient rattachée à un autre centre

        PageDeConstantes page = lire(banc, CENTRE_A, PATIENT, 0, 20);

        assertThat(page.items()).containsExactly(ConstantesDeSeance.sansSaisie(s(1), jour(-5)));
        assertThat(page.total()).isEqualTo(1);
    }

    @Test
    void should_return_an_empty_history_for_an_unknown_patient_or_one_of_another_centre() {
        Banc banc = nouveauBanc();
        banc.seance(CENTRE_B, AUTRE_PATIENT, s(1), jour(-2), T0);
        banc.volet(CENTRE_B, s(1), complet());

        assertThat(lire(banc, CENTRE_A, AUTRE_PATIENT, 0, 20).items()).isEmpty();
        assertThat(lire(banc, CENTRE_A, AUTRE_PATIENT, 0, 20).total()).isZero();
        assertThat(lire(banc, CENTRE_A, new PatientId(new UUID(9, 9)), 0, 20).items()).isEmpty();
    }

    @Test
    void should_not_mix_the_sessions_of_two_patients() {
        Banc banc = nouveauBanc();
        banc.seance(CENTRE_A, PATIENT, s(1), jour(-2), T0);
        banc.seance(CENTRE_A, AUTRE_PATIENT, s(2), jour(-1), T0.plusSeconds(1));

        assertThat(lire(banc, CENTRE_A, PATIENT, 0, 20).items()).extracting(ConstantesDeSeance::seance).containsExactly(s(1));
    }

    @Test
    void should_read_fresh_values_on_every_call() {
        Banc banc = nouveauBanc();
        banc.seance(CENTRE_A, PATIENT, s(1), jour(-2), T0);
        banc.volet(CENTRE_A, s(1), complet());
        assertThat(lire(banc, CENTRE_A, PATIENT, 0, 1).items().get(0).constantes().poidsAvantKg()).isEqualByComparingTo("70.50");

        banc.volet(CENTRE_A, s(1), new Constantes(new BigDecimal("99.90"), null, null, null, null, null, null));

        assertThat(lire(banc, CENTRE_A, PATIENT, 0, 1).items().get(0).constantes().poidsAvantKg()).isEqualByComparingTo("99.90");
    }
}

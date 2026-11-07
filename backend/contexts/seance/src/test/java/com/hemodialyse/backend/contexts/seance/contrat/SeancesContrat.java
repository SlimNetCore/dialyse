package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.domain.DerogationHorsPlanning;
import com.hemodialyse.backend.contexts.seance.domain.MotifHorsPlanning;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIllisible;
import com.hemodialyse.backend.contexts.seance.domain.Seances;
import com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat du port {@link Seances} : le même jeu de tests est rejoué sur le double en mémoire et sur la base (H2 et PostgreSQL 16), ce qui
 * garantit la fidélité du double.
 */
public abstract class SeancesContrat {

    protected static final CentreId CENTRE_A = new CentreId(new UUID(0, 0xA));
    protected static final CentreId CENTRE_B = new CentreId(new UUID(0, 0xB));
    protected static final PatientId PATIENT = new PatientId(new UUID(1, 1));
    protected static final PatientId AUTRE_PATIENT = new PatientId(new UUID(1, 2));
    protected static final LocalDate JOUR = LocalDate.of(2026, 6, 15);
    protected static final Instant T0 = Instant.parse("2026-06-15T08:00:00Z");

    /** Banc de données du port testé : sème des lignes telles que le legacy les a écrites. */
    public interface Banc {
        Seances seances();

        /** Une ligne existante, avec son statut et ses jalons (validation, signatures, régularisation). */
        void semer(Seance seance);

        /** Une ligne dont le statut n'est pas un statut connu (reliquat). */
        void semerUneLigneIllisible(CentreId centre, SeanceId id);
    }

    protected abstract Banc nouveauBanc();

    protected static SeanceId s(int n) {
        return new SeanceId(new UUID(2, n));
    }

    private static Seance programmee(int n, CentreId centre, PatientId patient, LocalDate jour, Instant creeLe) {
        return Seance.creer(s(n), centre, patient, jour, creeLe);
    }

    @Test
    void should_find_a_session_added_as_it_was_created() {
        var banc = nouveauBanc();
        banc.seances().ajouter(programmee(1, CENTRE_A, PATIENT, JOUR, T0));

        var lue = banc.seances().trouver(CENTRE_A, s(1)).orElseThrow();

        assertThat(lue.id()).isEqualTo(s(1));
        assertThat(lue.centre()).isEqualTo(CENTRE_A);
        assertThat(lue.patient()).isEqualTo(PATIENT);
        assertThat(lue.jour()).isEqualTo(JOUR);
        assertThat(lue.statut()).isEqualTo(StatutDeSeance.CREE);
        assertThat(lue.creeLe()).isEqualTo(T0);
        assertThat(lue.horsPlanning()).isFalse();
        assertThat(lue.jalons()).isEqualTo(Seance.Jalons.AUCUN);
    }

    @Test
    void should_keep_the_motif_and_the_precision_of_a_session_outside_the_planning() {
        var banc = nouveauBanc();
        var derogation = new DerogationHorsPlanning(MotifHorsPlanning.AUTRE, "Panne du poste", true);
        banc.seances().ajouter(Seance.creerHorsPlanning(s(1), CENTRE_A, PATIENT, JOUR, T0, derogation));

        var lue = banc.seances().trouver(CENTRE_A, s(1)).orElseThrow();

        assertThat(lue.horsPlanning()).isTrue();
        assertThat(lue.derogation().orElseThrow().motif()).isEqualTo(MotifHorsPlanning.AUTRE);
        assertThat(lue.derogation().orElseThrow().precision()).isEqualTo("Panne du poste");
    }

    @Test
    void should_never_give_a_session_of_another_centre() {
        var banc = nouveauBanc();
        banc.seances().ajouter(programmee(1, CENTRE_A, PATIENT, JOUR, T0));

        assertThat(banc.seances().trouver(CENTRE_B, s(1))).isEmpty();
        assertThat(banc.seances().duJour(CENTRE_B, PATIENT, JOUR)).isEmpty();
    }

    @Test
    void should_find_nothing_for_an_unknown_session() {
        assertThat(nouveauBanc().seances().trouver(CENTRE_A, s(99))).isEmpty();
    }

    @Test
    void should_give_the_sessions_of_the_patient_for_the_day_oldest_first() {
        var banc = nouveauBanc();
        banc.seances().ajouter(programmee(2, CENTRE_A, PATIENT, JOUR, T0.plusSeconds(60)));
        banc.seances().ajouter(programmee(1, CENTRE_A, PATIENT, JOUR, T0));
        banc.seances().ajouter(programmee(3, CENTRE_A, PATIENT, JOUR.plusDays(1), T0));
        banc.seances().ajouter(programmee(4, CENTRE_A, AUTRE_PATIENT, JOUR, T0));

        var duJour = banc.seances().duJour(CENTRE_A, PATIENT, JOUR);

        assertThat(duJour).extracting(Seance::id).containsExactly(s(1), s(2));
    }

    @Test
    void should_allow_several_sessions_for_the_same_patient_and_day() {
        var banc = nouveauBanc();
        banc.seances().ajouter(programmee(1, CENTRE_A, PATIENT, JOUR, T0));
        banc.seances().ajouter(programmee(2, CENTRE_A, PATIENT, JOUR, T0));

        assertThat(banc.seances().duJour(CENTRE_A, PATIENT, JOUR)).hasSize(2);
    }

    @Test
    void should_give_a_session_without_creation_time_last() {
        var banc = nouveauBanc();
        banc.semer(Seance.restituer(s(1), CENTRE_A, PATIENT, JOUR, StatutDeSeance.VALIDEE, null, null, null));
        banc.seances().ajouter(programmee(2, CENTRE_A, PATIENT, JOUR, T0));

        assertThat(banc.seances().duJour(CENTRE_A, PATIENT, JOUR)).extracting(Seance::id).containsExactly(s(2), s(1));
    }

    @Test
    void should_restore_the_milestones_written_by_the_legacy() {
        var banc = nouveauBanc();
        var jalons = new Seance.Jalons(T0.plusSeconds(10), T0.plusSeconds(11), T0.plusSeconds(12), T0.plusSeconds(13));
        banc.semer(Seance.restituer(s(1), CENTRE_A, PATIENT, JOUR, StatutDeSeance.SIGNEE, T0, null, jalons));

        var lue = banc.seances().trouver(CENTRE_A, s(1)).orElseThrow();

        assertThat(lue.statut()).isEqualTo(StatutDeSeance.SIGNEE);
        assertThat(lue.jalons()).isEqualTo(jalons);
        assertThat(lue.attendLaValidation()).isFalse();
    }

    @Test
    void should_read_every_status_of_the_legacy_including_the_historical_absent() {
        var banc = nouveauBanc();
        int n = 1;
        for (StatutDeSeance statut : StatutDeSeance.values()) {
            banc.semer(Seance.restituer(s(n), CENTRE_A, PATIENT, JOUR.plusDays(n), statut, T0, null, null));
            assertThat(banc.seances().trouver(CENTRE_A, s(n)).orElseThrow().statut()).isEqualTo(statut);
            n++;
        }
    }

    @Test
    void should_report_an_unreadable_line_by_its_identifier_without_a_technical_class_name() {
        var banc = nouveauBanc();
        banc.semerUneLigneIllisible(CENTRE_A, s(7));

        assertThatThrownBy(() -> banc.seances().trouver(CENTRE_A, s(7)))
                .isInstanceOfSatisfying(SeanceIllisible.class, e -> {
                    assertThat(e.seance()).isEqualTo(s(7));
                    assertThat(e.getMessage()).doesNotContain("com.hemodialyse").doesNotContain("Exception");
                });
    }

    @Test
    void should_lock_the_day_without_disturbing_the_reads() {
        var banc = nouveauBanc();
        banc.seances().ajouter(programmee(1, CENTRE_A, PATIENT, JOUR, T0));

        assertThatCode(() -> banc.seances().verrouillerLeJour(CENTRE_A, PATIENT, JOUR)).doesNotThrowAnyException();
        assertThat(banc.seances().duJour(CENTRE_A, PATIENT, JOUR)).hasSize(1);
    }
}

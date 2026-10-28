package com.hemodialyse.backend.contexts.facturation.application.cloture;

import com.hemodialyse.backend.contexts.facturation.application.cloture.CloturerLeMois.ResultatDeCloture;
import com.hemodialyse.backend.contexts.facturation.doubles.MoisDeFacturationEnMemoire;
import com.hemodialyse.backend.contexts.facturation.doubles.SeancesEnMemoire;
import com.hemodialyse.backend.contexts.facturation.doubles.UniteDeTravailDirecte;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.EntreeDuJournal;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDeFacturationNonTermine;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDejaClos;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisNonClos;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.MotifDeReouvertureObligatoire;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.ReouvertureRefuseeParLaComptabilite;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitApplique;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PriseEnChargeTarifee;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.PeriodeDeFacturationCloturee;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.PeriodeDeFacturationRouverte;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloturerEtRouvrirLeMoisTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99997100-0000-0000-0000-00000000000a"));
    private static final CentreId AUTRE_CENTRE = new CentreId(UUID.fromString("99997100-0000-0000-0000-00000000000b"));
    private static final UUID PATIENT = UUID.fromString("99997100-0000-0000-0000-00000000a001");
    private static final UUID FORFAIT = UUID.fromString("99997100-0000-0000-0000-0000000f0001");
    private static final UUID ADMIN = UUID.fromString("99997100-0000-0000-0000-00000000c001");
    private static final YearMonth AOUT = YearMonth.of(2026, 8);
    private static final YearMonth SEPTEMBRE = YearMonth.of(2026, 9);
    private static final Instant MAINTENANT = Instant.parse("2026-10-21T08:00:00Z");

    private final MoisDeFacturationEnMemoire mois = new MoisDeFacturationEnMemoire();
    private final SeancesEnMemoire seances = new SeancesEnMemoire();
    private final Publications publication = new Publications();
    private final Set<String> comptablesCloses = new HashSet<>();
    private final Clock horloge = Clock.fixed(MAINTENANT, ZoneOffset.UTC);
    private final Set<String> absencesCloses = new HashSet<>();
    private final CloturerLeMois cloturer = new CloturerLeMois(mois, seances, publication, new UniteDeTravailDirecte(), horloge,
            (centre, m) -> absencesCloses.contains(centre.valeur() + "/" + m), centre -> ZoneOffset.UTC);
    private final RouvrirLeMois rouvrir = new RouvrirLeMois(mois,
            (centre, m) -> comptablesCloses.contains(centre.valeur() + "/" + m), publication, new UniteDeTravailDirecte(), horloge);
    private final ConsulterLesPeriodes consulter = new ConsulterLesPeriodes(mois);

    CloturerEtRouvrirLeMoisTest() {
        seances.forfait(FORFAIT, CENTRE, "Forfait HD", new BigDecimal("3500.00"));
        seances.patient(PATIENT, CENTRE, "P-001", "Amrani", "Lina", "PERMANENT");
        ForfaitApplique hd = new ForfaitApplique(FORFAIT, "Forfait HD", new BigDecimal("3500.00"));
        seances.priseEnCharge(PATIENT, new PriseEnChargeTarifee(hd, true, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                null, null, Instant.parse("2026-01-01T08:00:00Z")));
    }

    private void seance(String jour, String statut, boolean facturee) {
        seances.seance(UUID.randomUUID(), CENTRE, PATIENT, LocalDate.parse(jour), statut, facturee);
    }

    @Test
    void should_close_the_month_and_warn_with_the_number_of_eligible_seances_left() {
        seance("2026-08-03", "VALIDEE", false);
        seance("2026-08-04", "SIGNEE", false);
        seance("2026-08-05", "VALIDEE", true);
        seance("2026-08-06", "ABSENT", false);
        seance("2026-08-07", "EN_COURS", false);
        seance("2026-09-01", "VALIDEE", false);

        ResultatDeCloture resultat = cloturer.executer(CENTRE, AOUT, ADMIN);

        assertThat(resultat).isEqualTo(new ResultatDeCloture(AOUT, MAINTENANT, 2, true));
        assertThat(mois.duMois(CENTRE, AOUT).estClos()).isTrue();
        assertThat(mois.duMois(CENTRE, SEPTEMBRE).estClos()).isFalse();
    }

    @Test
    void should_warn_that_the_absences_of_the_month_are_not_closed_but_never_refuse_the_closure() {
        absencesCloses.add(CENTRE.valeur() + "/" + SEPTEMBRE);

        assertThat(cloturer.executer(CENTRE, AOUT, ADMIN).absencesDuMoisNonCloses()).isTrue();
        assertThat(cloturer.executer(CENTRE, SEPTEMBRE, ADMIN).absencesDuMoisNonCloses()).isFalse();
        assertThat(mois.duMois(CENTRE, AOUT).estClos()).isTrue();
    }

    @Test
    void should_close_an_empty_past_month_without_any_state_precondition() {
        assertThat(cloturer.executer(CENTRE, YearMonth.of(2026, 3), ADMIN).seancesEligiblesRestantes()).isZero();
        assertThat(mois.duMois(CENTRE, YearMonth.of(2026, 3)).estClos()).isTrue();
    }

    @Test
    void should_refuse_the_current_month_and_future_months_before_any_lock_like_the_absences() {
        assertThatThrownBy(() -> cloturer.executer(CENTRE, YearMonth.of(2026, 10), ADMIN))
                .isInstanceOf(MoisDeFacturationNonTermine.class).hasMessageContaining("2026-10");
        assertThatThrownBy(() -> cloturer.executer(CENTRE, YearMonth.of(2027, 3), ADMIN))
                .isInstanceOf(MoisDeFacturationNonTermine.class);

        assertThat(mois.moisVerrouilles()).isEmpty();
        assertThat(publication.cloturees).isEmpty();
        assertThat(mois.journal(CENTRE, YearMonth.of(2026, 10))).isEmpty();
    }

    @Test
    void should_decide_the_current_month_in_the_time_zone_of_the_centre() {
        // 2026-10-31 23:30 UTC = 2026-11-01 00:30 à Alger : octobre est terminé au centre, pas en UTC
        Clock finOctobreUtc = Clock.fixed(Instant.parse("2026-10-31T23:30:00Z"), ZoneOffset.UTC);
        CloturerLeMois aAlger = new CloturerLeMois(mois, seances, publication, new UniteDeTravailDirecte(), finOctobreUtc,
                (centre, m) -> false, centre -> java.time.ZoneId.of("Africa/Algiers"));
        CloturerLeMois enUtc = new CloturerLeMois(mois, seances, publication, new UniteDeTravailDirecte(), finOctobreUtc,
                (centre, m) -> false, centre -> ZoneOffset.UTC);

        assertThatThrownBy(() -> enUtc.executer(CENTRE, YearMonth.of(2026, 10), ADMIN)).isInstanceOf(MoisDeFacturationNonTermine.class);
        assertThat(aAlger.executer(CENTRE, YearMonth.of(2026, 10), ADMIN).mois()).isEqualTo(YearMonth.of(2026, 10));
    }

    @Test
    void should_journal_the_closure_and_publish_the_event_with_the_warning() {
        seance("2026-08-03", "VALIDEE", false);

        cloturer.executer(CENTRE, AOUT, ADMIN);

        assertThat(mois.journal(CENTRE, AOUT)).containsExactly(
                new EntreeDuJournal(EntreeDuJournal.Type.CLOTURE, CENTRE, AOUT, MAINTENANT, ADMIN, null, 1));
        assertThat(publication.cloturees).containsExactly(new PeriodeDeFacturationCloturee(CENTRE.valeur(), AOUT, ADMIN, MAINTENANT, 1));
        assertThat(publication.rouvertes).isEmpty();
    }

    @Test
    void should_refuse_a_second_closure_without_journal_or_event() {
        cloturer.executer(CENTRE, AOUT, ADMIN);

        assertThatThrownBy(() -> cloturer.executer(CENTRE, AOUT, ADMIN)).isInstanceOf(MoisDejaClos.class);

        assertThat(mois.journal(CENTRE, AOUT)).hasSize(1);
        assertThat(publication.cloturees).hasSize(1);
    }

    @Test
    void should_lock_the_month_before_counting_and_closing() {
        cloturer.executer(CENTRE, AOUT, ADMIN);

        assertThat(mois.moisVerrouilles()).containsExactly(AOUT);
    }

    @Test
    void should_keep_other_months_and_other_centres_independent() {
        cloturer.executer(CENTRE, AOUT, ADMIN);

        assertThat(mois.duMois(CENTRE, SEPTEMBRE).estClos()).isFalse();
        assertThat(mois.duMois(AUTRE_CENTRE, AOUT).estClos()).isFalse();
        cloturer.executer(AUTRE_CENTRE, AOUT, ADMIN);
    }

    @Test
    void should_reopen_with_a_reason_journal_it_and_publish_the_event() {
        cloturer.executer(CENTRE, AOUT, ADMIN);

        rouvrir.executer(CENTRE, AOUT, ADMIN, "Facture oubliee");

        assertThat(mois.duMois(CENTRE, AOUT).estClos()).isFalse();
        assertThat(mois.journal(CENTRE, AOUT)).extracting(EntreeDuJournal::type)
                .containsExactly(EntreeDuJournal.Type.CLOTURE, EntreeDuJournal.Type.REOUVERTURE);
        assertThat(publication.rouvertes).containsExactly(
                new PeriodeDeFacturationRouverte(CENTRE.valeur(), AOUT, ADMIN, MAINTENANT, "Facture oubliee"));
    }

    @Test
    void should_refuse_to_reopen_without_a_reason_and_keep_the_month_closed() {
        cloturer.executer(CENTRE, AOUT, ADMIN);

        assertThatThrownBy(() -> rouvrir.executer(CENTRE, AOUT, ADMIN, " ")).isInstanceOf(MotifDeReouvertureObligatoire.class);

        assertThat(mois.duMois(CENTRE, AOUT).estClos()).isTrue();
        assertThat(publication.rouvertes).isEmpty();
        assertThat(mois.journal(CENTRE, AOUT)).hasSize(1);
    }

    @Test
    void should_refuse_to_reopen_a_month_that_is_not_closed_before_looking_at_the_accounting() {
        comptablesCloses.add(CENTRE.valeur() + "/" + AOUT);

        assertThatThrownBy(() -> rouvrir.executer(CENTRE, AOUT, ADMIN, "motif")).isInstanceOf(MoisNonClos.class);
    }

    @Test
    void should_refuse_to_reopen_when_the_accounting_period_of_the_same_month_and_centre_is_closed() {
        cloturer.executer(CENTRE, AOUT, ADMIN);
        cloturer.executer(CENTRE, SEPTEMBRE, ADMIN);
        comptablesCloses.add(CENTRE.valeur() + "/" + AOUT);

        assertThatThrownBy(() -> rouvrir.executer(CENTRE, AOUT, ADMIN, "motif")).isInstanceOf(ReouvertureRefuseeParLaComptabilite.class)
                .hasMessageContaining("2026-08");

        assertThat(mois.duMois(CENTRE, AOUT).estClos()).isTrue();
        assertThat(publication.rouvertes).isEmpty();
        rouvrir.executer(CENTRE, SEPTEMBRE, ADMIN, "un autre mois, comptabilite ouverte");
        assertThat(mois.duMois(CENTRE, SEPTEMBRE).estClos()).isFalse();
    }

    @Test
    void should_list_the_twelve_months_of_a_year_with_closed_ones_marked() {
        cloturer.executer(CENTRE, AOUT, ADMIN);
        cloturer.executer(CENTRE, SEPTEMBRE, ADMIN);
        rouvrir.executer(CENTRE, SEPTEMBRE, ADMIN, "correction");
        // un mois d'une autre année (passé : ADR 0044) n'apparaît pas dans 2026
        cloturer.executer(CENTRE, YearMonth.of(2025, 12), ADMIN);

        List<MoisDeFacturation> annee = consulter.executer(CENTRE, 2026);

        assertThat(annee).extracting(MoisDeFacturation::mois).containsExactlyElementsOf(tousLesMoisDe2026());
        assertThat(annee).filteredOn(MoisDeFacturation::estClos).extracting(MoisDeFacturation::mois).containsExactly(AOUT);
        MoisDeFacturation septembre = annee.get(8);
        assertThat(septembre.rouvertPar()).isEqualTo(ADMIN);
        assertThat(septembre.motifDeReouverture()).isEqualTo("correction");
        assertThat(consulter.executer(AUTRE_CENTRE, 2026)).noneMatch(MoisDeFacturation::estClos);
    }

    private static List<YearMonth> tousLesMoisDe2026() {
        List<YearMonth> liste = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            liste.add(YearMonth.of(2026, m));
        }
        return liste;
    }

    private static final class Publications implements PublicationDesPeriodes {
        final List<PeriodeDeFacturationCloturee> cloturees = new ArrayList<>();
        final List<PeriodeDeFacturationRouverte> rouvertes = new ArrayList<>();

        @Override
        public void cloturee(PeriodeDeFacturationCloturee evenement) {
            cloturees.add(evenement);
        }

        @Override
        public void rouverte(PeriodeDeFacturationRouverte evenement) {
            rouvertes.add(evenement);
        }
    }
}

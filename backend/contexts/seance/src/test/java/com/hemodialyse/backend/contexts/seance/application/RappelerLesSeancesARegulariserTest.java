package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.Pagination;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.RappelDeRegularisation;
import com.hemodialyse.backend.contexts.seance.domain.RechercheDeSeances;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_A;
import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_B;
import static org.assertj.core.api.Assertions.assertThat;

/** SE4 : le rappel quotidien des séances à régulariser, avec des doubles en mémoire (aucun mock interactionnel). */
class RappelerLesSeancesARegulariserTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 6, 15);

    private final List<HistoriqueDesSeances.LigneDeSeance> lignes = new ArrayList<>();
    private final List<String> journalDesAppels = new ArrayList<>();
    private final Set<String> rappelsEmis = new HashSet<>();
    private final List<RappelDeRegularisation> emis = new ArrayList<>();
    private final Set<CentreId> centresEnPanne = new HashSet<>();
    private List<CentreId> centres = List.of(CENTRE_A, CENTRE_B);
    private HorlogeDuCentre horloge = HorlogeDuCentre.enUtc(Clock.fixed(Instant.parse("2026-06-15T05:00:00Z"), ZoneOffset.UTC));

    private final HistoriqueDesSeances historique = (centre, r, pagination) -> {
        var trouvees = lignes.stream().filter(l -> l.centre().equals(centre))
                .filter(l -> r.du() == null || !l.jour().isBefore(r.du())).filter(l -> r.au() == null || !l.jour().isAfter(r.au()))
                .filter(l -> r.statuts().isEmpty() || r.statuts().contains(l.statut()))
                .filter(l -> r.deverrouillee() == null || (l.deverrouilleeLe() != null) == r.deverrouillee())
                .sorted(r.decroissant() ? Comparator.comparing(HistoriqueDesSeances.LigneDeSeance::jour).reversed()
                        : Comparator.comparing(HistoriqueDesSeances.LigneDeSeance::jour)).toList();
        journalDesAppels.add("lecture:" + pagination.taille());
        return PagedResult.of(trouvees.stream().skip(pagination.decalage()).limit(pagination.taille()).toList(), trouvees.size(),
                pagination.page(), pagination.taille());
    };

    private final RappelsDeRegularisation rappels = rappel -> {
        if (centresEnPanne.contains(rappel.centre())) throw new IllegalStateException("notifications indisponibles");
        journalDesAppels.add("rappel:" + rappel.centre().valeur());
        boolean nouveau = rappelsEmis.add(rappel.centre().valeur() + "|" + rappel.jour());
        if (nouveau) emis.add(rappel);
        return nouveau;
    };

    private final VerrousDesRappels verrous = (centre, jour) -> journalDesAppels.add("verrou:" + centre.valeur() + "|" + jour);

    private final UniteDeTravail unite = new UniteDeTravail() {
        @Override
        public <T> T executer(Supplier<T> action) {
            journalDesAppels.add("debut");
            try {
                return action.get();
            } finally {
                journalDesAppels.add("fin");
            }
        }
    };

    private RappelerLesSeancesARegulariser cas() {
        return new RappelerLesSeancesARegulariser(() -> centres, horloge, historique, rappels, verrous, unite);
    }

    private void oubliee(CentreId centre, int decalage, StatutDeSeance statut) {
        int n = lignes.size() + 1;
        lignes.add(new HistoriqueDesSeances.LigneDeSeance(new SeanceId(new UUID(5, n)), centre, new PatientId(new UUID(7, n)), "P" + n,
                "Nom" + n, "Prenom" + n, AUJOURDHUI.plusDays(decalage), statut, null, null, null, null, null, false, null));
    }

    private void deverrouillee(CentreId centre, int decalage) {
        int n = lignes.size() + 1;
        lignes.add(new HistoriqueDesSeances.LigneDeSeance(new SeanceId(new UUID(5, n)), centre, new PatientId(new UUID(7, n)), "P" + n,
                "Nom" + n, "Prenom" + n, AUJOURDHUI.plusDays(decalage), StatutDeSeance.CREE, null, null, null, null,
                java.time.OffsetDateTime.parse("2026-06-14T08:00:00Z"), false, null));
    }

    @Test
    void should_remind_the_administrator_of_the_forgotten_sessions_of_the_last_seven_days_with_the_oldest_date() {
        oubliee(CENTRE_A, -1, StatutDeSeance.CREE);
        oubliee(CENTRE_A, -4, StatutDeSeance.CREE);
        oubliee(CENTRE_A, -7, StatutDeSeance.CREE);

        var resultat = cas().executerPourLeCentre(CENTRE_A, AUJOURDHUI);

        assertThat(resultat.rappele()).isTrue();
        assertThat(resultat.seances()).isEqualTo(3);
        assertThat(emis).singleElement().satisfies(r -> {
            assertThat(r.centre()).isEqualTo(CENTRE_A);
            assertThat(r.jour()).isEqualTo(AUJOURDHUI);
            assertThat(r.seances().total()).isEqualTo(3);
            assertThat(r.seances().plusAncienne()).isEqualTo(AUJOURDHUI.minusDays(7));
        });
    }

    @Test
    void should_not_remind_sessions_older_than_seven_days_today_or_the_future_even_though_they_stay_locked() {
        // KB-SEA-37 reproduit : décision métier prioritaire (ADR 0091)
        oubliee(CENTRE_A, -8, StatutDeSeance.CREE);
        oubliee(CENTRE_A, -30, StatutDeSeance.CREE);
        oubliee(CENTRE_A, 0, StatutDeSeance.CREE);
        oubliee(CENTRE_A, 1, StatutDeSeance.CREE);

        var resultat = cas().executerPourLeCentre(CENTRE_A, AUJOURDHUI);

        assertThat(resultat.rappele()).isFalse();
        assertThat(emis).isEmpty();
    }

    @Test
    void should_count_only_created_sessions_that_the_administrator_has_not_unlocked_yet() {
        oubliee(CENTRE_A, -2, StatutDeSeance.VALIDEE);
        oubliee(CENTRE_A, -3, StatutDeSeance.SIGNEE);
        oubliee(CENTRE_A, -4, StatutDeSeance.FACTUREE);
        oubliee(CENTRE_A, -5, StatutDeSeance.ABSENT);
        deverrouillee(CENTRE_A, -6);
        oubliee(CENTRE_A, -1, StatutDeSeance.CREE);

        cas().executerPourLeCentre(CENTRE_A, AUJOURDHUI);

        assertThat(emis).singleElement().satisfies(r -> {
            assertThat(r.seances().total()).isEqualTo(1);
            assertThat(r.seances().plusAncienne()).isEqualTo(AUJOURDHUI.minusDays(1));
        });
    }

    @Test
    void should_remind_each_centre_only_of_its_own_sessions() {
        oubliee(CENTRE_A, -1, StatutDeSeance.CREE);
        oubliee(CENTRE_A, -2, StatutDeSeance.CREE);
        oubliee(CENTRE_B, -5, StatutDeSeance.CREE);

        var resultats = cas().executer();

        assertThat(resultats).extracting(RappelerLesSeancesARegulariser.ResultatDuCentre::seances).containsExactly(2L, 1L);
        assertThat(emis).extracting(r -> r.seances().total()).containsExactly(2L, 1L);
    }

    @Test
    void should_not_remind_a_centre_without_forgotten_session() {
        var resultat = cas().executerPourLeCentre(CENTRE_A, AUJOURDHUI);

        assertThat(resultat.rappele()).isFalse();
        assertThat(resultat.seances()).isZero();
        assertThat(resultat.incident()).isNull();
        assertThat(journalDesAppels).noneMatch(a -> a.startsWith("verrou") || a.startsWith("rappel"));
    }

    @Test
    void should_emit_only_one_reminder_per_centre_and_per_day_but_one_again_the_next_day() {
        // KB-SEA-35 corrigé : le legacy diffusait et journalisait un rappel à chaque appel
        oubliee(CENTRE_A, -1, StatutDeSeance.CREE);

        var premier = cas().executerPourLeCentre(CENTRE_A, AUJOURDHUI);
        var second = cas().executerPourLeCentre(CENTRE_A, AUJOURDHUI);
        var lendemain = cas().executerPourLeCentre(CENTRE_A, AUJOURDHUI.plusDays(1));

        assertThat(premier.rappele()).isTrue();
        assertThat(second.rappele()).isFalse();
        assertThat(second.seances()).isEqualTo(1);
        assertThat(lendemain.rappele()).isTrue();
        assertThat(emis).hasSize(2);
    }

    @Test
    void should_take_the_lock_of_the_centre_and_day_inside_the_unit_of_work_before_emitting() {
        oubliee(CENTRE_A, -1, StatutDeSeance.CREE);

        cas().executerPourLeCentre(CENTRE_A, AUJOURDHUI);

        assertThat(journalDesAppels).containsExactly("lecture:1", "debut", "verrou:" + CENTRE_A.valeur() + "|" + AUJOURDHUI,
                "rappel:" + CENTRE_A.valeur(), "fin");
    }

    @Test
    void should_use_the_day_of_each_centre_and_not_the_utc_day() {
        // KB-SEA-36 corrigé : à 23:30 UTC le 14 juin il est déjà le 15 à Alger ; la séance du 14 est « hier » pour le centre
        horloge = new HorlogeDuCentre(Clock.fixed(Instant.parse("2026-06-14T23:30:00Z"), ZoneOffset.UTC), java.time.ZoneId.of("Africa/Algiers"));
        oubliee(CENTRE_A, -1, StatutDeSeance.CREE);
        centres = List.of(CENTRE_A);

        var resultats = cas().executer();

        assertThat(resultats).singleElement().satisfies(r -> {
            assertThat(r.jour()).isEqualTo(AUJOURDHUI);
            assertThat(r.rappele()).isTrue();
        });
    }

    @Test
    void should_use_a_different_day_for_each_centre_when_their_time_zones_differ() {
        horloge = new HorlogeDuCentre(Clock.fixed(Instant.parse("2026-06-15T05:00:00Z"), ZoneOffset.UTC),
                centre -> centre.equals(CENTRE_A.valeur()) ? AUJOURDHUI : AUJOURDHUI.plusDays(1));
        oubliee(CENTRE_A, 0, StatutDeSeance.CREE);
        oubliee(CENTRE_B, 0, StatutDeSeance.CREE);

        var resultats = cas().executer();

        assertThat(resultats).extracting(RappelerLesSeancesARegulariser.ResultatDuCentre::jour)
                .containsExactly(AUJOURDHUI, AUJOURDHUI.plusDays(1));
        // la séance du 15 est « aujourd'hui » pour le centre A (exclue), « hier » pour le centre B (rappelée)
        assertThat(resultats.get(0).seances()).isZero();
        assertThat(resultats.get(1).seances()).isEqualTo(1);
    }

    @Test
    void should_report_the_incident_of_a_failing_centre_without_stopping_the_others_nor_propagating() {
        oubliee(CENTRE_A, -1, StatutDeSeance.CREE);
        oubliee(CENTRE_B, -1, StatutDeSeance.CREE);
        centresEnPanne.add(CENTRE_A);

        var resultats = cas().executer();

        assertThat(resultats.get(0).incident()).contains("IllegalStateException").contains("notifications indisponibles");
        assertThat(resultats.get(0).rappele()).isFalse();
        assertThat(resultats.get(1).rappele()).isTrue();
    }

    @Test
    void should_report_an_incident_when_the_day_of_a_centre_cannot_be_read() {
        horloge = new HorlogeDuCentre(Clock.fixed(Instant.parse("2026-06-15T05:00:00Z"), ZoneOffset.UTC), centre -> {
            if (centre.equals(CENTRE_A.valeur())) throw new IllegalStateException("fuseau illisible");
            return AUJOURDHUI;
        });
        oubliee(CENTRE_B, -1, StatutDeSeance.CREE);

        var resultats = cas().executer();

        assertThat(resultats.get(0).incident()).contains("fuseau illisible");
        assertThat(resultats.get(1).rappele()).isTrue();
    }

    @Test
    void should_only_read_the_first_page_of_one_line_to_count_and_date_the_oldest() {
        for (int i = 1; i <= 5; i++) oubliee(CENTRE_A, -i, StatutDeSeance.CREE);

        cas().executerPourLeCentre(CENTRE_A, AUJOURDHUI);

        assertThat(journalDesAppels.getFirst()).isEqualTo("lecture:1");
        assertThat(lignes).hasSize(5);
    }

    @Test
    void should_ask_the_history_for_the_window_of_the_day_of_the_centre() {
        var recues = new ArrayList<RechercheDeSeances>();
        var pages = new ArrayList<Pagination>();
        HistoriqueDesSeances espion = (centre, r, p) -> {
            recues.add(r);
            pages.add(p);
            return PagedResult.of(List.of(), 0, 0, 1);
        };

        new RappelerLesSeancesARegulariser(() -> centres, horloge, espion, rappels, verrous, unite)
                .executerPourLeCentre(CENTRE_A, AUJOURDHUI);

        assertThat(recues).singleElement().satisfies(r -> {
            assertThat(r.du()).isEqualTo(AUJOURDHUI.minusDays(7));
            assertThat(r.au()).isEqualTo(AUJOURDHUI.minusDays(1));
            assertThat(r.statuts()).containsExactly(StatutDeSeance.CREE);
            assertThat(r.deverrouillee()).isFalse();
        });
        assertThat(pages).containsExactly(new Pagination(0, 1));
    }
}

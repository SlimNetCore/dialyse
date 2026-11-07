package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.Pagination;
import com.hemodialyse.backend.contexts.seance.domain.RechercheDeSeances;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_A;
import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListerLesSeancesTest {

    private final Banc banc = new Banc();
    private CentreId centreInterroge;
    private RechercheDeSeances rechercheRecue;
    private Pagination paginationRecue;

    private ListerLesSeances lister() {
        HistoriqueDesSeances historique = (centre, recherche, pagination) -> {
            centreInterroge = centre;
            rechercheRecue = recherche;
            paginationRecue = pagination;
            var patient = banc.patient(CENTRE_A, 1, "P-001", "A1");
            var ligne = new HistoriqueDesSeances.LigneDeSeance(new com.hemodialyse.backend.contexts.seance.domain.SeanceId(UUID.randomUUID()),
                    centre, patient, "P-001", "Nom1", "Prenom1", LocalDate.of(2026, 6, 15), StatutDeSeance.CREE, null, null, null, null,
                    null, false, null);
            return PagedResult.of(List.of(ligne), 41, pagination.page(), pagination.taille());
        };
        ForfaitsDeSeance forfaits = (centre, s, p, jour) -> Optional.of(new ForfaitsDeSeance.ForfaitCourant(UUID.randomUUID(), "F1", "Forfait",
                java.math.BigDecimal.TEN, 0, null, null, false));
        return new ListerLesSeances(banc.session, historique, forfaits);
    }

    private static ListerLesSeances.Demande demande(UUID centre, String statuts, String tri, String sens, int page, int taille) {
        return new ListerLesSeances.Demande(centre, null, null, statuts, null, tri, sens, null, page, taille);
    }

    @Test
    void should_list_the_sessions_of_the_session_centre_with_the_current_forfait_of_each_line() {
        var page = lister().executer(demande(null, null, null, null, 0, 20));

        assertThat(centreInterroge).isEqualTo(CENTRE_A);
        assertThat(page.total()).isEqualTo(41);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().forfait()).isPresent();
    }

    @Test
    void should_search_by_decreasing_date_unless_ascending_is_asked() {
        lister().executer(demande(null, null, null, null, 0, 20));
        assertThat(rechercheRecue.decroissant()).isTrue();
        lister().executer(demande(null, null, "patient", "ASC", 0, 20));
        assertThat(rechercheRecue.decroissant()).isFalse();
        assertThat(rechercheRecue.ordre()).isEqualTo(RechercheDeSeances.OrdreDeTri.PATIENT);
    }

    @Test
    void should_read_the_statuses_of_the_client() {
        lister().executer(demande(null, "cree, validee", null, null, 0, 20));
        assertThat(rechercheRecue.statuts()).containsExactlyInAnyOrder(StatutDeSeance.CREE, StatutDeSeance.VALIDEE);
    }

    @Test
    void should_bound_the_page_and_the_size_instead_of_refusing_them() {
        lister().executer(demande(null, null, null, null, -2, 100000));
        assertThat(paginationRecue).isEqualTo(new Pagination(0, 500));
    }

    @Test
    void should_refuse_an_unknown_status_and_an_inverted_period_in_400() {
        assertThatThrownBy(() -> lister().executer(demande(null, "EN_COURS", null, null, 0, 20))).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> lister().executer(new ListerLesSeances.Demande(null, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1),
                null, null, null, null, null, 0, 20))).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_answer_403_before_400_when_the_centre_is_not_the_session_one() {
        assertThatThrownBy(() -> lister().executer(demande(CENTRE_B.valeur(), "EN_COURS", null, null, 0, 20))).isInstanceOf(AccesRefuse.class);
    }
}

package com.hemodialyse.backend.contexts.facturation.interfaces.publie;

import com.hemodialyse.backend.contexts.facturation.application.SessionCourante;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseDeFacturation;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La règle d'impression de la synthèse publiée à Documents : profil (administrateur ou secrétaire), centre, données du serveur. */
class RegleDImpressionDeLaSyntheseMensuelleTest {

    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-00000000000d");

    private final List<UUID> centresDemandes = new ArrayList<>();
    private final List<String> lectures = new ArrayList<>();

    private SessionCourante session(String... roles) {
        return new SessionCourante() {
            @Override
            public CentreId centre(UUID centreDemande) {
                centresDemandes.add(centreDemande);
                if (centreDemande != null && !centreDemande.equals(CENTRE)) throw new AccessDeniedException("Accès refusé : autre centre");
                return new CentreId(CENTRE);
            }

            @Override
            public UUID utilisateur() {
                return null;
            }

            @Override
            public Collection<String> roles() {
                return Set.of(roles);
            }
        };
    }

    private RegleDImpressionDeLaSyntheseMensuelle regle(String... roles) {
        SyntheseDeFacturation synthese = (centre, debut, fin) -> {
            lectures.add(debut + ".." + fin);
            throw new IllegalStateException("lecture de la synthèse");
        };
        return new RegleDImpressionDeLaSyntheseMensuelle(session(roles), synthese);
    }

    @Test
    void should_be_the_rule_of_the_monthly_billing_summary_on_both_routes() {
        var regle = regle("ROLE_ADMIN");

        assertThat(regle.type()).isEqualTo("SYNTHESE_FACTURATION_MENSUELLE");
        assertThat(regle.gardeAussiParLeModele()).isTrue();
    }

    @Test
    void should_let_an_administrator_or_a_secretary_print_even_with_other_roles() {
        regle("ROLE_ADMIN").garder(CENTRE);
        regle("ROLE_SECRETAIRE").garder(null);
        regle("ROLE_MEDECIN", "ROLE_SECRETAIRE").garder(CENTRE);

        assertThat(centresDemandes).containsExactly(CENTRE, null, CENTRE);
    }

    @Test
    void should_refuse_the_other_profiles_before_looking_at_the_centre() {
        for (String role : List.of("ROLE_MEDECIN", "ROLE_INFIRMIER", "ROLE_DIRECTION", "ROLE_SUPERADMIN")) {
            assertThatThrownBy(() -> regle(role).garder(CENTRE)).isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Profil insuffisant pour la synthèse de facturation");
        }
        assertThatThrownBy(() -> regle().garder(CENTRE)).isInstanceOf(AccessDeniedException.class);
        assertThat(centresDemandes).isEmpty();
    }

    @Test
    void should_refuse_another_centre_than_the_one_of_the_session() {
        assertThatThrownBy(() -> regle("ROLE_ADMIN").garder(UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void should_set_nothing_for_a_missing_or_reversed_period_and_fail_for_an_unreadable_one() {
        var regle = regle("ROLE_ADMIN");

        assertThat(regle.donneesDuServeur(CENTRE, Map.of())).isEmpty();
        assertThat(regle.donneesDuServeur(CENTRE, Map.of("PERIOD_START", "2026-02-01"))).isEmpty();
        assertThat(regle.donneesDuServeur(CENTRE, Map.of("PERIOD_START", "2026-03-01", "PERIOD_END", "2026-02-01"))).isEmpty();
        assertThatThrownBy(() -> regle.donneesDuServeur(CENTRE, Map.of("PERIOD_START", "illisible", "PERIOD_END", "2026-02-28")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Période illisible");
        assertThat(lectures).isEmpty();
    }

    @Test
    void should_read_the_summary_of_the_requested_period() {
        var regle = regle("ROLE_ADMIN");

        assertThatThrownBy(() -> regle.donneesDuServeur(CENTRE, Map.of("PERIOD_START", "2026-02-01", "PERIOD_END", "2026-02-28")))
                .hasMessage("lecture de la synthèse");
        assertThat(lectures).containsExactly(LocalDate.of(2026, 2, 1) + ".." + LocalDate.of(2026, 2, 28));
    }
}

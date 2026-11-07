package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIllisible;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_A;
import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsulterUneSeanceTest {

    private final Banc banc = new Banc();
    private PatientId patient;
    private SeanceId seance;
    private List<ConsommationsDeLaSeance.Ligne> consommables = List.of();
    private Optional<ForfaitsDeSeance.ForfaitCourant> forfait = Optional.empty();
    private Optional<VoletsDeLaSeance.VoletParamedical> paramedical = Optional.empty();

    @BeforeEach
    void uneSeanceDuCentre() {
        patient = banc.patient(CENTRE_A, 1, "P-001", "A1");
        var ligne = banc.ligne(CENTRE_A, patient, 1, StatutDeSeance.VALIDEE);
        banc.seances.semer(ligne);
        seance = ligne.id();
    }

    private ConsulterUneSeance consulter() {
        VoletsDeLaSeance volets = new VoletsDeLaSeance() {
            @Override
            public Optional<VoletParamedical> paramedical(com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId centre, SeanceId s) {
                return paramedical;
            }

            @Override
            public Optional<VoletMedical> medical(com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId centre, SeanceId s) {
                return Optional.empty();
            }
        };
        return new ConsulterUneSeance(banc.session, banc.seances, banc.patients, banc.generateurs(), volets,
                (centre, s) -> consommables, (centre, s, p, jour) -> forfait);
    }

    @Test
    void should_give_the_session_its_patient_and_an_empty_context_when_nothing_else_is_known() {
        var detail = consulter().executer(null, seance.valeur());

        assertThat(detail.seance().id()).isEqualTo(seance);
        assertThat(detail.patient().code()).isEqualTo("P-001");
        assertThat(detail.paramedical()).isEmpty();
        assertThat(detail.medical()).isEmpty();
        assertThat(detail.consommables()).isEmpty();
        assertThat(detail.totalValoriseDesConsommables()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(detail.forfait()).isEmpty();
    }

    @Test
    void should_total_the_valued_consumables_and_carry_the_forfait_and_the_volet() {
        consommables = List.of(
                new ConsommationsDeLaSeance.Ligne(UUID.randomUUID(), "DIAL", "Dialyseur", "u", BigDecimal.ONE, new BigDecimal("10.50"), new BigDecimal("10.50")),
                new ConsommationsDeLaSeance.Ligne(UUID.randomUUID(), "AIG", "Aiguille", "u", new BigDecimal("2"), new BigDecimal("1.25"), new BigDecimal("2.50")),
                new ConsommationsDeLaSeance.Ligne(UUID.randomUUID(), "X", "Sans valeur", "u", BigDecimal.ONE, null, null));
        forfait = Optional.of(new ForfaitsDeSeance.ForfaitCourant(UUID.randomUUID(), "F1", "Forfait", new BigDecimal("100"), 0, null, null, false));
        paramedical = Optional.of(new VoletsDeLaSeance.VoletParamedical(new BigDecimal("70"), null, null, null, 240, null, null, null, null, null));

        var detail = consulter().executer(null, seance.valeur());

        assertThat(detail.totalValoriseDesConsommables()).isEqualByComparingTo("13.00");
        assertThat(detail.forfait()).isPresent();
        assertThat(detail.paramedical().orElseThrow().dureeMinutes()).isEqualTo(240);
    }

    @Test
    void should_refuse_an_unknown_session_and_a_session_of_another_centre() {
        assertThatThrownBy(() -> consulter().executer(null, UUID.randomUUID())).isInstanceOf(SeanceIntrouvable.class);
        var autre = banc.patient(CENTRE_B, 2, "P-002", "B2");
        var ligneB = banc.ligne(CENTRE_B, autre, 2, StatutDeSeance.CREE);
        banc.seances.semer(ligneB);
        assertThatThrownBy(() -> consulter().executer(null, ligneB.id().valeur())).isInstanceOf(SeanceIntrouvable.class);
    }

    @Test
    void should_refuse_a_centre_that_is_not_the_session_one_before_any_other_check() {
        assertThatThrownBy(() -> consulter().executer(CENTRE_B.valeur(), null)).isInstanceOf(AccesRefuse.class);
        assertThatThrownBy(() -> consulter().executer(CENTRE_A.valeur(), null)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_refuse_in_400_a_session_whose_patient_has_disappeared() {
        banc.patients.fiches.clear();
        assertThatThrownBy(() -> consulter().executer(null, seance.valeur()))
                .isInstanceOf(SaisieInvalide.class).hasMessage("Patient introuvable");
    }

    @Test
    void should_report_an_unreadable_line_without_naming_any_technical_class() {
        var illisible = new SeanceId(UUID.randomUUID());
        banc.seances.semerUneLigneIllisible(CENTRE_A, illisible);
        assertThatThrownBy(() -> consulter().executer(null, illisible.valeur()))
                .isInstanceOf(SeanceIllisible.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("com.hemodialyse").doesNotContain("Exception"));
    }
}

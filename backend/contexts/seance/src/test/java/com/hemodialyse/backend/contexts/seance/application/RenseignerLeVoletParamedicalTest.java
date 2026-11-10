package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.contrat.VoletsParamedicauxEnMemoire;
import com.hemodialyse.backend.contexts.seance.domain.MesuresDeSeance;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.SeanceRefusee;
import com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance;
import com.hemodialyse.backend.contexts.seance.domain.VoletParamedical;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_A;
import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_B;
import static com.hemodialyse.backend.contexts.seance.application.Banc.MAINTENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SE7f (ADR 0100) : saisie du volet paramédical, ports en mémoire ; parité DM1 et KB-SEA-93, 94 reproduits. */
class RenseignerLeVoletParamedicalTest {

    private Banc banc;
    private VoletsParamedicauxEnMemoire volets;
    private PatientId patient;

    @BeforeEach
    void preparer() {
        banc = new Banc();
        volets = new VoletsParamedicauxEnMemoire();
        patient = banc.patient(CENTRE_A, 1, "P001", "A1");
    }

    private RenseignerLeVoletParamedical cas() {
        return new RenseignerLeVoletParamedical(banc.session, banc.seances, volets, banc.patients, banc.unite(), banc.identifiants(),
                banc.horloge(), banc.diffusions());
    }

    private Seance semer(int n, StatutDeSeance statut) {
        var s = banc.ligne(CENTRE_A, patient, n, statut);
        banc.seances.semer(s);
        return s;
    }

    private static MesuresDeSeance completes() {
        return MesuresDeSeance.saisies(new BigDecimal("70.5"), new BigDecimal("68.0"), "130/80", "120/70", 240, 300, new BigDecimal("2000"),
                "HEPARINE", "STANDARD", "RAS");
    }

    @Test
    void should_create_the_sheet_under_the_row_lock_then_publish_the_two_legacy_diffusions_in_order() {
        var s = semer(1, StatutDeSeance.VALIDEE);

        VoletParamedical volet = cas().executer(CENTRE_A.valeur(), s.id().valeur(), RenseignerLeVoletParamedicalTest::completes);

        assertThat(volet.id()).isEqualTo(new UUID(9, 100));
        assertThat(volet.mesures()).isEqualTo(completes());
        assertThat(volet.creeLe()).isEqualTo(MAINTENANT);
        assertThat(volets.deLaSeance(CENTRE_A, s.id())).isPresent();
        assertThat(banc.seances.verrousDeLigneDemandes).isEqualTo(1);
        assertThat(banc.diffusionsRecues).containsExactly("SEANCE_PARAMEDICAL_SAVED:Nom1", "PARAMEDICAL:u-infirmier");
    }

    @Test
    void should_replace_everything_so_that_a_measure_left_out_erases_the_value_already_entered_and_keep_one_sheet() {
        var s = semer(1, StatutDeSeance.VALIDEE);
        cas().executer(CENTRE_A.valeur(), s.id().valeur(), RenseignerLeVoletParamedicalTest::completes);
        banc.horlogeA("2026-06-15T12:00:00Z");

        var volet = cas().executer(CENTRE_A.valeur(), s.id().valeur(),
                () -> MesuresDeSeance.saisies(new BigDecimal("71.2"), null, null, null, null, null, null, null, null, null));

        assertThat(volet.id()).isEqualTo(new UUID(9, 100));
        assertThat(volets.nombre()).isEqualTo(1);
        var relu = volets.deLaSeance(CENTRE_A, s.id()).orElseThrow();
        assertThat(relu.mesures().poidsAvantKg()).isEqualByComparingTo("71.2");
        assertThat(relu.mesures().anticoagulant()).isNull();
        assertThat(relu.creeLe()).isEqualTo(MAINTENANT);
        assertThat(relu.modifieLe()).isEqualTo(Instant.parse("2026-06-15T12:00:00Z"));
    }

    @Test
    void should_accept_every_status_but_the_invoiced_one_which_is_refused_before_the_measures_are_read() {
        for (var statut : new StatutDeSeance[]{StatutDeSeance.CREE, StatutDeSeance.VALIDEE, StatutDeSeance.SIGNEE, StatutDeSeance.ABSENT}) {
            var s = semer(10 + statut.ordinal(), statut);
            cas().executer(CENTRE_A.valeur(), s.id().valeur(), RenseignerLeVoletParamedicalTest::completes);
        }
        assertThat(volets.nombre()).isEqualTo(4);

        var facturee = semer(30, StatutDeSeance.FACTUREE);
        assertThatThrownBy(() -> cas().executer(CENTRE_A.valeur(), facturee.id().valeur(), () -> {
            throw new IllegalStateException("les mesures ne doivent pas être lues");
        })).isInstanceOfSatisfying(SeanceRefusee.class, e -> assertThat(e.motif().code()).isEqualTo("SEANCE_FACTUREE_NON_MODIFIABLE"));
        assertThat(volets.nombre()).isEqualTo(4);
    }

    @Test
    void should_refuse_an_unknown_session_and_a_session_of_another_centre_before_the_measures_are_read() {
        var autre = banc.ligne(CENTRE_B, banc.patient(CENTRE_B, 2, "P002", "B1"), 5, StatutDeSeance.VALIDEE);
        banc.seances.semer(autre);

        assertThatThrownBy(() -> cas().executer(CENTRE_A.valeur(), new UUID(5, 99), RenseignerLeVoletParamedicalTest::completes))
                .isInstanceOf(SeanceIntrouvable.class);
        assertThatThrownBy(() -> cas().executer(CENTRE_A.valeur(), autre.id().valeur(), RenseignerLeVoletParamedicalTest::completes))
                .isInstanceOf(SeanceIntrouvable.class);
        assertThat(volets.nombre()).isZero();
        assertThat(banc.diffusionsRecues).isEmpty();
    }

    @Test
    void should_refuse_another_centre_than_the_session_before_anything_else() {
        var s = semer(1, StatutDeSeance.VALIDEE);

        assertThatThrownBy(() -> cas().executer(CENTRE_B.valeur(), s.id().valeur(), () -> {
            throw new SaisieInvalide("ne doit pas être lu");
        })).isInstanceOf(AccesRefuse.class);
        assertThat(banc.seances.verrousDeLigneDemandes).isZero();
    }

    @Test
    void should_write_nothing_when_a_measure_is_refused() {
        var s = semer(1, StatutDeSeance.VALIDEE);

        assertThatThrownBy(() -> cas().executer(CENTRE_A.valeur(), s.id().valeur(),
                () -> MesuresDeSeance.saisies(BigDecimal.ZERO, null, null, null, null, null, null, null, null, null)))
                .isInstanceOfSatisfying(SeanceRefusee.class, e -> assertThat(e.getMessage()).isEqualTo("Le poids doit être strictement positif"));
        assertThat(volets.nombre()).isZero();
        assertThat(banc.diffusionsRecues).isEmpty();
    }

    /** KB-SEA-93, corrigé (L4, 2026-11-10) : la fiche est gardée AVANT l'écriture ; fiche hors centre : 400, rien d'écrit, rien de diffusé. */
    @Test
    void should_write_nothing_and_publish_nothing_when_the_record_is_in_another_centre() {
        var ficheAilleurs = banc.patient(CENTRE_B, 3, "P003", "C1");
        var s = banc.ligne(CENTRE_A, ficheAilleurs, 7, StatutDeSeance.VALIDEE);
        banc.seances.semer(s);

        assertThatThrownBy(() -> cas().executer(CENTRE_A.valeur(), s.id().valeur(), RenseignerLeVoletParamedicalTest::completes))
                .isInstanceOf(SaisieInvalide.class).hasMessage("Patient introuvable");

        assertThat(volets.nombre()).isZero();
        assertThat(banc.diffusionsRecues).isEmpty();
        assertThatThrownBy(() -> cas().executer(CENTRE_A.valeur(), s.id().valeur(),
                () -> MesuresDeSeance.saisies(new BigDecimal("71"), null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(SaisieInvalide.class);
        assertThat(volets.nombre()).isZero();
    }

    /** KB-SEA-93 : l'ordre des refus est conservé, une mesure refusée (422) précède toujours la fiche absente (400). */
    @Test
    void should_keep_the_measure_refusal_before_the_missing_record_refusal() {
        var ficheAilleurs = banc.patient(CENTRE_B, 3, "P003", "C1");
        var s = banc.ligne(CENTRE_A, ficheAilleurs, 7, StatutDeSeance.VALIDEE);
        banc.seances.semer(s);

        assertThatThrownBy(() -> cas().executer(CENTRE_A.valeur(), s.id().valeur(),
                () -> MesuresDeSeance.saisies(BigDecimal.ZERO, null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(SeanceRefusee.class);
        assertThat(volets.nombre()).isZero();
    }

    /** KB-SEA-94, corrigé (L4) : le volet de la séance posé sous un autre centre est trouvé par la séance et remplacé, sans doublon. */
    @Test
    void should_replace_the_sheet_of_the_session_even_when_it_was_laid_under_another_centre() {
        var s = semer(1, StatutDeSeance.VALIDEE);
        volets.enregistrer(VoletParamedical.ouvrir(new UUID(8, 9), CENTRE_B, s.id(), MesuresDeSeance.aucune(), MAINTENANT));

        var volet = cas().executer(CENTRE_A.valeur(), s.id().valeur(), RenseignerLeVoletParamedicalTest::completes);

        assertThat(volet.id()).isEqualTo(new UUID(8, 9));
        assertThat(volets.nombre()).isEqualTo(1);
        assertThat(volets.deLaSeance(CENTRE_A, s.id()).orElseThrow().mesures()).isEqualTo(completes());
        assertThat(banc.diffusionsRecues).containsExactly("SEANCE_PARAMEDICAL_SAVED:Nom1", "PARAMEDICAL:u-infirmier");
    }

    @Test
    void should_propagate_the_concurrent_write_and_write_nothing_when_the_row_lock_is_refused() {
        var s = semer(1, StatutDeSeance.VALIDEE);
        var seancesVerrouillees = org.mockito.Mockito.mock(com.hemodialyse.backend.contexts.seance.domain.Seances.class);
        org.mockito.Mockito.when(seancesVerrouillees.trouverPourModifier(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new com.hemodialyse.backend.contexts.seance.domain.EcritureConcurrente());
        var refuse = new RenseignerLeVoletParamedical(banc.session, seancesVerrouillees, volets, banc.patients, banc.unite(),
                banc.identifiants(), banc.horloge(), banc.diffusions());

        assertThatThrownBy(() -> refuse.executer(CENTRE_A.valeur(), s.id().valeur(), RenseignerLeVoletParamedicalTest::completes))
                .isInstanceOf(com.hemodialyse.backend.contexts.seance.domain.EcritureConcurrente.class);
        assertThat(volets.nombre()).isZero();
    }
}

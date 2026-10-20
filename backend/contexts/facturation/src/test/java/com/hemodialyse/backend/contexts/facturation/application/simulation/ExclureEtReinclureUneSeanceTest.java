package com.hemodialyse.backend.contexts.facturation.application.simulation;

import com.hemodialyse.backend.contexts.facturation.domain.seance.MotifDAbsence;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.FactureSimulee;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SeanceFacturable;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.Simulation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.StatutDeSeance;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.facturation.application.simulation.SimulationFixture.AMRANI;
import static com.hemodialyse.backend.contexts.facturation.application.simulation.SimulationFixture.AOUT;
import static com.hemodialyse.backend.contexts.facturation.application.simulation.SimulationFixture.AUTRE_CENTRE;
import static com.hemodialyse.backend.contexts.facturation.application.simulation.SimulationFixture.CENTRE;
import static com.hemodialyse.backend.contexts.facturation.application.simulation.SimulationFixture.UTILISATEUR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExclureEtReinclureUneSeanceTest {

    private final SimulationFixture decor = new SimulationFixture();
    private final ExclureUneSeance exclure = new ExclureUneSeance(decor.seances, decor.absences, decor.simuler, decor.unite);
    private final ReinclureUneSeance reinclure = new ReinclureUneSeance(decor.seances, decor.absences, decor.simuler, decor.unite);

    private static ExclureUneSeance.Commande commande(MotifDAbsence motif, String commentaire) {
        return new ExclureUneSeance.Commande(motif, commentaire, UTILISATEUR);
    }

    private static long nombreDeSeances(Simulation simulation) {
        return simulation.factures().stream().flatMap(f -> f.seances().stream()).count();
    }

    // ---- exclusion

    @Test
    void should_exclude_a_seance_declare_an_absence_with_its_reason_and_recompute_the_simulation() {
        UUID exclue = decor.seance(AMRANI, 2, "VALIDEE");
        decor.seance(AMRANI, 3, "SIGNEE");

        Simulation simulation = exclure.executer(CENTRE, exclue, commande(MotifDAbsence.MALADIE, "grippe"), AOUT);

        assertThat(nombreDeSeances(simulation)).isEqualTo(1);
        assertThat(decor.seances.trouver(CENTRE, exclue).orElseThrow().statut()).isEqualTo(StatutDeSeance.ABSENT);
        assertThat(decor.seances.trouver(CENTRE, exclue).orElseThrow().statutAvantExclusion()).isEqualTo(StatutDeSeance.VALIDEE);
        assertThat(decor.absences.declarees().values()).singleElement().satisfies(declaration -> {
            assertThat(declaration.patient()).isEqualTo(AMRANI);
            assertThat(declaration.dateDeSeance()).isEqualTo(LocalDate.of(2026, 8, 2));
            assertThat(declaration.motif()).isEqualTo(MotifDAbsence.MALADIE);
            assertThat(declaration.commentaire()).isEqualTo("grippe");
            assertThat(declaration.auteur()).isEqualTo(UTILISATEUR);
        });
        UUID absence = decor.absences.declarees().keySet().iterator().next();
        assertThat(decor.seances.trouver(CENTRE, exclue).orElseThrow().absenceDeLExclusion()).isEqualTo(absence);
    }

    @Test
    void should_refuse_to_exclude_an_unknown_seance_or_one_of_another_centre() {
        UUID duCentre = decor.seance(AMRANI, 2, "VALIDEE");

        assertThatThrownBy(() -> exclure.executer(CENTRE, UUID.randomUUID(), commande(MotifDAbsence.MALADIE, null), AOUT))
                .isInstanceOf(IllegalStateException.class).hasMessage("Seance introuvable ou non modifiable pour exclusion");
        assertThatThrownBy(() -> exclure.executer(AUTRE_CENTRE, duCentre, commande(MotifDAbsence.MALADIE, null), AOUT))
                .isInstanceOf(IllegalStateException.class).hasMessage("Seance introuvable ou non modifiable pour exclusion");
        assertThat(decor.absences.declarees()).isEmpty();
    }

    @Test
    void should_refuse_to_exclude_a_seance_already_excluded_invoiced_or_not_validated() {
        UUID exclue = decor.seance(AMRANI, 2, "VALIDEE");
        UUID brouillon = decor.seance(AMRANI, 3, "BROUILLON");
        UUID facturee = UUID.randomUUID();
        decor.seances.seance(facturee, CENTRE, AMRANI, LocalDate.of(2026, 8, 4), "VALIDEE", true);
        exclure.executer(CENTRE, exclue, commande(MotifDAbsence.MALADIE, null), AOUT);

        for (UUID seance : new UUID[]{exclue, brouillon, facturee}) {
            assertThatThrownBy(() -> exclure.executer(CENTRE, seance, commande(MotifDAbsence.MALADIE, null), AOUT))
                    .isInstanceOf(IllegalStateException.class);
        }
        assertThat(decor.absences.declarees()).hasSize(1);
    }

    @Test
    void should_require_a_reason_and_a_comment_with_autre_before_changing_anything() {
        UUID seance = decor.seance(AMRANI, 2, "VALIDEE");

        assertThatThrownBy(() -> exclure.executer(CENTRE, seance, commande(null, null), AOUT))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Le motif d'exclusion est obligatoire");
        assertThatThrownBy(() -> exclure.executer(CENTRE, seance, commande(MotifDAbsence.AUTRE, null), AOUT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(decor.seances.trouver(CENTRE, seance).orElseThrow().statut()).isEqualTo(StatutDeSeance.VALIDEE);
        assertThat(decor.absences.declarees()).isEmpty();
    }

    @Test
    void should_change_nothing_when_the_period_is_missing() {
        UUID seance = decor.seance(AMRANI, 2, "VALIDEE");

        assertThatThrownBy(() -> exclure.executer(CENTRE, seance, commande(MotifDAbsence.MALADIE, null),
                new DemandeDeSimulation(null, null, null, true)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("La periode de facturation est obligatoire");
        assertThat(decor.seances.trouver(CENTRE, seance).orElseThrow().statut()).isEqualTo(StatutDeSeance.VALIDEE);
        assertThat(decor.absences.declarees()).isEmpty();
    }

    @Test
    void should_let_the_refusal_of_the_absences_module_through() {
        UUID seance = decor.seance(AMRANI, 2, "VALIDEE");
        decor.absences.refuserLaDeclaration(new IllegalStateException("La facturation de cette période est validée"));

        assertThatThrownBy(() -> exclure.executer(CENTRE, seance, commande(MotifDAbsence.MALADIE, null), AOUT))
                .isInstanceOf(IllegalStateException.class).hasMessage("La facturation de cette période est validée");
    }

    // ---- réinclusion

    @Test
    void should_reinclude_an_excluded_seance_with_its_former_status_and_remove_the_absence() {
        UUID seance = decor.seance(AMRANI, 2, "SIGNEE");
        exclure.executer(CENTRE, seance, commande(MotifDAbsence.VOYAGE, null), AOUT);
        UUID absence = decor.absences.declarees().keySet().iterator().next();

        Simulation simulation = reinclure.executer(CENTRE, seance, AOUT);

        assertThat(nombreDeSeances(simulation)).isEqualTo(1);
        SeanceFacturable revenue = simulation.factures().getFirst().seances().getFirst();
        assertThat(revenue.statut()).isEqualTo(StatutDeSeance.SIGNEE);
        assertThat(decor.seances.trouver(CENTRE, seance).orElseThrow().statutAvantExclusion()).isNull();
        assertThat(decor.absences.retirees()).containsExactly(absence);
        assertThat(decor.absences.declarees()).isEmpty();
    }

    @Test
    void should_allow_to_exclude_again_after_a_reinclusion() {
        UUID seance = decor.seance(AMRANI, 2, "VALIDEE");
        exclure.executer(CENTRE, seance, commande(MotifDAbsence.VOYAGE, null), AOUT);
        reinclure.executer(CENTRE, seance, AOUT);

        exclure.executer(CENTRE, seance, commande(MotifDAbsence.MALADIE, null), AOUT);

        assertThat(decor.absences.declarees().values()).singleElement().extracting(a -> a.motif()).isEqualTo(MotifDAbsence.MALADIE);
    }

    @Test
    void should_refuse_to_reinclude_a_seance_that_was_not_excluded_or_is_unknown() {
        UUID validee = decor.seance(AMRANI, 2, "VALIDEE");
        UUID absenteReelle = decor.seance(AMRANI, 3, "ABSENT");

        for (UUID seance : new UUID[]{validee, absenteReelle, UUID.randomUUID()}) {
            assertThatThrownBy(() -> reinclure.executer(CENTRE, seance, AOUT))
                    .isInstanceOf(IllegalStateException.class).hasMessage("Seance introuvable ou non reinclusable");
        }
        assertThat(decor.absences.retirees()).isEmpty();
    }

    @Test
    void should_refuse_to_reinclude_a_seance_of_another_centre() {
        UUID seance = decor.seance(AMRANI, 2, "VALIDEE");
        exclure.executer(CENTRE, seance, commande(MotifDAbsence.VOYAGE, null), AOUT);

        assertThatThrownBy(() -> reinclure.executer(AUTRE_CENTRE, seance, AOUT)).isInstanceOf(IllegalStateException.class);
        assertThat(decor.seances.trouver(CENTRE, seance).orElseThrow().statut()).isEqualTo(StatutDeSeance.ABSENT);
    }

    @Test
    void should_report_a_factures_view_of_the_remaining_seances() {
        UUID a = decor.seance(AMRANI, 2, "VALIDEE");
        decor.seance(AMRANI, 3, "VALIDEE");

        FactureSimulee facture = exclure.executer(CENTRE, a, commande(MotifDAbsence.MALADIE, null), AOUT).factures().getFirst();

        assertThat(facture.lignes().getFirst().nombreDeSeances()).isEqualTo(1);
    }
}

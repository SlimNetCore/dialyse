package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.facture.LiaisonDesSeances;
import com.hemodialyse.backend.contexts.facturation.domain.seance.SeanceDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.seance.SeancesDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitApplique;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SeanceFacturable;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.StatutDeSeance;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat partagé des séances vues par la Facturation (lecture des séances facturables, écritures d'exclusion, de réinclusion
 * et de surcharge de forfait, liaison) : exécuté contre le double en mémoire seulement depuis SE7c2 (les écritures sur {@code seances} sont
 * jouées sur H2 et PostgreSQL 16 par le contrat de la Séance, {@code FacturationDesSeancesContrat}). Hérite des 5 cas du forfait appliqué
 * ({@link ForfaitAppliqueContrat}), qui sont aussi joués sur JDBC. Le décor (patients, forfaits, prises en charge, séances) est posé par
 * chaque environnement.
 */
public abstract class SeancesContrat extends ForfaitAppliqueContrat {

    private static final Instant LE_20 = Instant.parse("2026-10-20T08:00:00Z");

    protected abstract SeancesDeFacturation seances();

    protected abstract LiaisonDesSeances liaison();

    /**
     * Donne au patient son centre payeur (et l'agence de celui-ci) ; son numéro d'immatriculation est {@code ASS-<code>}.
     */
    protected abstract void payeur(UUID patient, CentreId centre, UUID centrePayeur, UUID agence);

    protected abstract String statutDe(UUID seance);

    protected abstract UUID factureDe(UUID seance);

    private List<UUID> identifiantsFacturables(CentreId centre) {
        return facturables().dansLaPeriode(centre, AOUT).stream().map(SeanceFacturable::id).toList();
    }

    // ---- séances facturables

    @Test
    void should_read_only_validated_or_signed_seances_not_invoiced_of_the_centre_within_the_period() {
        decor();
        UUID validee = uneSeance(CENTRE_A, AMRANI, "2026-08-02", "VALIDEE", false);
        UUID signee = uneSeance(CENTRE_A, AMRANI, "2026-08-03", "SIGNEE", false);
        uneSeance(CENTRE_A, AMRANI, "2026-08-04", "BROUILLON", false);
        uneSeance(CENTRE_A, AMRANI, "2026-08-05", "ABSENT", false);
        uneSeance(CENTRE_A, AMRANI, "2026-08-06", "validee", false);
        uneSeance(CENTRE_A, AMRANI, "2026-08-07", "VALIDEE", true);
        uneSeance(CENTRE_A, AMRANI, "2026-07-31", "VALIDEE", false);
        uneSeance(CENTRE_A, AMRANI, "2026-09-01", "VALIDEE", false);
        uneSeance(CENTRE_B, PATIENT_B, "2026-08-02", "VALIDEE", false);

        assertThat(identifiantsFacturables(CENTRE_A)).containsExactly(validee, signee);
    }

    @Test
    void should_include_the_first_and_the_last_day_of_the_period() {
        decor();
        UUID premier = uneSeance(CENTRE_A, AMRANI, "2026-08-01", "VALIDEE", false);
        UUID dernier = uneSeance(CENTRE_A, AMRANI, "2026-08-31", "SIGNEE", false);

        assertThat(identifiantsFacturables(CENTRE_A)).containsExactly(premier, dernier);
    }

    @Test
    void should_order_the_seances_by_patient_name_then_date() {
        decor();
        UUID belkacem = uneSeance(CENTRE_A, BELKACEM, "2026-08-01", "VALIDEE", false);
        UUID amraniTard = uneSeance(CENTRE_A, AMRANI, "2026-08-09", "VALIDEE", false);
        UUID amraniTot = uneSeance(CENTRE_A, AMRANI, "2026-08-03", "VALIDEE", false);

        assertThat(identifiantsFacturables(CENTRE_A)).containsExactly(amraniTot, amraniTard, belkacem);
    }

    @Test
    void should_read_the_patient_of_each_seance() {
        decor();
        uneSeance(CENTRE_A, BELKACEM, "2026-08-01", "VALIDEE", false);

        SeanceFacturable lue = facturables().dansLaPeriode(CENTRE_A, AOUT).getFirst();

        assertThat(lue.patient().id()).isEqualTo(BELKACEM);
        assertThat(lue.patient().code()).isEqualTo("P-002");
        assertThat(lue.patient().nom()).isEqualTo("Belkacem");
        assertThat(lue.patient().prenom()).isEqualTo("Omar");
        assertThat(lue.patient().etat()).isNull();
        assertThat(lue.date()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(lue.statut()).isEqualTo(StatutDeSeance.VALIDEE);
    }

    // ---- séance modifiable

    @Test
    void should_find_a_seance_of_the_centre_with_its_state() {
        decor();
        UUID id = uneSeance(CENTRE_A, AMRANI, "2026-08-02", "VALIDEE", false);

        SeanceDeFacturation trouvee = seances().trouver(CENTRE_A, id).orElseThrow();

        assertThat(trouvee.id()).isEqualTo(id);
        assertThat(trouvee.centre()).isEqualTo(CENTRE_A);
        assertThat(trouvee.patientId()).isEqualTo(AMRANI);
        assertThat(trouvee.date()).isEqualTo(LocalDate.of(2026, 8, 2));
        assertThat(trouvee.statut()).isEqualTo(StatutDeSeance.VALIDEE);
        assertThat(trouvee.facturee()).isFalse();
        assertThat(trouvee.statutAvantExclusion()).isNull();
        assertThat(trouvee.absenceDeLExclusion()).isNull();
    }

    @Test
    void should_not_find_an_unknown_seance_nor_one_of_another_centre() {
        decor();
        UUID id = uneSeance(CENTRE_A, AMRANI, "2026-08-02", "VALIDEE", false);

        assertThat(seances().trouver(CENTRE_A, UUID.randomUUID())).isEmpty();
        assertThat(seances().trouver(CENTRE_B, id)).isEmpty();
    }

    @Test
    void should_see_an_invoiced_seance_as_invoiced() {
        decor();
        UUID id = uneSeance(CENTRE_A, AMRANI, "2026-08-02", "FACTUREE", true);

        assertThat(seances().trouver(CENTRE_A, id).orElseThrow().facturee()).isTrue();
    }

    // ---- exclusion et réinclusion

    @Test
    void should_exclude_a_seance_remember_its_status_and_remove_it_from_the_facturables() {
        decor();
        UUID id = uneSeance(CENTRE_A, AMRANI, "2026-08-02", "SIGNEE", false);

        seances().exclure(CENTRE_A, id, StatutDeSeance.SIGNEE);

        SeanceDeFacturation exclue = seances().trouver(CENTRE_A, id).orElseThrow();
        assertThat(exclue.statut()).isEqualTo(StatutDeSeance.ABSENT);
        assertThat(exclue.statutAvantExclusion()).isEqualTo(StatutDeSeance.SIGNEE);
        assertThat(identifiantsFacturables(CENTRE_A)).isEmpty();
    }

    @Test
    void should_remember_the_absence_declared_by_the_exclusion() {
        decor();
        UUID id = uneSeance(CENTRE_A, AMRANI, "2026-08-02", "VALIDEE", false);
        UUID absence = UUID.randomUUID();
        seances().exclure(CENTRE_A, id, StatutDeSeance.VALIDEE);

        seances().lierALAbsence(CENTRE_A, id, absence);

        assertThat(seances().trouver(CENTRE_A, id).orElseThrow().absenceDeLExclusion()).isEqualTo(absence);
    }

    @Test
    void should_reinclude_an_excluded_seance_with_its_former_status_and_forget_the_exclusion() {
        decor();
        UUID id = uneSeance(CENTRE_A, AMRANI, "2026-08-02", "SIGNEE", false);
        seances().exclure(CENTRE_A, id, StatutDeSeance.SIGNEE);
        seances().lierALAbsence(CENTRE_A, id, UUID.randomUUID());

        seances().reinclure(CENTRE_A, id, StatutDeSeance.SIGNEE);

        SeanceDeFacturation rendue = seances().trouver(CENTRE_A, id).orElseThrow();
        assertThat(rendue.statut()).isEqualTo(StatutDeSeance.SIGNEE);
        assertThat(rendue.statutAvantExclusion()).isNull();
        assertThat(rendue.absenceDeLExclusion()).isNull();
        assertThat(identifiantsFacturables(CENTRE_A)).containsExactly(id);
    }

    @Test
    void should_refuse_to_exclude_a_seance_in_another_status_invoiced_unknown_or_of_another_centre() {
        decor();
        UUID validee = uneSeance(CENTRE_A, AMRANI, "2026-08-02", "VALIDEE", false);
        UUID facturee = uneSeance(CENTRE_A, AMRANI, "2026-08-03", "VALIDEE", true);

        assertThatThrownBy(() -> seances().exclure(CENTRE_A, validee, StatutDeSeance.SIGNEE)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> seances().exclure(CENTRE_A, facturee, StatutDeSeance.VALIDEE)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> seances().exclure(CENTRE_A, UUID.randomUUID(), StatutDeSeance.VALIDEE))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> seances().exclure(CENTRE_B, validee, StatutDeSeance.VALIDEE)).isInstanceOf(IllegalStateException.class);
        assertThat(seances().trouver(CENTRE_A, validee).orElseThrow().statut()).isEqualTo(StatutDeSeance.VALIDEE);
    }

    @Test
    void should_refuse_to_link_an_absence_to_a_seance_that_was_not_excluded() {
        decor();
        UUID validee = uneSeance(CENTRE_A, AMRANI, "2026-08-02", "VALIDEE", false);
        UUID absenteReelle = uneSeance(CENTRE_A, AMRANI, "2026-08-03", "ABSENT", false);

        assertThatThrownBy(() -> seances().lierALAbsence(CENTRE_A, validee, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> seances().lierALAbsence(CENTRE_A, absenteReelle, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_refuse_to_reinclude_a_seance_that_was_not_excluded_by_the_facturation() {
        decor();
        UUID validee = uneSeance(CENTRE_A, AMRANI, "2026-08-02", "VALIDEE", false);
        UUID absenteReelle = uneSeance(CENTRE_A, AMRANI, "2026-08-03", "ABSENT", false);

        assertThatThrownBy(() -> seances().reinclure(CENTRE_A, validee, StatutDeSeance.VALIDEE))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> seances().reinclure(CENTRE_A, absenteReelle, StatutDeSeance.VALIDEE))
                .isInstanceOf(IllegalStateException.class);
        assertThat(seances().trouver(CENTRE_A, absenteReelle).orElseThrow().statut()).isEqualTo(StatutDeSeance.ABSENT);
    }

    // ---- surcharge de forfait

    @Test
    void should_impose_a_forfait_on_a_seance_and_read_it_back_in_the_facturables() {
        decor();
        UUID id = uneSeance(CENTRE_A, AMRANI, "2026-08-02", "VALIDEE", false);

        seances().poserLeForfait(CENTRE_A, id, new ForfaitApplique(PREMIUM, "Forfait HD Premium", new BigDecimal("5200.00")),
                "medecin", LE_20);

        ForfaitApplique forfait = facturables().dansLaPeriode(CENTRE_A, AOUT).getFirst().forfait();
        assertThat(forfait.id()).isEqualTo(PREMIUM);
        assertThat(forfait.libelle()).isEqualTo("Forfait HD Premium");
        assertThat(forfait.prixHt()).isEqualByComparingTo("5200.00");
    }

    @Test
    void should_refuse_to_impose_a_forfait_on_an_excluded_invoiced_unknown_or_foreign_seance() {
        decor();
        UUID exclue = uneSeance(CENTRE_A, AMRANI, "2026-08-02", "VALIDEE", false);
        seances().exclure(CENTRE_A, exclue, StatutDeSeance.VALIDEE);
        UUID facturee = uneSeance(CENTRE_A, AMRANI, "2026-08-03", "VALIDEE", true);
        UUID validee = uneSeance(CENTRE_A, AMRANI, "2026-08-04", "VALIDEE", false);
        ForfaitApplique premium = new ForfaitApplique(PREMIUM, "Forfait HD Premium", new BigDecimal("5200.00"));

        for (UUID refusee : new UUID[]{exclue, facturee, UUID.randomUUID()}) {
            assertThatThrownBy(() -> seances().poserLeForfait(CENTRE_A, refusee, premium, "medecin", LE_20))
                    .isInstanceOf(IllegalStateException.class);
        }
        assertThatThrownBy(() -> seances().poserLeForfait(CENTRE_B, validee, premium, "medecin", LE_20))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- payeur du patient

    @Test
    void should_read_the_registration_number_and_the_payer_of_the_patient() {
        decor();
        UUID centrePayeur = UUID.fromString("99996800-0000-0000-0000-000000000c01");
        UUID agence = UUID.fromString("99996800-0000-0000-0000-000000000a61");
        payeur(AMRANI, CENTRE_A, centrePayeur, agence);
        uneSeance(CENTRE_A, AMRANI, "2026-08-02", "VALIDEE", false);

        var patient = facturables().dansLaPeriode(CENTRE_A, AOUT).getFirst().patient();

        assertThat(patient.numeroDImmatriculation()).isEqualTo("ASS-P-001");
        assertThat(patient.centrePayeur()).isEqualTo(centrePayeur);
        assertThat(patient.agence()).isEqualTo(agence);
    }

    @Test
    void should_read_a_patient_without_payer() {
        decor();
        uneSeance(CENTRE_A, BELKACEM, "2026-08-02", "VALIDEE", false);

        var patient = facturables().dansLaPeriode(CENTRE_A, AOUT).getFirst().patient();

        assertThat(patient.centrePayeur()).isNull();
        assertThat(patient.agence()).isNull();
    }

    // ---- liaison séance → facture

    @Test
    void should_link_the_seances_to_the_invoice_and_bill_them() {
        decor();
        UUID validee = uneSeance(CENTRE_A, AMRANI, "2026-08-02", "VALIDEE", false);
        UUID signee = uneSeance(CENTRE_A, AMRANI, "2026-08-03", "SIGNEE", false);
        UUID autre = uneSeance(CENTRE_A, AMRANI, "2026-08-04", "VALIDEE", false);
        UUID facture = UUID.randomUUID();

        liaison().lier(CENTRE_A, facture, List.of(validee, signee));

        assertThat(statutDe(validee)).isEqualTo("FACTUREE");
        assertThat(statutDe(signee)).isEqualTo("FACTUREE");
        assertThat(factureDe(validee)).isEqualTo(facture);
        assertThat(factureDe(signee)).isEqualTo(facture);
        assertThat(statutDe(autre)).isEqualTo("VALIDEE");
        assertThat(factureDe(autre)).isNull();
        assertThat(identifiantsFacturables(CENTRE_A)).containsExactly(autre);
    }

    @Test
    void should_refuse_to_link_a_seance_that_is_no_longer_eligible() {
        decor();
        UUID facturee = uneSeance(CENTRE_A, AMRANI, "2026-08-02", "VALIDEE", true);
        UUID exclue = uneSeance(CENTRE_A, AMRANI, "2026-08-03", "ABSENT", false);
        UUID brouillon = uneSeance(CENTRE_A, AMRANI, "2026-08-04", "BROUILLON", false);
        UUID minuscule = uneSeance(CENTRE_A, AMRANI, "2026-08-05", "validee", false);

        for (UUID refusee : new UUID[]{facturee, exclue, brouillon, minuscule, UUID.randomUUID()}) {
            assertThatThrownBy(() -> liaison().lier(CENTRE_A, UUID.randomUUID(), List.of(refusee)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Une ou plusieurs seances ne sont plus eligibles a la validation");
        }
    }

    @Test
    void should_refuse_to_link_a_seance_of_another_centre() {
        decor();
        UUID duCentreB = uneSeance(CENTRE_B, PATIENT_B, "2026-08-02", "VALIDEE", false);

        assertThatThrownBy(() -> liaison().lier(CENTRE_A, UUID.randomUUID(), List.of(duCentreB)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(statutDe(duCentreB)).isEqualTo("VALIDEE");
    }
}

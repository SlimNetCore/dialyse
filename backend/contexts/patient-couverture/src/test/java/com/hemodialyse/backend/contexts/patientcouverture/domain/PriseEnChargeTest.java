package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PriseEnChargeTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000000c"));
    private static final LocalDate D = LocalDate.of(2026, 3, 1);
    private static final LocalDate F = LocalDate.of(2026, 9, 1);
    private static final UUID FORFAIT = new UUID(5, 5);

    private PriseEnCharge creee() {
        return PriseEnCharge.demander(new UUID(1, 1), new PatientId(new UUID(2, 2)), CENTRE, D, F, FORFAIT, Instant.parse("2026-11-02T08:00:00Z"));
    }

    private void refus(String message, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(RegleDePatientViolee.class).hasMessage(message)
                .extracting("code").isEqualTo("BUSINESS_RULE_VIOLATION");
    }

    @Test
    void should_creer_une_demande_au_statut_cree_sans_accord() {
        PriseEnCharge p = creee();

        assertThat(p.statut()).isEqualTo(StatutPec.CREE);
        assertThat(p.accordDebut()).isNull();
        assertThat(p.demandeForfait()).isEqualTo(FORFAIT);
    }

    @Test
    void should_valider_puis_cloturer_sans_retour() {
        PriseEnCharge p = creee();

        p.valider(D, F, FORFAIT);
        assertThat(p.statut()).isEqualTo(StatutPec.VALIDEE);
        assertThat(p.accordFin()).isEqualTo(F);
        assertThat(p.autoriseSeance()).isTrue();
        p.cloturer();

        assertThat(p.statut()).isEqualTo(StatutPec.CLOTUREE);
        assertThat(p.autoriseSeance()).isFalse();
    }

    @Test
    void should_refuser_une_seance_a_un_non_vacancier_sans_attestation_valide_meme_si_la_pec_est_validee() {
        PriseEnCharge p = creee();
        p.valider(D, F, FORFAIT);

        assertThat(p.autoriseSeance(false, false)).isFalse();
        assertThat(p.autoriseSeance(false, true)).isTrue();
        assertThat(p.autoriseSeance(true, false)).isTrue();
        assertThat(creee().autoriseSeance(true, true)).isFalse();
    }

    @Test
    void should_refuser_une_affectation_dont_la_fin_precede_le_debut() {
        java.time.LocalDate jour = java.time.LocalDate.of(2026, 5, 10);
        org.junit.jupiter.api.Assertions.assertThrows(SaisieInvalide.class,
                () -> AssurePrincipal.verifierLesDatesDeLAffectation(jour, jour.minusDays(1)));
        AssurePrincipal.verifierLesDatesDeLAffectation(jour, jour);
        AssurePrincipal.verifierLesDatesDeLAffectation(null, jour);
        AssurePrincipal.verifierLesDatesDeLAffectation(jour, null);
    }

    @ParameterizedTest
    @EnumSource(value = StatutPec.class, names = {"VALIDEE", "CLOTUREE"})
    void should_refuser_de_valider_une_pec_deja_validee_ou_cloturee(StatutPec statut) {
        PriseEnCharge p = creee();
        p.valider(D, F, FORFAIT);
        if (statut == StatutPec.CLOTUREE) p.cloturer();

        refus("Transition invalide vers VALIDEE", () -> p.valider(D, F, FORFAIT));
    }

    @ParameterizedTest
    @EnumSource(value = StatutPec.class, names = {"CREE", "CLOTUREE"})
    void should_refuser_de_cloturer_une_pec_creee_ou_deja_cloturee(StatutPec statut) {
        PriseEnCharge p = creee();
        if (statut == StatutPec.CLOTUREE) {
            p.valider(D, F, FORFAIT);
            p.cloturer();
        }

        refus("Transition invalide vers CLOTUREE", p::cloturer);
    }

    @Test
    void should_exiger_une_attestation_valide_d_un_non_vacancier_seulement() {
        PriseEnCharge.verifierAttestation(true, false);
        PriseEnCharge.verifierAttestation(false, true);
        refus("Attestation valide obligatoire pour PEC non-vacancier", () -> PriseEnCharge.verifierAttestation(false, false));
    }

    @Test
    void should_reviser_la_demande_sans_toucher_au_statut_ni_a_l_accord() {
        PriseEnCharge p = creee();
        p.valider(D, F, FORFAIT);

        assertThat(p.reviserDemande(D, F, FORFAIT)).as("identique : rien ne change").isFalse();
        assertThat(p.reviserDemande(D.plusDays(1), F, null)).isTrue();

        assertThat(p.statut()).isEqualTo(StatutPec.VALIDEE);
        assertThat(p.accordDebut()).isEqualTo(D);
        assertThat(p.demandeDebut()).isEqualTo(D.plusDays(1));
        assertThat(p.demandeIdentique(D.plusDays(1), F, null)).isTrue();
    }

    @Test
    void should_reproduire_l_absence_de_controle_de_periode_et_de_forfait() {
        PriseEnCharge inversee = PriseEnCharge.demander(new UUID(1, 2), new PatientId(new UUID(2, 2)), CENTRE, F, D, null,
                Instant.parse("2026-11-02T08:00:00Z"));
        PriseEnCharge sansDates = PriseEnCharge.demander(new UUID(1, 3), new PatientId(new UUID(2, 2)), CENTRE, null, null, null, null);

        inversee.valider(null, null, null);
        sansDates.valider(F, D, new UUID(9, 9));

        assertThat(inversee.statut()).isEqualTo(StatutPec.VALIDEE);
        assertThat(sansDates.statut()).isEqualTo(StatutPec.VALIDEE);
    }

    @Test
    void should_lire_un_statut_inconnu_en_400() {
        assertThat(StatutPec.depuis("CLOTUREE")).isEqualTo(StatutPec.CLOTUREE);
        assertThatThrownBy(() -> StatutPec.depuis("BIDON")).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> StatutPec.depuis(null)).isInstanceOf(SaisieInvalide.class);
    }
}

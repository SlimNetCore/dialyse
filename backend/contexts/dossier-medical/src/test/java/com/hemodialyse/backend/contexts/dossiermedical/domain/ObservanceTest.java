package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservanceTest {

    private static final LocalDate PRESCRIPTION = LocalDate.of(2026, 1, 1);

    @Test
    void les_periodes_sont_ancrees_sur_la_date_de_la_prescription_et_ont_la_longueur_de_la_frequence() {
        assertThat(PeriodeDObservance.fenetreEnJours("JOUR")).isEqualTo(1);
        assertThat(PeriodeDObservance.fenetreEnJours("SEMAINE")).isEqualTo(7);
        assertThat(PeriodeDObservance.fenetreEnJours("MOIS")).isEqualTo(30);
        assertThat(PeriodeDObservance.fenetreEnJours("ANNEE")).isEqualTo(365);
        assertThat(PeriodeDObservance.fenetreEnJours(null)).isEqualTo(7);
        var courante = PeriodeDObservance.courante(PRESCRIPTION, 7, LocalDate.of(2026, 1, 10));
        assertThat(courante).isEqualTo(new PeriodeDObservance(LocalDate.of(2026, 1, 8), LocalDate.of(2026, 1, 14)));
        assertThat(courante.joursRestants(LocalDate.of(2026, 1, 10))).isEqualTo(5);
        assertThat(courante.joursRestants(LocalDate.of(2026, 1, 20))).isZero();
        assertThat(PeriodeDObservance.precedente(PRESCRIPTION, 7, LocalDate.of(2026, 1, 10)))
                .isEqualTo(new PeriodeDObservance(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 7)));
        assertThat(PeriodeDObservance.precedente(PRESCRIPTION, 7, LocalDate.of(2026, 1, 3))).isNull();
    }

    @Test
    void le_seuil_de_rappel_est_le_tiers_de_la_periode_au_moins_un_jour() {
        assertThat(PeriodeDObservance.seuilDeRappelEnJours(1)).isEqualTo(1);
        assertThat(PeriodeDObservance.seuilDeRappelEnJours(7)).isEqualTo(2);
        assertThat(PeriodeDObservance.seuilDeRappelEnJours(30)).isEqualTo(10);
    }

    @Test
    void sans_dose_prescrite_on_compte_des_administrations_et_avec_une_dose_on_compte_la_dose_cumulee() {
        var enNombre = MesureDObservance.calculer(3, null, "UI", 1, 0);
        assertThat(enNombre.enDose()).isFalse();
        assertThat(enNombre.restantes()).isEqualTo(2);
        assertThat(enNombre.enRetard()).isTrue();
        var enDose = MesureDObservance.calculer(2, 4000, "UI", 1, 4000);
        assertThat(enDose.attendu()).isEqualTo(8000);
        assertThat(enDose.administre()).isEqualTo(4000);
        assertThat(enDose.restant()).isEqualTo(4000);
        assertThat(enDose.restantes()).isEqualTo(1);
        assertThat(MesureDObservance.calculer(2, 4000, "UI", 2, 9000).enRetard()).isFalse();
    }

    @Test
    void le_message_du_rappel_reste_mal_forme_comme_le_legacy_et_celui_du_retard_est_complet() {
        var mesure = MesureDObservance.calculer(2, 4000, "UI", 0, 0);
        assertThat(mesure.messageDeRappel(TypeDeTraitement.EPO, LocalDate.of(2026, 1, 14), 2))
                .endsWith("Prescription : administré : 0 ui (0 administration(s)).");
        assertThat(mesure.messageDeRetard(TypeDeTraitement.FER_INJECTABLE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 7), 2, "SEMAINE"))
                .contains("fer injectable", "01/01/2026", "07/01/2026", "4000 UI x 2 par semaine", "Il manque 8000 UI.");
    }

    @Test
    void une_administration_effective_exige_une_dose_et_une_non_faite_un_motif_et_perd_sa_dose() {
        var patient = new PatientId(UUID.randomUUID());
        var centre = new CentreId(UUID.randomUUID());
        assertThatThrownBy(() -> AdministrationDeTraitement.enregistrer(UUID.randomUUID(), patient, centre, null, TypeDeTraitement.EPO, null, null, null,
                PRESCRIPTION, null, null, true, null, null, null, Instant.EPOCH)).extracting("code").isEqualTo("ADMINISTRATION_DOSE_REQUISE");
        assertThatThrownBy(() -> AdministrationDeTraitement.enregistrer(UUID.randomUUID(), patient, centre, null, TypeDeTraitement.EPO, null, null, null,
                PRESCRIPTION, null, null, false, " ", null, null, Instant.EPOCH)).extracting("code").isEqualTo("ADMINISTRATION_MOTIF_REQUIS");
        var nonFaite = AdministrationDeTraitement.enregistrer(UUID.randomUUID(), patient, centre, null, TypeDeTraitement.EPO, null,
                new DoseAdministree(BigDecimal.TEN, "UI"), null, PRESCRIPTION, null, null, false, "absent", UUID.randomUUID(), BigDecimal.ONE, Instant.EPOCH);
        assertThat(nonFaite.dose()).isNull();
        assertThat(nonFaite.article()).isNull();
        assertThat(nonFaite.quantiteArticle()).isNull();
        assertThatThrownBy(() -> new DoseAdministree(BigDecimal.ZERO, "UI")).extracting("code").isEqualTo("DOSE_ADMINISTREE_INVALIDE");
        assertThatThrownBy(() -> new DoseAdministree(BigDecimal.ONE, " ")).extracting("code").isEqualTo("DOSE_ADMINISTREE_UNITE_REQUISE");
    }

    @Test
    void l_evaluation_kdigo_compare_la_valeur_a_sa_cible() {
        assertThat(EvaluationDeCible.hemoglobine(new BigDecimal("9.0")).statut()).isEqualTo("SOUS_CIBLE");
        assertThat(EvaluationDeCible.hemoglobine(new BigDecimal("10.8")).statut()).isEqualTo("DANS_CIBLE");
        assertThat(EvaluationDeCible.hemoglobine(new BigDecimal("12")).statut()).isEqualTo("AU_DESSUS_CIBLE");
        assertThat(EvaluationDeCible.ferritine(null).statut()).isEqualTo("NON_EVALUABLE");
        assertThat(EvaluationDeCible.ktV(new BigDecimal("1.5")).code()).isEqualTo("KT_V");
    }

    @Test
    void resoudre_une_alerte_deja_resolue_reecrit_la_date_de_resolution() {
        var alerte = AlerteDObservance.declencher(UUID.randomUUID(), new PatientId(UUID.randomUUID()), new CentreId(UUID.randomUUID()),
                TypeDeTraitement.EPO, TypeDAlerte.RETARD_CONSTATE, PRESCRIPTION, PRESCRIPTION.plusDays(6), 8000, 0, "message", null, Instant.EPOCH);
        assertThat(alerte.resolueLe()).isNull();
        alerte.resoudre(Instant.parse("2026-01-10T00:00:00Z"));
        alerte.resoudre(Instant.parse("2026-01-11T00:00:00Z"));
        assertThat(alerte.resolueLe()).isEqualTo(Instant.parse("2026-01-11T00:00:00Z"));
        assertThat(alerte.detail()).isEqualTo(DetailDObservance.AUCUN);
    }
}

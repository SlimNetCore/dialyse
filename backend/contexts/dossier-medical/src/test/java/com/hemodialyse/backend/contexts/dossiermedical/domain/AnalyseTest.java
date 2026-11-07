package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Invariants de l'analyse (bilan à colonnes fixes ; domaine pur, sans mock). */
class AnalyseTest {
    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000c001"));
    private static final PatientId PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final LocalDate JOUR = LocalDate.of(2026, 1, 10);
    private static final Instant T0 = Instant.parse("2026-01-10T10:00:00Z");

    private static MesuresDuBilan hemoglobine(String valeur) {
        return new MesuresDuBilan(new BigDecimal(valeur), null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void une_analyse_sans_date_de_prelevement_prend_le_jour_de_l_horloge() {
        var a = Analyse.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, null, hemoglobine("11"), JOUR, T0);
        assertThat(a.datePrelevement()).isEqualTo(JOUR);
        assertThat(a.creeLe()).isEqualTo(T0);
        assertThat(a.modifieLe()).isEqualTo(T0);
    }

    @Test
    void aucune_valeur_n_est_plausibilisee_negative_nulle_demesuree_ou_date_dans_le_futur() {
        for (String valeur : new String[]{"-5", "0", "999", "1" + "0".repeat(30)}) {
            assertThat(Analyse.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, LocalDate.of(2200, 1, 1), hemoglobine(valeur), JOUR, T0)).isNotNull();
        }
        assertThat(Analyse.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, JOUR, new MesuresDuBilan(null, new BigDecimal("250"), -1, null, null, null,
                null, null, null, null, null, null, null, null, null, null), JOUR, T0)).isNotNull();
    }

    @Test
    void une_mesure_qui_ne_tient_pas_dans_sa_colonne_est_refusee() {
        assertThatThrownBy(() -> Analyse.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, JOUR, hemoglobine("1" + "0".repeat(37)), JOUR, T0))
                .isInstanceOf(SaisieInvalide.class);
        assertThat(Analyse.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, JOUR, hemoglobine("9".repeat(36)), JOUR, T0)).isNotNull();
        assertThatThrownBy(() -> Analyse.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, JOUR, hemoglobine("9".repeat(36) + ".995"), JOUR, T0))
                .isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void le_remplacement_efface_les_mesures_absentes_reprend_le_jour_et_reinitialise_la_creation_sans_changer_patient_ni_centre() {
        var a = Analyse.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, JOUR.minusDays(5), new MesuresDuBilan(new BigDecimal("11"), new BigDecimal("34"),
                220, null, null, null, null, null, null, null, null, null, null, null, null, null), JOUR, T0);
        a.remplacer(null, hemoglobine("9"), JOUR, T0.plusSeconds(60));
        assertThat(a.datePrelevement()).isEqualTo(JOUR);
        assertThat(a.mesures().hemoglobine()).isEqualByComparingTo("9");
        assertThat(a.mesures().hematocrite()).isNull();
        assertThat(a.mesures().plaquettes()).isNull();
        assertThat(a.creeLe()).isEqualTo(T0.plusSeconds(60));
        assertThat(a.modifieLe()).isEqualTo(T0.plusSeconds(60));
        assertThat(a.patient()).isEqualTo(PATIENT);
        assertThat(a.centre()).isEqualTo(CENTRE);
    }

    @Test
    void un_remplacement_hors_colonne_est_refuse_et_laisse_le_bilan_intact() {
        var a = Analyse.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, JOUR, hemoglobine("11"), JOUR, T0);
        assertThatThrownBy(() -> a.remplacer(JOUR, hemoglobine("1" + "0".repeat(37)), JOUR, T0.plusSeconds(60))).isInstanceOf(SaisieInvalide.class);
        assertThat(a.mesures().hemoglobine()).isEqualByComparingTo("11");
        assertThat(a.modifieLe()).isEqualTo(T0);
    }
}

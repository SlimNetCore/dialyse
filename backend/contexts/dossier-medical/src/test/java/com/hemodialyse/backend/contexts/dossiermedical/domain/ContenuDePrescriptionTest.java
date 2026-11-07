package com.hemodialyse.backend.contexts.dossiermedical.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContenuDePrescriptionTest {

    private static ContenuDePrescription avecPoids(String poids) {
        return new ContenuDePrescription(LocalDate.of(2026, 1, 1), null, 300, null, null, null, poids == null ? null : new BigDecimal(poids),
                null, null, null, null);
    }

    @Test
    void le_poids_sec_est_borne_de_20_a_300_kg_et_arrondi_au_centieme() {
        assertThat(avecPoids("20").poidsSecCibleKg()).isEqualByComparingTo("20.00");
        assertThat(avecPoids("72.455").poidsSecCibleKg()).isEqualByComparingTo("72.46");
        assertThat(avecPoids("72.454").poidsSecCibleKg()).isEqualByComparingTo("72.45");
        assertThat(avecPoids("299.999").poidsSecCibleKg()).isEqualByComparingTo("300.00");
        assertThat(avecPoids(null).poidsSecCibleKg()).isNull();
        for (String hors : new String[]{"19.99", "0", "-5", "300.01"}) {
            assertThatThrownBy(() -> avecPoids(hors)).as(hors).isInstanceOf(ValeurHorsBorne.class);
        }
    }

    @Test
    void les_autres_valeurs_cliniques_acceptent_zero_et_les_negatifs_comme_le_legacy() {
        var c = new ContenuDePrescription(LocalDate.of(2099, 1, 1), null, -1, -5, -100, -60, null, null, null,
                new Traitement(UUID.randomUUID(), -4000, "SC", 1, UniteDeFrequence.SEMAINE), null);
        assertThat(c.qbCible()).isEqualTo(-1);
        assertThat(c.epo().dose()).isEqualTo(-4000);
        assertThat(c.fer()).isEqualTo(Traitement.AUCUN);
    }

    @Test
    void un_texte_plus_long_que_sa_colonne_est_refuse_en_saisie_invalide() {
        assertThatThrownBy(() -> new ContenuDePrescription(null, null, null, null, null, null, null, "X".repeat(101), null, null, null))
                .isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new ContenuDePrescription(null, null, null, null, null, null, null, null, "X".repeat(21), null, null))
                .isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new Traitement(null, null, "X".repeat(11), null, null)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void remplacer_une_prescription_reecrit_le_contenu_et_la_date_de_creation_mais_pas_le_patient_ni_le_centre() {
        var patient = new PatientId(UUID.randomUUID());
        var centre = new com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId(UUID.randomUUID());
        var avant = Instant.parse("2026-01-01T10:00:00Z");
        var apres = Instant.parse("2026-02-01T10:00:00Z");
        var p = PrescriptionMedicale.prescrire(UUID.randomUUID(), patient, centre, avecPoids("70"), avant);
        p.remplacerPar(avecPoids("71"), apres);
        assertThat(p.contenu().poidsSecCibleKg()).isEqualByComparingTo("71.00");
        assertThat(p.creeeLe()).isEqualTo(apres);
        assertThat(p.modifieeLe()).isEqualTo(apres);
        assertThat(p.patient()).isEqualTo(patient);
        assertThat(p.centre()).isEqualTo(centre);
    }

    @Test
    void une_periode_inversee_est_refusee() {
        assertThatThrownBy(() -> new Periode(LocalDate.of(2026, 1, 8), LocalDate.of(2026, 1, 4))).isInstanceOf(SaisieInvalide.class);
        assertThat(new Periode(LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 6)).du()).isEqualTo(LocalDate.of(2026, 1, 6));
    }
}

package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DossierMedicalDeBaseTest {
    private static final PatientId PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000d8001"));
    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d8a1"));
    private static final Instant T0 = Instant.parse("2026-11-07T10:00:00Z");

    private DossierMedicalDeBase ouvert() {
        return DossierMedicalDeBase.ouvrir(UUID.randomUUID(), PATIENT, CENTRE, "Nephropathie fictive", LocalDate.of(2026, 1, 11), "NEGATIF",
                "POSITIF", "Observation", "Conclusion", T0);
    }

    @Test
    void should_ouvrir_le_dossier_avec_la_creation_et_la_modification_a_l_instant_donne() {
        var dossier = ouvert();
        assertThat(dossier.creeLe()).isEqualTo(T0);
        assertThat(dossier.modifieLe()).isEqualTo(T0);
        assertThat(dossier.nephropathieInitiale()).isEqualTo("Nephropathie fictive");
        assertThat(dossier.hepatiteCStatut()).isEqualTo("POSITIF");
    }

    @Test
    void should_remplacer_tout_le_contenu_et_conserver_la_creation() {
        var dossier = ouvert();
        dossier.remplacer("Seul champ", null, null, null, null, null, T0.plusSeconds(60));
        assertThat(dossier.nephropathieInitiale()).isEqualTo("Seul champ");
        assertThat(dossier.dateMiseEnDialyse()).isNull();
        assertThat(dossier.hepatiteBStatut()).isNull();
        assertThat(dossier.observationGlobale()).isNull();
        assertThat(dossier.conclusionMedicale()).isNull();
        assertThat(dossier.creeLe()).isEqualTo(T0);
        assertThat(dossier.modifieLe()).isEqualTo(T0.plusSeconds(60));
    }

    @Test
    void should_accepter_une_valeur_de_la_longueur_de_sa_colonne_et_un_statut_d_hepatite_libre() {
        var dossier = DossierMedicalDeBase.ouvrir(UUID.randomUUID(), PATIENT, CENTRE, "n".repeat(255), null, "N'IMPORTE QUOI", "x".repeat(20), null, "", T0);
        assertThat(dossier.nephropathieInitiale()).hasSize(255);
        assertThat(dossier.conclusionMedicale()).isEmpty();
    }

    @Test
    void should_refuser_une_valeur_plus_longue_que_sa_colonne_sans_rien_modifier() {
        var dossier = ouvert();
        assertThatThrownBy(() -> dossier.remplacer("n".repeat(256), null, null, null, null, null, T0.plusSeconds(1))).isInstanceOf(ValeurHorsBorne.class);
        assertThatThrownBy(() -> dossier.remplacer(null, null, "x".repeat(21), null, null, null, T0.plusSeconds(1))).isInstanceOf(ValeurHorsBorne.class);
        assertThatThrownBy(() -> dossier.remplacer(null, null, null, "x".repeat(21), null, null, T0.plusSeconds(1))).isInstanceOf(ValeurHorsBorne.class);
        assertThat(dossier.nephropathieInitiale()).isEqualTo("Nephropathie fictive");
        assertThat(dossier.modifieLe()).isEqualTo(T0);
    }

    @Test
    void should_reconstituer_un_dossier_sans_aucune_date() {
        var dossier = DossierMedicalDeBase.reconstituer(UUID.randomUUID(), PATIENT, CENTRE, null, null, null, null, null, null, null, null);
        assertThat(dossier.creeLe()).isNull();
        assertThat(dossier.modifieLe()).isNull();
    }
}

package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Règles de l'abord vasculaire (domaine pur, sans double). */
class AbordVasculaireTest {
    private static final PatientId PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000d5001"));
    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d5a1"));
    private static final Instant T0 = Instant.parse("2026-06-15T10:00:00Z");

    private static AbordVasculaire abord(String type, LocalDate creation, LocalDate fin, Boolean actif) {
        return AbordVasculaire.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, type, null, null, creation, fin, actif, null, T0);
    }

    @Test
    void un_abord_enregistre_est_actif_quand_l_activite_n_est_pas_precisee() {
        assertThat(abord("FAV", null, null, null).actif()).isTrue();
        assertThat(abord("FAV", null, null, false).actif()).isFalse();
        assertThat(abord("FAV", null, null, null).enregistreLe()).isEqualTo(T0);
    }

    @Test
    void le_type_est_un_texte_libre_obligatoire_mais_vide_accepte() {
        for (String libre : new String[]{"FAV", "kt_aigu", "N'IMPORTE QUOI", ""}) {
            assertThat(abord(libre, null, null, null).type()).isEqualTo(libre);
        }
        assertThatThrownBy(() -> abord(null, null, null, null)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void la_fin_ne_precede_pas_la_creation_quand_les_deux_dates_sont_connues() {
        assertThatThrownBy(() -> abord("FAV", LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 9), null)).isInstanceOfSatisfying(
                RegleDeGestionViolee.class, e -> assertThat(e.code()).isEqualTo("ABORD_DATE_FIN_ANTERIEURE"));
        assertThat(abord("FAV", LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 10), null).fin()).isEqualTo(LocalDate.of(2026, 1, 10));
        assertThat(abord("FAV", null, LocalDate.of(2026, 1, 1), null).fin()).isNotNull();
        assertThat(abord("FAV", LocalDate.of(2300, 1, 1), null, true).creation()).isEqualTo(LocalDate.of(2300, 1, 1));
    }

    @Test
    void les_bornes_de_colonne_sont_des_saisies_invalides() {
        assertThatThrownBy(() -> abord("t".repeat(21), null, null, null)).isInstanceOf(SaisieInvalide.class);
        assertThat(abord("t".repeat(20), null, null, null)).isNotNull();
        assertThatThrownBy(() -> AbordVasculaire.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, "FAV", "c".repeat(11), null, null, null, null, null,
                T0)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> AbordVasculaire.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, "FAV", null, "l".repeat(256), null, null, null, null,
                T0)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> AbordVasculaire.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, "FAV", null, null, null, null, null, "k".repeat(501),
                T0)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void le_remplacement_efface_les_champs_absents_reactive_l_abord_et_reecrit_la_date_d_enregistrement() {
        var a = AbordVasculaire.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, "FAV", "GAUCHE", "Avant-bras", LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 2, 1), false, "Complication", T0);
        a.remplacer("PTFE", null, null, LocalDate.of(2026, 3, 1), null, null, null, T0.plusSeconds(30));
        assertThat(a.type()).isEqualTo("PTFE");
        assertThat(a.cote()).isNull();
        assertThat(a.localisation()).isNull();
        assertThat(a.fin()).isNull();
        assertThat(a.complications()).isNull();
        assertThat(a.actif()).isTrue();
        assertThat(a.enregistreLe()).isEqualTo(T0.plusSeconds(30));
        assertThat(a.patient()).isEqualTo(PATIENT);
        assertThat(a.centre()).isEqualTo(CENTRE);
    }

    @Test
    void un_remplacement_refuse_ne_change_rien() {
        var a = abord("FAV", LocalDate.of(2026, 1, 1), null, true);
        assertThatThrownBy(() -> a.remplacer(null, null, null, null, null, null, null, T0.plusSeconds(1))).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> a.remplacer("PTFE", null, null, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 2, 1), null, null, T0.plusSeconds(1)))
                .isInstanceOf(RegleDeGestionViolee.class);
        assertThat(a.type()).isEqualTo("FAV");
        assertThat(a.enregistreLe()).isEqualTo(T0);
    }
}

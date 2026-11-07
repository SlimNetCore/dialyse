package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Règles de l'allergie (domaine pur, sans double). */
class AllergieTest {
    private static final PatientId PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000d5001"));
    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d5a1"));
    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 6, 15);
    private static final Instant T0 = Instant.parse("2026-06-15T10:00:00Z");
    private static final CodeClinique LATEX = CodeClinique.saisir(SystemeDeCodage.LOCAL, "latex", null);

    private static Allergie declarer(CriticiteDAllergie criticite, String manifestations, LocalDate date, StatutDeVerification statut) {
        return Allergie.declarer(UUID.randomUUID(), PATIENT, CENTRE, LATEX, CategorieDAllergie.ENVIRONNEMENT, criticite, TypeDeReaction.ALLERGIE,
                manifestations, date, statut, AUJOURDHUI, T0);
    }

    @Test
    void sans_date_ni_statut_une_allergie_est_constatee_aujourd_hui_et_suspectee() {
        var a = declarer(CriticiteDAllergie.BASSE, null, null, null);
        assertThat(a.dateDeConstatation()).isEqualTo(AUJOURDHUI);
        assertThat(a.statut()).isEqualTo(StatutDeVerification.SUSPECTEE);
        assertThat(a.creeLe()).isEqualTo(T0).isEqualTo(a.modifieLe());
    }

    @Test
    void une_criticite_haute_exige_des_manifestations_non_blanches() {
        for (String manifestations : new String[]{null, "", "   "}) {
            assertThatThrownBy(() -> declarer(CriticiteDAllergie.HAUTE, manifestations, null, null)).isInstanceOfSatisfying(
                    RegleDeGestionViolee.class, e -> assertThat(e.code()).isEqualTo("ALLERGIE_MANIFESTATIONS_REQUISES"));
        }
        assertThat(declarer(CriticiteDAllergie.HAUTE, "Choc", null, null).estCritique()).isTrue();
        assertThat(declarer(CriticiteDAllergie.BASSE, null, null, null).estCritique()).isFalse();
    }

    @Test
    void la_date_de_constatation_n_est_jamais_dans_le_futur() {
        assertThat(declarer(CriticiteDAllergie.BASSE, null, AUJOURDHUI, null).dateDeConstatation()).isEqualTo(AUJOURDHUI);
        assertThatThrownBy(() -> declarer(CriticiteDAllergie.BASSE, null, AUJOURDHUI.plusDays(1), null)).isInstanceOfSatisfying(
                RegleDeGestionViolee.class, e -> assertThat(e.code()).isEqualTo("ALLERGIE_DATE_FUTURE"));
    }

    @Test
    void une_allergie_refutee_reste_critique_si_sa_criticite_est_haute() {
        assertThat(declarer(CriticiteDAllergie.HAUTE, "Choc", null, StatutDeVerification.REFUTEE).estCritique()).isTrue();
    }

    @Test
    void les_bornes_de_colonne_de_la_substance_sont_des_saisies_invalides() {
        assertThatThrownBy(() -> Allergie.declarer(UUID.randomUUID(), PATIENT, CENTRE,
                CodeClinique.saisir(SystemeDeCodage.LOCAL, "c".repeat(21), null), CategorieDAllergie.ALIMENT, CriticiteDAllergie.BASSE,
                TypeDeReaction.ALLERGIE, null, null, null, AUJOURDHUI, T0)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> Allergie.declarer(UUID.randomUUID(), PATIENT, CENTRE,
                CodeClinique.saisir(SystemeDeCodage.LOCAL, "c", "a".repeat(256)), CategorieDAllergie.ALIMENT, CriticiteDAllergie.BASSE,
                TypeDeReaction.ALLERGIE, null, null, null, AUJOURDHUI, T0)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void la_correction_remplace_les_manifestations_et_conserve_le_statut_absent() {
        var a = declarer(CriticiteDAllergie.HAUTE, "Choc", null, StatutDeVerification.CONFIRMEE);
        a.corriger(CriticiteDAllergie.HAUTE, "Autre choc", null, T0.plusSeconds(5));
        assertThat(a.manifestations()).isEqualTo("Autre choc");
        assertThat(a.statut()).isEqualTo(StatutDeVerification.CONFIRMEE);
        a.corriger(CriticiteDAllergie.BASSE, null, StatutDeVerification.REFUTEE, T0.plusSeconds(9));
        assertThat(a.manifestations()).isNull();
        assertThat(a.criticite()).isEqualTo(CriticiteDAllergie.BASSE);
        assertThat(a.statut()).isEqualTo(StatutDeVerification.REFUTEE);
        assertThat(a.modifieLe()).isEqualTo(T0.plusSeconds(9));
        assertThat(a.creeLe()).isEqualTo(T0);
    }

    @Test
    void la_correction_vers_haute_sans_manifestations_est_refusee_et_ne_change_rien() {
        var a = declarer(CriticiteDAllergie.BASSE, null, null, null);
        assertThatThrownBy(() -> a.corriger(CriticiteDAllergie.HAUTE, " ", null, T0)).isInstanceOfSatisfying(RegleDeGestionViolee.class,
                e -> assertThat(e.code()).isEqualTo("ALLERGIE_MANIFESTATIONS_REQUISES"));
        assertThat(a.criticite()).isEqualTo(CriticiteDAllergie.BASSE);
    }

    @Test
    void la_substance_la_categorie_le_type_et_la_date_ne_changent_jamais() {
        var a = declarer(CriticiteDAllergie.BASSE, null, LocalDate.of(2026, 1, 2), null);
        a.corriger(CriticiteDAllergie.BASSE, "x", StatutDeVerification.CONFIRMEE, T0.plusSeconds(1));
        assertThat(a.substance().code()).isEqualTo("LATEX");
        assertThat(a.categorie()).isEqualTo(CategorieDAllergie.ENVIRONNEMENT);
        assertThat(a.typeDeReaction()).isEqualTo(TypeDeReaction.ALLERGIE);
        assertThat(a.dateDeConstatation()).isEqualTo(LocalDate.of(2026, 1, 2));
    }

    @Test
    void les_listes_fermees_refusent_l_absent_l_inconnu_et_les_minuscules_sans_nom_de_classe_interne() {
        assertThatThrownBy(() -> CategorieDAllergie.lire("medicament")).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> CategorieDAllergie.lire(null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> CriticiteDAllergie.lire("MOYENNE")).isInstanceOfSatisfying(SaisieInvalide.class,
                e -> assertThat(e.getMessage()).doesNotContain("com.hemodialyse").doesNotContain("No enum constant"));
        assertThatThrownBy(() -> TypeDeReaction.lire("ANAPHYLAXIE")).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> StatutDeVerification.lireFacultatif("PROBABLE")).isInstanceOf(SaisieInvalide.class);
        assertThat(StatutDeVerification.lireFacultatif(null)).isNull();
    }
}

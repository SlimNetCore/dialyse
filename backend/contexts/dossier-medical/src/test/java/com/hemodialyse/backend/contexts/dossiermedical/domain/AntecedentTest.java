package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Règles de l'antécédent (domaine pur, sans double). Matrice invariant, test : voir ADR 0084. */
class AntecedentTest {
    private static final PatientId PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000d5001"));
    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d5a1"));
    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 6, 15);
    private static final Instant T0 = Instant.parse("2026-06-15T10:00:00Z");

    private static Antecedent libre(LocalDate debut, LocalDate fin) {
        return Antecedent.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, TypeDAntecedent.MEDICAL, null, "  Libellé  ", debut, fin, null, null,
                AUJOURDHUI, T0);
    }

    @Test
    void un_antecedent_enregistre_demarre_actif_avec_un_libelle_sans_espaces_en_bordure() {
        var a = libre(LocalDate.of(2026, 1, 1), null);
        assertThat(a.statut()).isEqualTo(StatutClinique.ACTIF);
        assertThat(a.libelleLibre()).isEqualTo("Libellé");
        assertThat(a.creeLe()).isEqualTo(T0).isEqualTo(a.modifieLe());
    }

    @Test
    void un_diagnostic_code_ou_un_libelle_est_obligatoire() {
        assertThatThrownBy(() -> Antecedent.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, TypeDAntecedent.MEDICAL, null, "   ",
                LocalDate.of(2026, 1, 1), null, null, null, AUJOURDHUI, T0))
                .isInstanceOfSatisfying(RegleDeGestionViolee.class, e -> assertThat(e.code()).isEqualTo("ANTECEDENT_DIAGNOSTIC_REQUIS"));
        var codee = Antecedent.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, TypeDAntecedent.MEDICAL,
                CodeClinique.saisir(SystemeDeCodage.CIM10, "n18", null), null, LocalDate.of(2026, 1, 1), null, null, null, AUJOURDHUI, T0);
        assertThat(codee.diagnostic().code()).isEqualTo("N18");
    }

    @Test
    void la_periode_exige_un_debut_et_une_fin_qui_ne_precede_pas_le_debut() {
        assertThatThrownBy(() -> libre(null, null)).isInstanceOfSatisfying(RegleDeGestionViolee.class,
                e -> assertThat(e.code()).isEqualTo("PERIODE_DEBUT_REQUIS"));
        assertThatThrownBy(() -> libre(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 9))).isInstanceOfSatisfying(RegleDeGestionViolee.class,
                e -> assertThat(e.code()).isEqualTo("PERIODE_FIN_ANTERIEURE"));
        assertThat(libre(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 10)).fin()).isEqualTo(LocalDate.of(2026, 1, 10));
    }

    @Test
    void un_debut_dans_le_futur_est_refuse_a_l_enregistrement_mais_pas_aujourd_hui() {
        assertThat(libre(AUJOURDHUI, null).debut()).isEqualTo(AUJOURDHUI);
        assertThatThrownBy(() -> libre(AUJOURDHUI.plusDays(1), null)).isInstanceOfSatisfying(RegleDeGestionViolee.class,
                e -> assertThat(e.code()).isEqualTo("ANTECEDENT_DATE_FUTURE"));
    }

    @Test
    void les_bornes_de_colonne_sont_des_saisies_invalides() {
        assertThatThrownBy(() -> Antecedent.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, TypeDAntecedent.MEDICAL, null, "l".repeat(256),
                LocalDate.of(2026, 1, 1), null, null, null, AUJOURDHUI, T0)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> Antecedent.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, TypeDAntecedent.MEDICAL, null, "ok",
                LocalDate.of(2026, 1, 1), null, "s".repeat(51), null, AUJOURDHUI, T0)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> Antecedent.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, TypeDAntecedent.MEDICAL,
                CodeClinique.saisir(SystemeDeCodage.LOCAL, "c".repeat(21), null), null, LocalDate.of(2026, 1, 1), null, null, null,
                AUJOURDHUI, T0)).isInstanceOf(SaisieInvalide.class);
        assertThat(Antecedent.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, TypeDAntecedent.MEDICAL, null, "l".repeat(255),
                LocalDate.of(2026, 1, 1), null, "s".repeat(50), null, AUJOURDHUI, T0)).isNotNull();
    }

    @Test
    void la_resolution_fixe_la_fin_et_ne_se_rejoue_pas() {
        var a = libre(LocalDate.of(2026, 1, 1), null);
        a.resoudre(LocalDate.of(2026, 2, 1), T0.plusSeconds(5));
        assertThat(a.statut()).isEqualTo(StatutClinique.RESOLU);
        assertThat(a.fin()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(a.modifieLe()).isEqualTo(T0.plusSeconds(5));
        assertThatThrownBy(() -> a.resoudre(LocalDate.of(2026, 3, 1), T0)).isInstanceOfSatisfying(RegleDeGestionViolee.class,
                e -> assertThat(e.code()).isEqualTo("ANTECEDENT_DEJA_RESOLU"));
    }

    @Test
    void la_resolution_accepte_une_date_absente_ou_future_mais_pas_anterieure_au_debut() {
        var sansDate = libre(LocalDate.of(2026, 1, 1), null);
        sansDate.resoudre(null, T0);
        assertThat(sansDate.fin()).isNull();
        var future = libre(LocalDate.of(2026, 1, 1), null);
        future.resoudre(AUJOURDHUI.plusDays(30), T0);
        assertThat(future.statut()).isEqualTo(StatutClinique.RESOLU);
        var avant = libre(LocalDate.of(2026, 1, 10), null);
        assertThatThrownBy(() -> avant.resoudre(LocalDate.of(2026, 1, 9), T0)).isInstanceOfSatisfying(RegleDeGestionViolee.class,
                e -> assertThat(e.code()).isEqualTo("PERIODE_FIN_ANTERIEURE"));
        assertThat(avant.statut()).isEqualTo(StatutClinique.ACTIF);
    }

    @Test
    void la_correction_remplace_tout_sauf_le_type_et_conserve_le_statut_absent() {
        var a = Antecedent.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, TypeDAntecedent.FAMILIAL, null, "Avant", LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 5), "FORTE", "note", AUJOURDHUI, T0);
        a.corriger(null, "Après", LocalDate.of(2026, 3, 1), null, null, null, null, T0.plusSeconds(9));
        assertThat(a.type()).isEqualTo(TypeDAntecedent.FAMILIAL);
        assertThat(a.libelleLibre()).isEqualTo("Après");
        assertThat(a.debut()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(a.fin()).isNull();
        assertThat(a.severite()).isNull();
        assertThat(a.note()).isNull();
        assertThat(a.statut()).isEqualTo(StatutClinique.ACTIF);
        a.corriger(null, "Après", LocalDate.of(2026, 3, 1), null, StatutClinique.INACTIF, null, null, T0);
        a.corriger(null, "Après", LocalDate.of(2026, 3, 1), null, null, null, null, T0);
        assertThat(a.statut()).isEqualTo(StatutClinique.INACTIF);
    }

    @Test
    void la_correction_ne_controle_pas_le_futur_et_peut_rouvrir_un_antecedent_resolu() {
        var a = libre(LocalDate.of(2026, 1, 1), null);
        a.resoudre(LocalDate.of(2026, 2, 1), T0);
        a.corriger(null, "Rouvert", AUJOURDHUI.plusDays(30), null, StatutClinique.ACTIF, null, null, T0);
        assertThat(a.statut()).isEqualTo(StatutClinique.ACTIF);
        assertThat(a.debut()).isEqualTo(AUJOURDHUI.plusDays(30));
    }

    @Test
    void le_code_clinique_est_normalise_et_controle_selon_son_systeme() {
        assertThat(CodeClinique.saisir(SystemeDeCodage.CIM10, " e11.9 ", "  Diabète ").code()).isEqualTo("E11.9");
        assertThat(CodeClinique.saisir(SystemeDeCodage.CIM10, "E11.9", "  Diabète ").affichage()).isEqualTo("Diabète");
        assertThat(CodeClinique.saisir(SystemeDeCodage.LOINC, "718-7", null).code()).isEqualTo("718-7");
        assertThat(CodeClinique.saisir(SystemeDeCodage.ATC, "j01ca04", null).code()).isEqualTo("J01CA04");
        assertThat(CodeClinique.saisir(SystemeDeCodage.LOCAL, "x", null).code()).isEqualTo("X");
        for (String invalide : new String[]{"N1", "118", "I10.ABCDE"}) {
            assertThatThrownBy(() -> CodeClinique.saisir(SystemeDeCodage.CIM10, invalide, null)).isInstanceOfSatisfying(RegleDeGestionViolee.class,
                    e -> assertThat(e.code()).isEqualTo("CONCEPT_CODE_INVALID_CIM10"));
        }
        assertThatThrownBy(() -> CodeClinique.saisir(SystemeDeCodage.LOINC, "abc", null)).isInstanceOfSatisfying(RegleDeGestionViolee.class,
                e -> assertThat(e.code()).isEqualTo("CONCEPT_CODE_INVALID_LOINC"));
    }

    @Test
    void deux_codes_sont_les_memes_s_ils_ont_le_meme_systeme_et_le_meme_code() {
        var cim = CodeClinique.saisir(SystemeDeCodage.CIM10, "N18", "a");
        assertThat(cim.memeCodeQue(CodeClinique.saisir(SystemeDeCodage.CIM10, "n18", "b"))).isTrue();
        assertThat(cim.memeCodeQue(CodeClinique.saisir(SystemeDeCodage.LOCAL, "N18", null))).isFalse();
        assertThat(cim.memeCodeQue(null)).isFalse();
    }

    @Test
    void les_listes_fermees_refusent_l_absent_l_inconnu_et_les_minuscules_sans_nom_de_classe_interne() {
        assertThat(TypeDAntecedent.lire("COMORBIDITE")).isEqualTo(TypeDAntecedent.COMORBIDITE);
        for (String saisie : new String[]{null, "", "AUTRE", "medical"}) {
            assertThatThrownBy(() -> TypeDAntecedent.lire(saisie)).isInstanceOfSatisfying(SaisieInvalide.class,
                    e -> assertThat(e.getMessage()).doesNotContain("com.hemodialyse").doesNotContain("No enum constant"));
        }
        assertThat(StatutClinique.lireFacultatif(null)).isNull();
        assertThatThrownBy(() -> StatutClinique.lireFacultatif("GUERI")).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> SystemeDeCodage.lire(null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> SystemeDeCodage.lire("cim10")).isInstanceOf(SaisieInvalide.class);
    }
}

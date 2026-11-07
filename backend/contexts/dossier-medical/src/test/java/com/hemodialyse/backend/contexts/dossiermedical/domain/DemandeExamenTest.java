package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemandeExamenTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-01-02T10:00:00Z");
    private static final LocalDate JOUR = LocalDate.of(2026, 1, 1);

    private static LigneDeDemandeDExamen ligne() {
        return LigneDeDemandeDExamen.nouvelle(UUID.randomUUID(), null, "Hemoglobine", null);
    }

    private static DemandeExamen demande() {
        return DemandeExamen.demander(UUID.randomUUID(), new PatientId(UUID.randomUUID()), new CentreId(UUID.randomUUID()), "dr", JOUR, JOUR,
                CategorieDExamen.BIOLOGIE, false, "motif", List.of(ligne()), T0);
    }

    private static DemandeExamen au(StatutDeDemandeDExamen statut) {
        return DemandeExamen.reconstituer(UUID.randomUUID(), new PatientId(UUID.randomUUID()), new CentreId(UUID.randomUUID()), "dr", JOUR,
                CategorieDExamen.BIOLOGIE, false, null, statut, null, List.of(ligne()), T0, T0);
    }

    @Test
    void une_demande_nait_a_l_etat_demande_sans_conclusion() {
        var d = demande();
        assertThat(d.statut()).isEqualTo(StatutDeDemandeDExamen.DEMANDE);
        assertThat(d.conclusion()).isNull();
        assertThat(d.creeeLe()).isEqualTo(d.modifieeLe());
    }

    @Test
    void une_demande_sans_ligne_est_refusee() {
        assertThatThrownBy(() -> DemandeExamen.demander(UUID.randomUUID(), new PatientId(UUID.randomUUID()), new CentreId(UUID.randomUUID()), null,
                JOUR, JOUR, CategorieDExamen.IMAGERIE, false, null, List.of(), T0))
                .isInstanceOf(RegleDeGestionViolee.class).extracting("code").isEqualTo("DEMANDE_EXAMEN_LIGNE_REQUISE");
    }

    @Test
    void une_date_dans_le_futur_est_refusee_mais_aujourd_hui_et_une_date_tres_ancienne_sont_acceptees() {
        var patient = new PatientId(UUID.randomUUID());
        var centre = new CentreId(UUID.randomUUID());
        assertThatThrownBy(() -> DemandeExamen.demander(UUID.randomUUID(), patient, centre, null, JOUR.plusDays(1), JOUR,
                CategorieDExamen.BIOLOGIE, false, null, List.of(ligne()), T0))
                .extracting("code").isEqualTo("DEMANDE_EXAMEN_DATE_FUTURE");
        assertThat(DemandeExamen.demander(UUID.randomUUID(), patient, centre, null, JOUR, JOUR, CategorieDExamen.BIOLOGIE, false, null,
                List.of(ligne()), T0).dateDemande()).isEqualTo(JOUR);
        assertThat(DemandeExamen.demander(UUID.randomUUID(), patient, centre, null, LocalDate.of(1900, 1, 1), JOUR, CategorieDExamen.BIOLOGIE,
                false, null, List.of(ligne()), T0).dateDemande()).isEqualTo(LocalDate.of(1900, 1, 1));
    }

    @Test
    void les_tailles_maximales_sont_des_saisies_invalides() {
        var patient = new PatientId(UUID.randomUUID());
        var centre = new CentreId(UUID.randomUUID());
        assertThatThrownBy(() -> DemandeExamen.demander(UUID.randomUUID(), patient, centre, "p".repeat(101), JOUR, JOUR,
                CategorieDExamen.BIOLOGIE, false, null, List.of(ligne()), T0)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> DemandeExamen.demander(UUID.randomUUID(), patient, centre, null, JOUR, JOUR,
                CategorieDExamen.BIOLOGIE, false, "m".repeat(501), List.of(ligne()), T0)).isInstanceOf(SaisieInvalide.class);
        assertThat(DemandeExamen.demander(UUID.randomUUID(), patient, centre, "p".repeat(100), JOUR, JOUR, CategorieDExamen.BIOLOGIE, false,
                "m".repeat(500), List.of(ligne()), T0)).isNotNull();
    }

    @Test
    void une_ligne_exige_un_code_ou_un_libelle_non_blanc() {
        var id = UUID.randomUUID();
        assertThatThrownBy(() -> LigneDeDemandeDExamen.nouvelle(id, null, null, "x")).extracting("code").isEqualTo("LIGNE_DEMANDE_EXAMEN_REQUISE");
        assertThatThrownBy(() -> LigneDeDemandeDExamen.nouvelle(id, null, "  ", null)).extracting("code").isEqualTo("LIGNE_DEMANDE_EXAMEN_REQUISE");
        assertThat(LigneDeDemandeDExamen.nouvelle(id, new CodeDExamen("LOINC", "718-7", null), null, null).code().code()).isEqualTo("718-7");
        assertThatThrownBy(() -> LigneDeDemandeDExamen.nouvelle(id, null, "l".repeat(256), null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> LigneDeDemandeDExamen.nouvelle(id, null, "x", "c".repeat(501))).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void un_code_est_normalise_en_majuscules_aucune_nomenclature_n_est_limitee_a_loinc_et_les_formats_sont_controles() {
        assertThat(new CodeDExamen("CIM10", " n18.5 ", "  Maladie  ").code()).isEqualTo("N18.5");
        assertThat(new CodeDExamen("CIM10", "n18.5", "  Maladie  ").libelle()).isEqualTo("Maladie");
        assertThat(new CodeDExamen("ATC", "b03xa02", null).code()).isEqualTo("B03XA02");
        assertThat(new CodeDExamen("LOCAL", " cr-1 ", null).code()).isEqualTo("CR-1");
        assertThatThrownBy(() -> new CodeDExamen("LOINC", "XYZ", null)).extracting("code").isEqualTo("CONCEPT_CODE_INVALID_LOINC");
        assertThatThrownBy(() -> new CodeDExamen("LOINC", "7187", null)).extracting("code").isEqualTo("CONCEPT_CODE_INVALID_LOINC");
        assertThatThrownBy(() -> new CodeDExamen("CIM10", "718-7", null)).extracting("code").isEqualTo("CONCEPT_CODE_INVALID_CIM10");
        assertThatThrownBy(() -> new CodeDExamen("loinc", "718-7", null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new CodeDExamen(null, "718-7", null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new CodeDExamen("LOCAL", "k".repeat(21), null)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void la_categorie_est_sensible_a_la_casse_et_refuse_l_absente() {
        assertThat(CategorieDExamen.lire("ANAPATH")).isEqualTo(CategorieDExamen.ANAPATH);
        assertThatThrownBy(() -> CategorieDExamen.lire("biologie")).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> CategorieDExamen.lire("")).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> CategorieDExamen.lire(null)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void le_parcours_nominal_conserve_les_lignes_et_pose_la_conclusion_a_la_validation() {
        var d = demande();
        var lignes = d.lignes();
        d.enregistrerLePrelevement(T1);
        assertThat(d.statut()).isEqualTo(StatutDeDemandeDExamen.PRELEVE);
        d.rendreLeResultatDisponible(T1);
        d.valider("Normal", T1);
        assertThat(d.statut()).isEqualTo(StatutDeDemandeDExamen.VALIDE);
        assertThat(d.conclusion()).isEqualTo("Normal");
        assertThat(d.lignes()).isEqualTo(lignes);
        assertThat(d.modifieeLe()).isEqualTo(T1);
        assertThat(d.creeeLe()).isEqualTo(T0);
    }

    @Test
    void toutes_les_transitions_de_la_machine_a_etats_sont_decidees_depuis_chaque_statut() {
        // Légales : DEMANDE vers PRELEVE / ANNULE, PRELEVE vers RESULTAT_DISPONIBLE / ANNULE, RESULTAT_DISPONIBLE vers VALIDE. Toutes les autres : 422.
        for (StatutDeDemandeDExamen depart : StatutDeDemandeDExamen.values()) {
            verifier(depart, "preleve", depart == StatutDeDemandeDExamen.DEMANDE, StatutDeDemandeDExamen.PRELEVE);
            verifier(depart, "resultat", depart == StatutDeDemandeDExamen.PRELEVE, StatutDeDemandeDExamen.RESULTAT_DISPONIBLE);
            verifier(depart, "valider", depart == StatutDeDemandeDExamen.RESULTAT_DISPONIBLE, StatutDeDemandeDExamen.VALIDE);
            verifier(depart, "annuler", depart == StatutDeDemandeDExamen.DEMANDE || depart == StatutDeDemandeDExamen.PRELEVE,
                    StatutDeDemandeDExamen.ANNULE);
        }
    }

    private static void verifier(StatutDeDemandeDExamen depart, String action, boolean legale, StatutDeDemandeDExamen attendu) {
        var d = au(depart);
        Runnable appel = switch (action) {
            case "preleve" -> () -> d.enregistrerLePrelevement(T1);
            case "resultat" -> () -> d.rendreLeResultatDisponible(T1);
            case "valider" -> () -> d.valider("c", T1);
            default -> () -> d.annuler(T1);
        };
        if (legale) {
            appel.run();
            assertThat(d.statut()).as(depart + " + " + action).isEqualTo(attendu);
            assertThat(d.modifieeLe()).isEqualTo(T1);
        } else {
            assertThatThrownBy(appel::run).as(depart + " + " + action).isInstanceOf(RegleDeGestionViolee.class)
                    .extracting("code").isEqualTo("DEMANDE_EXAMEN_TRANSITION_INVALIDE");
            assertThat(d.statut()).isEqualTo(depart);
            assertThat(d.conclusion()).isNull();
            assertThat(d.modifieeLe()).isEqualTo(T0);
        }
    }

    @Test
    void une_conclusion_absente_blanche_ou_tres_longue_est_acceptee_a_la_validation() {
        for (String conclusion : new String[]{null, "", "   ", "c".repeat(20000)}) {
            var d = au(StatutDeDemandeDExamen.RESULTAT_DISPONIBLE);
            d.valider(conclusion, T1);
            assertThat(d.conclusion()).isEqualTo(conclusion);
        }
    }
}

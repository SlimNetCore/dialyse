package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrdonnanceTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-01-02T10:00:00Z");

    private static LigneDOrdonnance ligne() {
        return LigneDOrdonnance.nouvelle(UUID.randomUUID(), null, "Medicament fictif", "1 par jour", null, null, null, null);
    }

    private static Ordonnance brouillon() {
        return Ordonnance.rediger(UUID.randomUUID(), new PatientId(UUID.randomUUID()), new CentreId(UUID.randomUUID()), "dr", LocalDate.of(2026, 1, 1),
                List.of(ligne()), T0);
    }

    @Test
    void une_ordonnance_redigee_est_un_brouillon_sans_numero() {
        var o = brouillon();
        assertThat(o.statut()).isEqualTo(StatutOrdonnance.BROUILLON);
        assertThat(o.numero()).isNull();
        assertThat(o.signeeLe()).isNull();
    }

    @Test
    void une_ordonnance_sans_ligne_est_refusee() {
        assertThatThrownBy(() -> Ordonnance.rediger(UUID.randomUUID(), new PatientId(UUID.randomUUID()), new CentreId(UUID.randomUUID()), null,
                LocalDate.of(2026, 1, 1), List.of(), T0))
                .isInstanceOf(RegleDeGestionViolee.class).extracting("code").isEqualTo("ORDONNANCE_LIGNE_REQUISE");
    }

    @Test
    void une_ligne_exige_un_medicament_une_posologie_et_des_valeurs_positives() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> LigneDOrdonnance.nouvelle(id, null, " ", "1", null, null, null, null)).extracting("code").isEqualTo("LIGNE_ORDONNANCE_MEDICAMENT_REQUIS");
        assertThatThrownBy(() -> LigneDOrdonnance.nouvelle(id, null, "X", " ", null, null, null, null)).extracting("code").isEqualTo("LIGNE_ORDONNANCE_POSOLOGIE_REQUISE");
        assertThatThrownBy(() -> LigneDOrdonnance.nouvelle(id, null, "X", "1", null, 0, null, null)).extracting("code").isEqualTo("LIGNE_ORDONNANCE_DUREE_INVALIDE");
        assertThatThrownBy(() -> LigneDOrdonnance.nouvelle(id, null, "X", "1", null, null, -1, null)).extracting("code").isEqualTo("LIGNE_ORDONNANCE_QUANTITE_INVALIDE");
    }

    @Test
    void un_code_de_medicament_est_normalise_en_majuscules_et_une_nomenclature_inconnue_est_refusee() {
        assertThat(new Medicament("ATC", " b03xa02 ", "x").code()).isEqualTo("B03XA02");
        assertThat(new Medicament("ATC", "XYZ", null).code()).isEqualTo("XYZ");
        assertThatThrownBy(() -> new Medicament("INCONNU", "B03", null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new Medicament(null, "B03", null)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void la_signature_pose_le_numero_et_la_date() {
        var o = brouillon();
        o.signer("ORD-00001", T1);
        assertThat(o.statut()).isEqualTo(StatutOrdonnance.SIGNEE);
        assertThat(o.numero()).isEqualTo("ORD-00001");
        assertThat(o.signeeLe()).isEqualTo(T1);
        assertThat(o.modifieeLe()).isEqualTo(T1);
    }

    @Test
    void une_signature_refusee_ne_pose_aucun_numero_sur_l_ordonnance() {
        var o = brouillon();
        o.annuler(T1);
        assertThatThrownBy(() -> o.signer("ORD-00001", T1)).isInstanceOf(RegleDeGestionViolee.class).extracting("code")
                .isEqualTo("ORDONNANCE_TRANSITION_INVALIDE");
        assertThat(o.numero()).isNull();
    }

    @Test
    void la_matrice_des_transitions_est_celle_du_legacy() {
        record Cas(StatutOrdonnance depart, String action, boolean permis) {
        }
        List<Cas> matrice = List.of(
                new Cas(StatutOrdonnance.BROUILLON, "signer", true), new Cas(StatutOrdonnance.BROUILLON, "imprimer", false),
                new Cas(StatutOrdonnance.BROUILLON, "annuler", true),
                new Cas(StatutOrdonnance.SIGNEE, "signer", false), new Cas(StatutOrdonnance.SIGNEE, "imprimer", true),
                new Cas(StatutOrdonnance.SIGNEE, "annuler", true),
                new Cas(StatutOrdonnance.IMPRIMEE, "signer", false), new Cas(StatutOrdonnance.IMPRIMEE, "imprimer", false),
                new Cas(StatutOrdonnance.IMPRIMEE, "annuler", true),
                new Cas(StatutOrdonnance.ANNULEE, "signer", false), new Cas(StatutOrdonnance.ANNULEE, "imprimer", false),
                new Cas(StatutOrdonnance.ANNULEE, "annuler", false));
        for (Cas cas : matrice) {
            var o = Ordonnance.reconstituer(UUID.randomUUID(), new PatientId(UUID.randomUUID()), new CentreId(UUID.randomUUID()), null,
                    LocalDate.of(2026, 1, 1), cas.depart(), cas.depart() == StatutOrdonnance.BROUILLON ? null : "ORD-00042", List.of(ligne()), T0, T0, null);
            Runnable action = switch (cas.action()) {
                case "signer" -> () -> o.signer("ORD-00099", T1);
                case "imprimer" -> () -> o.marquerImprimee(T1);
                default -> () -> o.annuler(T1);
            };
            if (cas.permis()) {
                action.run();
            } else {
                assertThatThrownBy(action::run).as(cas.depart() + " " + cas.action()).isInstanceOf(RegleDeGestionViolee.class);
            }
        }
    }

    @Test
    void annuler_une_ordonnance_signee_conserve_son_numero() {
        var o = brouillon();
        o.signer("ORD-00007", T1);
        o.annuler(T1.plusSeconds(60));
        assertThat(o.statut()).isEqualTo(StatutOrdonnance.ANNULEE);
        assertThat(o.numero()).isEqualTo("ORD-00007");
    }
}

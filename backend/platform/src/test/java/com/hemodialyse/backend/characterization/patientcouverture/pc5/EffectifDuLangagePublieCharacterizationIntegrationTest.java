package com.hemodialyse.backend.characterization.patientcouverture.pc5;

import com.hemodialyse.backend.characterization.patientcouverture.pc4.EffectifDeReference;
import com.hemodialyse.backend.characterization.patientcouverture.pc4.AttestationCharacterizationSupport;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CompteursDeCouverture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PC5, risque de DÉRIVE (revue du 2026-11-05) : le prédicat d'effectif d'un jour est copié en dur dans
 * {@code CompteursDeCouvertureJdbc.patientsPresentsLe} (le module ne dépend pas de la plateforme) alors que la règle de référence
 * ({@link EffectifDeReference}, copie figée de l'ancien {@code EffectifSql} retiré avec PD1, ADR 0102) reste la source de vérité. Ce test compare les deux sur tous les états, avec et sans date d'évènement, de
 * part et d'autre de chaque borne : il échoue si l'un des deux change sans l'autre. Aucun golden (assertions seules).
 */
class EffectifDuLangagePublieCharacterizationIntegrationTest extends AttestationCharacterizationSupport {

    @Autowired
    private CompteursDeCouverture compteurs;

    @Override
    protected String dossier() {
        return "effectif_du_langage_publie";
    }

    private void patient(String alias, String etat, LocalDate evenement, LocalDate admission) {
        UUID p = patientCouvert(CENTRE_A, alias, false, "ENFANT");
        jdbc.update("UPDATE patients SET etat_patient = ?, date_evenement_etat = ?, date_admission = ? WHERE id = ?", etat,
                evenement == null ? null : Date.valueOf(evenement), admission == null ? null : Date.valueOf(admission), p);
    }

    private long reference(LocalDate jour) {
        EffectifDeReference.Fragment effectif = EffectifDeReference.presentSur("p", jour, jour);
        List<Object> args = new ArrayList<>();
        args.add(CENTRE_A);
        args.addAll(effectif.args());
        return jdbc.queryForObject("SELECT COUNT(1) FROM patients p WHERE p.center_id = ? AND " + effectif.sql(), Long.class, args.toArray());
    }

    @Test
    void le_predicat_d_effectif_du_langage_publie_egale_celui_de_la_source_de_verite_pour_chaque_etat_et_chaque_borne() {
        LocalDate pivot = LocalDate.of(2026, 6, 15);
        LocalDate ancien = LocalDate.of(2025, 1, 1);
        int i = 0;
        for (String etat : new String[]{null, "PERMANENT", "OCCASIONNEL", "VACANCIER_LOCAL", "VACANCIER_ETRANGER", "TRANSFERE", "DECEDE",
                "GREFFE", "GUERRI"}) {
            for (LocalDate evenement : new LocalDate[]{null, pivot.minusDays(1), pivot, pivot.plusDays(1)}) {
                patient("EFF_" + (++i), etat, evenement, ancien);
            }
        }
        patient("EFF_ADMIS_LE_PIVOT", "PERMANENT", null, pivot);
        patient("EFF_ADMIS_APRES", "PERMANENT", null, pivot.plusDays(1));

        for (LocalDate jour : new LocalDate[]{pivot.minusDays(2), pivot.minusDays(1), pivot, pivot.plusDays(1), pivot.plusDays(2)}) {
            assertThat(compteurs.patientsPresentsLe(CENTRE_A, jour)).as("effectif au " + jour).isEqualTo(reference(jour));
        }
    }
}

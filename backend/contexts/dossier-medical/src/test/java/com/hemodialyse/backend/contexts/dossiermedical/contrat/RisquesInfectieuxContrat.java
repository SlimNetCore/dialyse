package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.RisquesInfectieuxPublies;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat du risque infectieux publié (DM9, ADR 0101 ; KB-MED-106 à 108 reproduits) : joué contre le double en mémoire (règle Java
 * {@code RisqueInfectieux}), H2 et PostgreSQL 16 (SQL de {@code PatientsARisqueInfectieuxJdbc}). Mêmes cas que les goldens DM9
 * {@code planification/marqueurs_et_resultats}, {@code derniere_serologie}, {@code cloisonnement}.
 */
abstract class RisquesInfectieuxContrat {

    static final UUID CENTRE_A = UUID.fromString("00000000-0000-0000-0000-00000000d9a1");
    static final UUID CENTRE_B = UUID.fromString("00000000-0000-0000-0000-00000000d9b2");
    static final LocalDate J = LocalDate.of(2026, 1, 10);
    static final Instant S10 = Instant.parse("2026-01-01T10:00:00Z");
    static final Instant S11 = Instant.parse("2026-01-01T11:00:00Z");

    protected abstract RisquesInfectieuxPublies risques();

    /** Pose une sérologie telle qu'en base (marqueur et résultat en texte : la casse compte). */
    protected abstract void serologie(UUID centre, UUID patient, String marqueur, String resultat, LocalDate prelevement, Instant saisie);

    private static UUID patient(int n) {
        return new UUID(0xD9, n);
    }

    @Test
    void un_centre_sans_serologie_n_a_personne_a_risque_et_seul_un_positif_d_un_marqueur_surveille_compte() {
        assertThat(risques().patientsARisque(CENTRE_A)).isEmpty();
        serologie(CENTRE_A, patient(1), "AG_HBS", "POSITIF", J, S10);
        serologie(CENTRE_A, patient(2), "TPHA", "POSITIF", J, S10);
        serologie(CENTRE_A, patient(3), "VIH_AC", "DOUTEUX", J, S10);
        serologie(CENTRE_A, patient(4), "ARN_VHC", "EN_COURS", J, S10);
        serologie(CENTRE_A, patient(5), "AC_VHC", "NEGATIF", J, S10);
        serologie(CENTRE_A, patient(6), "AG_HBS", "positif", J, S10);
        serologie(CENTRE_A, patient(7), "ag_hbs", "POSITIF", J, S10);
        serologie(CENTRE_A, patient(8), "AG_HBS", "NEGATIF", J, S10);
        serologie(CENTRE_A, patient(8), "VIH_AC", "POSITIF", J, S10);
        assertThat(risques().patientsARisque(CENTRE_A)).containsExactlyInAnyOrder(patient(1), patient(8));
        assertThat(risques().patientsARisque(CENTRE_B)).isEmpty();
    }

    @Test
    void la_derniere_serologie_conclusive_tranche_departagee_par_le_prelevement_puis_la_saisie_et_le_douteux_ne_leve_pas_le_risque() {
        serologie(CENTRE_A, patient(1), "AG_HBS", "POSITIF", J, S10);
        serologie(CENTRE_A, patient(1), "AG_HBS", "NEGATIF", J.plusDays(5), S10);
        serologie(CENTRE_A, patient(2), "AG_HBS", "NEGATIF", J, S10);
        serologie(CENTRE_A, patient(2), "AG_HBS", "POSITIF", J.plusDays(5), S10);
        serologie(CENTRE_A, patient(3), "AG_HBS", "POSITIF", J, S10);
        serologie(CENTRE_A, patient(3), "AG_HBS", "NEGATIF", J, S11);
        serologie(CENTRE_A, patient(4), "AG_HBS", "NEGATIF", J, S10);
        serologie(CENTRE_A, patient(4), "AG_HBS", "POSITIF", J, S11);
        // KB-MED-107 : égalité totale, le positif l'emporte.
        serologie(CENTRE_A, patient(5), "AG_HBS", "POSITIF", J, S10);
        serologie(CENTRE_A, patient(5), "AG_HBS", "NEGATIF", J, S10);
        // KB-MED-106 (corrigé) : un douteux ou un en cours plus récent n'efface pas le positif.
        serologie(CENTRE_A, patient(6), "AG_HBS", "POSITIF", J, S10);
        serologie(CENTRE_A, patient(6), "AG_HBS", "DOUTEUX", J.plusDays(5), S10);
        serologie(CENTRE_A, patient(7), "AG_HBS", "POSITIF", J, S10);
        serologie(CENTRE_A, patient(7), "AG_HBS", "EN_COURS", J.plusDays(5), S10);
        // Un autre marqueur plus récent ne change rien.
        serologie(CENTRE_A, patient(8), "VIH_AC", "POSITIF", J, S10);
        serologie(CENTRE_A, patient(8), "AG_HBS", "NEGATIF", J.plusDays(9), S10);
        // Positif, négatif, puis douteux : le dernier résultat conclusif est négatif.
        serologie(CENTRE_A, patient(9), "AG_HBS", "POSITIF", J, S10);
        serologie(CENTRE_A, patient(9), "AG_HBS", "NEGATIF", J.plusDays(5), S10);
        serologie(CENTRE_A, patient(9), "AG_HBS", "DOUTEUX", J.plusDays(9), S10);
        // Négatif puis douteux : pas de risque. Douteux seul : pas de risque.
        serologie(CENTRE_A, patient(10), "AG_HBS", "NEGATIF", J, S10);
        serologie(CENTRE_A, patient(10), "AG_HBS", "DOUTEUX", J.plusDays(5), S10);
        serologie(CENTRE_A, patient(11), "AC_VHC", "DOUTEUX", J, S10);
        // Marqueurs indépendants : VIH positif puis douteux reste à risque même si AgHBs est négatif.
        serologie(CENTRE_A, patient(12), "AG_HBS", "POSITIF", J, S10);
        serologie(CENTRE_A, patient(12), "AG_HBS", "NEGATIF", J.plusDays(5), S10);
        serologie(CENTRE_A, patient(12), "VIH_AC", "POSITIF", J, S10);
        serologie(CENTRE_A, patient(12), "VIH_AC", "EN_COURS", J.plusDays(5), S10);
        assertThat(risques().patientsARisque(CENTRE_A))
                .containsExactlyInAnyOrder(patient(2), patient(4), patient(5), patient(6), patient(7), patient(8), patient(12));
    }

    /** KB-MED-108 : le risque est lu par le centre de la SÉROLOGIE ; un négatif plus récent posé ailleurs n'y lève rien. */
    @Test
    void le_cloisonnement_se_fait_par_la_ligne_pas_par_la_fiche() {
        serologie(CENTRE_B, patient(1), "AG_HBS", "POSITIF", J, S10);
        serologie(CENTRE_A, patient(2), "AG_HBS", "POSITIF", J, S10);
        serologie(CENTRE_B, patient(2), "AG_HBS", "NEGATIF", J.plusDays(5), S10);
        assertThat(risques().patientsARisque(CENTRE_A)).containsExactly(patient(2));
        assertThat(risques().patientsARisque(CENTRE_B)).containsExactly(patient(1));
    }
}

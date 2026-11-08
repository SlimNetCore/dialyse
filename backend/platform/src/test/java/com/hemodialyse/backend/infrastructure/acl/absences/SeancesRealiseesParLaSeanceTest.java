package com.hemodialyse.backend.infrastructure.acl.absences;

import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.SeancesRealiseesFournies.JourRealiseFourni;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesPubliees;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fournisseurs des Absences de patients (ADR 0098, SE7d) : délégation pure aux ports publiés de la Séance, lignes, ordre et doublons
 * conservés, arguments transmis sans changement (table-driven).
 */
class SeancesRealiseesParLaSeanceTest {

    private static final UUID CENTRE = new UUID(0xDA, 1);
    private static final UUID AUTRE_CENTRE = new UUID(0xDA, 2);
    private static final UUID P1 = new UUID(0xDA, 0x11);
    private static final UUID P2 = new UUID(0xDA, 0x12);
    private static final LocalDate JOUR = LocalDate.of(2026, 3, 3);

    /** Séance publiée figée : ne répond que pour le centre attendu, pour vérifier que l'argument passe tel quel. */
    private static final SeancesRealiseesPubliees PUBLIEES = new SeancesRealiseesPubliees() {
        @Override
        public List<JourRealisePublie> joursRealisesDuCentre(UUID centre, LocalDate du, LocalDate au) {
            return centre.equals(CENTRE) && du.equals(JOUR) && au.equals(JOUR.plusDays(6))
                    ? List.of(new JourRealisePublie(P2, JOUR), new JourRealisePublie(P1, JOUR)) : List.of();
        }

        @Override
        public List<LocalDate> joursRealisesDuPatient(UUID centre, UUID patient, LocalDate du, LocalDate au) {
            return centre.equals(CENTRE) && patient.equals(P1) ? List.of(JOUR, JOUR.plusDays(2)) : List.of();
        }

        @Override
        public boolean estRealisee(UUID centre, UUID patient, LocalDate jour) {
            return centre.equals(CENTRE) && patient.equals(P1) && jour.equals(JOUR);
        }

        @Override
        public List<UUID> patientsRealisesLe(UUID centre, LocalDate jour) {
            return centre.equals(CENTRE) ? List.of(P1, P1, P2) : List.of();
        }
    };

    private final SeancesRealiseesParLaSeance fournisseur = new SeancesRealiseesParLaSeance(PUBLIEES);

    @Test
    void les_jours_realises_du_centre_gardent_l_ordre_publie() {
        assertThat(fournisseur.joursRealisesDuCentre(CENTRE, JOUR, JOUR.plusDays(6)))
                .containsExactly(new JourRealiseFourni(P2, JOUR), new JourRealiseFourni(P1, JOUR));
        assertThat(fournisseur.joursRealisesDuCentre(AUTRE_CENTRE, JOUR, JOUR.plusDays(6))).isEmpty();
    }

    @Test
    void les_jours_du_patient_et_les_patients_du_jour_sont_transmis_avec_leurs_doublons() {
        assertThat(fournisseur.joursRealisesDuPatient(CENTRE, P1, JOUR, JOUR.plusDays(5))).containsExactly(JOUR, JOUR.plusDays(2));
        assertThat(fournisseur.patientsRealisesLe(CENTRE, JOUR)).containsExactly(P1, P1, P2);
        assertThat(fournisseur.patientsRealisesLe(AUTRE_CENTRE, JOUR)).isEmpty();
    }

    @ParameterizedTest(name = "centre {0}, patient {1}, décalage {2} : {3}")
    @CsvSource({"A, P1, 0, true", "A, P2, 0, false", "B, P1, 0, false", "A, P1, 1, false"})
    void la_seance_realisee_est_celle_publiee(String centre, String patient, int decalage, boolean attendu) {
        assertThat(fournisseur.estRealisee(centre.equals("A") ? CENTRE : AUTRE_CENTRE, patient.equals("P1") ? P1 : P2, JOUR.plusDays(decalage)))
                .isEqualTo(attendu);
    }

    @ParameterizedTest(name = "lien publié dans le centre {0} : {1}")
    @CsvSource({"A, true", "B, false"})
    void le_lien_d_exclusion_est_celui_publie(String centre, boolean attendu) {
        UUID absence = new UUID(0xDA, 0xAB);
        var liens = new LiensDExclusionParLaSeance((c, a) -> c.equals(CENTRE) && a.equals(absence));

        assertThat(liens.absenceLiee(centre.equals("A") ? CENTRE : AUTRE_CENTRE, absence)).isEqualTo(attendu);
    }
}

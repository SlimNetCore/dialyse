package com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.domain.SeanceRealisee;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.LiensDExclusionFournis;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.SeancesRealiseesFournies;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Traduction table-driven des langages fournis (séances réalisées, lien d'exclusion) vers les ports des Absences (ADR 0098, SE7d) : lignes,
 * ordre et doublons transmis tels quels, identifiants typés, centre et bornes passés sans changement.
 */
class TraducteursDesSeancesTest {

    private static final CentreId CENTRE = new CentreId(new UUID(0xD7, 1));
    private static final UUID P1 = new UUID(0xD7, 0x11);
    private static final UUID P2 = new UUID(0xD7, 0x12);
    private static final LocalDate JOUR = LocalDate.of(2026, 3, 3);

    /** Double qui rend des lignes figées et retient les arguments reçus. */
    private static final class Fournies implements SeancesRealiseesFournies {
        final List<String> appels = new ArrayList<>();

        @Override
        public List<JourRealiseFourni> joursRealisesDuCentre(UUID centre, LocalDate du, LocalDate au) {
            appels.add("centre " + centre + " " + du + " " + au);
            return List.of(new JourRealiseFourni(P2, JOUR), new JourRealiseFourni(P1, JOUR), new JourRealiseFourni(P1, JOUR.plusDays(1)));
        }

        @Override
        public List<LocalDate> joursRealisesDuPatient(UUID centre, UUID patient, LocalDate du, LocalDate au) {
            appels.add("patient " + centre + " " + patient + " " + du + " " + au);
            return List.of(JOUR, JOUR.plusDays(2));
        }

        @Override
        public boolean estRealisee(UUID centre, UUID patient, LocalDate jour) {
            appels.add("realisee " + centre + " " + patient + " " + jour);
            return patient.equals(P1);
        }

        @Override
        public List<UUID> patientsRealisesLe(UUID centre, LocalDate jour) {
            return List.of(P1, P1);
        }
    }

    @Test
    void les_jours_realises_du_centre_gardent_leurs_lignes_et_leur_ordre() {
        Fournies fournies = new Fournies();

        List<SeanceRealisee> lues = new SeancesRealiseesPubliees(fournies).duCentre(CENTRE, JOUR.minusDays(2), JOUR.plusDays(4));

        assertThat(lues).containsExactly(new SeanceRealisee(new PatientId(P2), JOUR), new SeanceRealisee(new PatientId(P1), JOUR),
                new SeanceRealisee(new PatientId(P1), JOUR.plusDays(1)));
        assertThat(fournies.appels).containsExactly("centre " + CENTRE.valeur() + " " + JOUR.minusDays(2) + " " + JOUR.plusDays(4));
    }

    @Test
    void les_jours_d_un_patient_sont_transmis_tels_quels() {
        Fournies fournies = new Fournies();

        assertThat(new SeancesRealiseesPubliees(fournies).joursDuPatient(CENTRE, new PatientId(P1), JOUR, JOUR.plusDays(5)))
                .containsExactly(JOUR, JOUR.plusDays(2));
        assertThat(fournies.appels).containsExactly("patient " + CENTRE.valeur() + " " + P1 + " " + JOUR + " " + JOUR.plusDays(5));
    }

    @ParameterizedTest(name = "patient {0} : réalisée = {1}")
    @CsvSource({"P1, true", "P2, false"})
    void la_seance_realisee_un_jour_est_celle_que_la_seance_publie(String patient, boolean attendu) {
        UUID id = patient.equals("P1") ? P1 : P2;

        assertThat(new SeancesRealiseesPubliees(new Fournies()).realisee(CENTRE, new PatientId(id), JOUR)).isEqualTo(attendu);
    }

    @ParameterizedTest(name = "lien publié = {0}")
    @CsvSource({"true", "false"})
    void l_absence_vient_d_une_exclusion_si_la_seance_publie_le_lien_dans_ce_centre(boolean lie) {
        AbsenceId absence = new AbsenceId(new UUID(0xD7, 0xAB));
        List<String> appels = new ArrayList<>();
        LiensDExclusionFournis liens = (centre, a) -> {
            appels.add(centre + " " + a);
            return lie;
        };

        assertThat(new ExclusionsDeSeancesPubliees(liens).absenceIssueDUneExclusion(CENTRE, absence)).isEqualTo(lie);
        assertThat(appels).containsExactly(CENTRE.valeur() + " " + absence.valeur());
    }
}

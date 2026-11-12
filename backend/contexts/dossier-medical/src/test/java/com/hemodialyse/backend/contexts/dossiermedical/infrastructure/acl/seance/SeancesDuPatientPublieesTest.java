package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.dossiermedical.application.SeancesDuPatient.MesuresDesSeances;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PeriodeDeStatistiques;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Tension;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.StatistiquesDesSeancesPubliees;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PL4 : l'ACL du Dossier medical vers {@code seance.v1} (statistiques des seances). Le centre, le patient et la periode sont transmis tels
 * quels ; les moyennes absentes restent absentes (le zero est une regle du cas d'usage) ; les tensions brutes sont lues en {@link Tension}.
 */
class SeancesDuPatientPublieesTest {

    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final LocalDate DU = LocalDate.of(2026, 1, 1);
    private static final LocalDate AU = LocalDate.of(2026, 1, 31);

    private final List<String> appels = new ArrayList<>();

    private final StatistiquesDesSeancesPubliees publiees = new StatistiquesDesSeancesPubliees() {
        private void tracer(String nom, UUID centre, UUID patient, LocalDate du, LocalDate au) {
            appels.add(nom + " " + centre + " " + patient + " " + du + " " + au);
        }

        @Override
        public long nombreDeSeances(UUID centre, UUID patient, LocalDate du, LocalDate au) {
            tracer("nombre", centre, patient, du, au);
            return 4;
        }

        @Override
        public Double moyenneDuPoidsAvant(UUID centre, UUID patient, LocalDate du, LocalDate au) {
            tracer("avant", centre, patient, du, au);
            return 70.5;
        }

        @Override
        public Double moyenneDuPoidsApres(UUID centre, UUID patient, LocalDate du, LocalDate au) {
            tracer("apres", centre, patient, du, au);
            return null;
        }

        @Override
        public Double moyenneDeLUltrafiltration(UUID centre, UUID patient, LocalDate du, LocalDate au) {
            tracer("uf", centre, patient, du, au);
            return 1800.0;
        }

        @Override
        public List<Map<String, Object>> evolutionDuPoids(UUID centre, UUID patient, LocalDate du, LocalDate au) {
            tracer("poids", centre, patient, du, au);
            return List.of(Map.of("poids_avant_kg", 70.5));
        }

        @Override
        public List<TensionsDeSeancePubliees> evolutionDeLaTension(UUID centre, UUID patient, LocalDate du, LocalDate au) {
            tracer("tension", centre, patient, du, au);
            return List.of(new TensionsDeSeancePubliees(DU, "130/80", "bizarre"), new TensionsDeSeancePubliees(null, null, "120-70"));
        }
    };

    private final SeancesDuPatientPubliees acl = new SeancesDuPatientPubliees(publiees);

    @Test
    void should_traduire_les_mesures_publiees_et_lire_les_tensions() {
        MesuresDesSeances mesures = acl.lire(new CentreId(CENTRE), new PatientId(PATIENT), new PeriodeDeStatistiques(DU, AU));

        assertThat(mesures.nombre()).isEqualTo(4);
        assertThat(mesures.moyennePoidsAvant()).isEqualTo(70.5);
        assertThat(mesures.moyennePoidsApres()).isNull();
        assertThat(mesures.moyenneUltrafiltration()).isEqualTo(1800.0);
        assertThat(mesures.evolutionDuPoids()).containsExactly(Map.of("poids_avant_kg", 70.5));
        assertThat(mesures.evolutionDeLaTension()).hasSize(2);
        assertThat(mesures.evolutionDeLaTension().get(0).date()).isEqualTo(DU);
        assertThat(mesures.evolutionDeLaTension().get(0).avant()).isEqualTo(new Tension(130, 80));
        assertThat(mesures.evolutionDeLaTension().get(0).apres()).isEqualTo(Tension.ILLISIBLE);
        assertThat(mesures.evolutionDeLaTension().get(1).date()).isNull();
        assertThat(mesures.evolutionDeLaTension().get(1).avant()).isEqualTo(Tension.ILLISIBLE);
        assertThat(mesures.evolutionDeLaTension().get(1).apres()).isEqualTo(new Tension(120, 70));
    }

    @Test
    void should_transmettre_le_centre_le_patient_et_la_periode_a_chaque_lecture() {
        acl.lire(new CentreId(CENTRE), new PatientId(PATIENT), new PeriodeDeStatistiques(DU, null));

        assertThat(appels).hasSize(6).allSatisfy(a -> assertThat(a).contains(CENTRE + " " + PATIENT + " " + DU + " null"));
    }
}

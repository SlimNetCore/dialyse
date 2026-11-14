package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.DeplacementTemporaire;
import com.hemodialyse.backend.contexts.planification.domain.DeplacementsTemporairesRepository;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.DeplacementTemporaireId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.DeplacementsDuJourDesPatients.DeplacementDuJour;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** LEC-PLN-GMAO (ADR 0179) : le déplacement du jour est lu par centre, patient et date (dépôt en mémoire ; JDBC et PostgreSQL 16 : contrats de la Séance et de l'optimisation). */
class ConsulterLesDeplacementsDuJourTest {

    private static final CentreId CENTRE = new CentreId(new UUID(0, 0xA));
    private static final CentreId AUTRE_CENTRE = new CentreId(new UUID(0, 0xB));
    private static final UUID ALPHA = new UUID(1, 1);
    private static final UUID BRAVO = new UUID(1, 2);
    private static final LocalDate JOUR = LocalDate.of(2026, 6, 14);

    private final List<DeplacementTemporaire> temporaires = new ArrayList<>();
    private final DeplacementsTemporairesRepository depot = new DeplacementsTemporairesRepository() {
        @Override
        public void enregistrer(List<DeplacementTemporaire> deplacements) {
            temporaires.addAll(deplacements);
        }

        @Override
        public List<DeplacementTemporaire> entre(CentreId centre, LocalDate du, LocalDate au) {
            return temporaires.stream().filter(t -> t.centre().equals(centre) && !t.date().isBefore(du) && !t.date().isAfter(au))
                    .sorted(Comparator.comparing(DeplacementTemporaire::date)).toList();
        }
    };
    private final ConsulterLesDeplacementsDuJour lecture = new ConsulterLesDeplacementsDuJour(depot);

    private void deplacement(CentreId centre, UUID patient, LocalDate date, long salle) {
        depot.enregistrer(List.of(DeplacementTemporaire.reconstituer(new DeplacementTemporaireId(UUID.randomUUID()), centre,
                new PatientId(patient), date, new SalleId(new UUID(3, salle)), new CreneauId(new UUID(4, salle)),
                new GenerateurId(new UUID(5, salle)), null)));
    }

    @Test
    void le_deplacement_du_jour_du_patient_au_centre_est_publie() {
        deplacement(CENTRE, ALPHA, JOUR, 1);
        deplacement(CENTRE, BRAVO, JOUR, 2);

        assertThat(lecture.duPatient(CENTRE.valeur(), ALPHA, JOUR))
                .contains(new DeplacementDuJour(new UUID(3, 1), new UUID(4, 1), new UUID(5, 1)));
    }

    @Test
    void un_autre_jour_un_autre_patient_ou_un_autre_centre_n_a_pas_de_deplacement() {
        deplacement(CENTRE, ALPHA, JOUR, 1);
        deplacement(AUTRE_CENTRE, BRAVO, JOUR, 2);

        assertThat(lecture.duPatient(CENTRE.valeur(), ALPHA, JOUR.plusDays(1))).isEmpty();
        assertThat(lecture.duPatient(CENTRE.valeur(), BRAVO, JOUR)).isEmpty();
        assertThat(lecture.duPatient(AUTRE_CENTRE.valeur(), ALPHA, JOUR)).isEmpty();
    }
}

package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.PlacesDesPatients;
import com.hemodialyse.backend.contexts.seance.application.ForfaitsDeSeance;
import com.hemodialyse.backend.contexts.seance.application.HistoriqueDesSeances;
import com.hemodialyse.backend.contexts.seance.application.PlaceDuPatient;
import com.hemodialyse.backend.contexts.seance.application.PlanningDesPatients;
import com.hemodialyse.backend.contexts.seance.domain.ForfaitsDuCentre;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.patientcouverture.PlanningDesPatientsParLaFiche;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.planification.PlaceDuPatientParLaPlanification;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.patientcouverture.ForfaitsDuCentreParLeCatalogue;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.ForfaitsDeSeanceJdbc;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.HistoriqueDesSeancesJdbc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Contrat des lectures reprises de la plateforme, sur H2 en mode PostgreSQL. */
class LecturesReprisesDeLaPlateformeH2ContratTest extends LecturesReprisesDeLaPlateformeContrat {

    private final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:lectures_reprises_seance;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1", "sa", ""));

    @Override
    JdbcTemplate jdbc() {
        return jdbc;
    }

    @Override
    HistoriqueDesSeances historique() {
        return new HistoriqueDesSeancesJdbc(jdbc, new com.hemodialyse.backend.contexts.seance.infrastructure.acl.patientcouverture
                .PatientsDeLHistoriqueParLaFiche(new com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence
                .LotsDePatientsJdbc(jdbc)));
    }

    @Override
    ForfaitsDeSeance forfaits() {
        return new ForfaitsDeSeanceJdbc(jdbc, new com.hemodialyse.backend.contexts.seance.infrastructure.acl.patientcouverture
                .ForfaitDeLaPriseEnChargeParLaCouverture(new com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence
                .CouvertureDesPatientsJdbc(jdbc)));
    }

    @Override
    ForfaitsDuCentre referentiel() {
        return new ForfaitsDuCentreParLeCatalogue(
                new com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.ForfaitsJdbc(jdbc, centre -> { }));
    }

    @Override
    PlanningDesPatients planning() {
        return new PlanningDesPatientsParLaFiche(jdbc,
                new com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.ProgrammationPublieeJdbc(jdbc));
    }

    @Override
    PlaceDuPatient place(PlacesDesPatients places) {
        return new PlaceDuPatientParLaPlanification(new com.hemodialyse.backend.contexts.planification.application.ConsulterLesDeplacementsDuJour(
                new com.hemodialyse.backend.contexts.planification.infrastructure.persistence.DeplacementsTemporairesJdbc(jdbc)), places);
    }
}

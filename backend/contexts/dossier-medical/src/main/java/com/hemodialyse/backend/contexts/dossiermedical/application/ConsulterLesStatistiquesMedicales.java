package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.StatistiquesMedicalesPubliees;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Consulte les statistiques medicales d'un patient du centre de la session sur une periode (PL4) : moyennes d'hemoglobine, de Kt/V et de
 * ferritine, tendance des bilans et des prescriptions d'EPO. Memes gardes que les statistiques paramedicales.
 */
public class ConsulterLesStatistiquesMedicales {

    private final GardeDesStatistiques garde;
    private final StatistiquesMedicalesPubliees medicales;

    public ConsulterLesStatistiquesMedicales(SessionCourante session, PatientsDuCentre patients, StatistiquesMedicalesPubliees medicales) {
        this.garde = new GardeDesStatistiques(session, patients);
        this.medicales = medicales;
    }

    public StatistiquesMedicales executer(UUID centreDemande, UUID patient, LocalDate du, LocalDate au) {
        GardeDesStatistiques.Demande demande = garde.ouvrir(centreDemande, patient, du, au);
        garde.exigerLePatient(demande);
        return LecturesDesStatistiques.medicales(medicales, demande);
    }
}

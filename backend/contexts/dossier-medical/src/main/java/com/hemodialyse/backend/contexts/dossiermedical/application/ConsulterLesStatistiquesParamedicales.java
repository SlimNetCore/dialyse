package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.StatistiquesMedicalesPubliees;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Consulte les statistiques paramedicales d'un patient du centre de la session sur une periode (PL4, ex {@code PatientStatsQueryService}) :
 * nombre de seances, moyennes de poids et d'ultrafiltration, poids sec cible, evolution du poids et de la tension. Gardes dans l'ordre :
 * centre (403), periode (400 INVALID_DATE_RANGE), patient du centre (404 PATIENT_INTROUVABLE). Droits : ADMIN, INFIRMIER, MEDECIN, SECRETAIRE.
 */
public class ConsulterLesStatistiquesParamedicales {

    private final GardeDesStatistiques garde;
    private final SeancesDuPatient seances;
    private final StatistiquesMedicalesPubliees medicales;
    private final HorlogeDuCentre horloge;

    public ConsulterLesStatistiquesParamedicales(SessionCourante session, PatientsDuCentre patients, SeancesDuPatient seances,
                                                 StatistiquesMedicalesPubliees medicales, HorlogeDuCentre horloge) {
        this.garde = new GardeDesStatistiques(session, patients);
        this.seances = seances;
        this.medicales = medicales;
        this.horloge = horloge;
    }

    public StatistiquesParamedicales executer(UUID centreDemande, UUID patient, LocalDate du, LocalDate au) {
        GardeDesStatistiques.Demande demande = garde.ouvrir(centreDemande, patient, du, au);
        garde.exigerLePatient(demande);
        return LecturesDesStatistiques.paramedicales(seances, medicales, horloge, demande);
    }
}

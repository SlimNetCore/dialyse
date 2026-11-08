package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.EffectifDuMois;
import com.hemodialyse.backend.contexts.seance.domain.MoisDuTableauDeBord;
import com.hemodialyse.backend.contexts.seance.domain.SeancesAttendues;
import com.hemodialyse.backend.contexts.seance.domain.TableauDeBordDesSeances;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Tableau de bord mensuel des séances d'un centre (GET /api/v1/seances/dashboard). Lecture seule. Le centre doit être celui de la
 * session (403, avant tout 400 : KB-SEA-63). « Aujourd'hui » est le jour du centre ({@link HorlogeDuCentre}), jamais celui de la JVM.
 * Aucune formule ici : elles vivent dans le domaine.
 */
public class ConsulterLeTableauDeBord {

    private final SessionCourante session;
    private final HorlogeDuCentre horloge;
    private final SeancesRealisees seances;
    private final PatientsDuTableauDeBord patients;
    private final AbsencesDuCentre absences;
    private final JoursBloques joursBloques;

    public ConsulterLeTableauDeBord(SessionCourante session, HorlogeDuCentre horloge, SeancesRealisees seances,
                                    PatientsDuTableauDeBord patients, AbsencesDuCentre absences, JoursBloques joursBloques) {
        this.session = session;
        this.horloge = horloge;
        this.seances = seances;
        this.patients = patients;
        this.absences = absences;
        this.joursBloques = joursBloques;
    }

    public TableauDeBordDesSeances executer(UUID centreDemande, int annee, int mois) {
        CentreId centre = session.centre(centreDemande);
        return pour(centre, new MoisDuTableauDeBord(annee, mois));
    }

    TableauDeBordDesSeances pour(CentreId centre, MoisDuTableauDeBord mois) {
        LocalDate debut = mois.premierJour();
        LocalDate fin = mois.dernierJour();
        LocalDate aujourdhui = horloge.aujourdhui(centre);
        LocalDate limite = mois.dernierJourEchu(aujourdhui);

        long presences = seances.nombre(centre, mois);
        long nombreDAbsences = mois.aUnJourEchu(aujourdhui) ? absences.nombre(centre, debut, limite) : 0L;
        var bloques = joursBloques.du(centre, mois);
        var patientsDuCentre = patients.duCentre(centre);
        var attendues = SeancesAttendues.calculer(patientsDuCentre, debut, limite, bloques);
        var effectif = EffectifDuMois.composer(patientsDuCentre, patients.presentsDuMois(centre, mois), debut, fin);
        return TableauDeBordDesSeances.composer(mois, aujourdhui, presences, nombreDAbsences, attendues, effectif, bloques.size());
    }
}

package com.hemodialyse.backend.contexts.planification.application;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.ParametresDuPlanningRepository;
import com.hemodialyse.backend.contexts.planification.domain.PatientsParPosteEtSerie;
import com.hemodialyse.backend.contexts.planification.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.BaseDeLaCapaciteTheorique;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.PresenceDesPatients;

import java.time.LocalDate;
import java.util.UUID;

/** Lecture publiée de la base de la capacité théorique d'un centre (pilotage de la Direction). */
public class ConsulterLaBaseDeLaCapaciteTheorique implements BaseDeLaCapaciteTheorique {

    private final SeriesDuCentre creneaux;
    private final ParametresDuPlanningRepository parametres;
    private final PresenceDesPatients presence;

    public ConsulterLaBaseDeLaCapaciteTheorique(SeriesDuCentre creneaux, ParametresDuPlanningRepository parametres,
                                                PresenceDesPatients presence) {
        this.creneaux = creneaux;
        this.parametres = parametres;
        this.presence = presence;
    }

    @Override
    public Base duCentre(UUID centre, LocalDate du, LocalDate au) {
        CentreId id = new CentreId(centre);
        int parPoste;
        try {
            parPoste = parametres.lire(id).patientsParPoste().nombre();
        } catch (SaisieInvalide valeurInvalide) {
            parPoste = PatientsParPosteEtSerie.PAR_DEFAUT;
        }
        return new Base(creneaux.nombre(id), parPoste, presence.fileActive(centre, du, au));
    }
}

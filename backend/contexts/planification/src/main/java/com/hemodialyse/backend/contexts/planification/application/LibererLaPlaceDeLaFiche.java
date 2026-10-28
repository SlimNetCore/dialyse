package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDesPlaces;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.CachesDesPatients;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.LiberationDesPlaces;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Libération à l'enregistrement d'une fiche (RG-PAT-034) : l'échéance est jugée à la VEILLE (la veille reste conservée pour le
 * contrôle des absences), le mouvement est daté du jour réel du centre. S'exécute dans la transaction de l'appelant. Une fiche sans
 * place échue n'est ni verrouillée ni modifiée (aucun verrou pris hors du cas à libérer).
 */
public class LibererLaPlaceDeLaFiche implements LiberationDesPlaces {

    private final HorlogeDesCentres horloge;
    private final LibererUnePlace liberation;

    public LibererLaPlaceDeLaFiche(HorlogeDesCentres horloge, UniteDeTravail uniteDeTravail, VerrouDesPlaces verrou,
                                   PlacesTenues places, MouvementsDesPatients mouvements, CachesDesPatients caches, Clock instant) {
        this.horloge = horloge;
        this.liberation = new LibererUnePlace(uniteDeTravail, verrou, places, mouvements, caches, instant);
    }

    @Override
    public LocalDate aujourdhui(UUID centre) {
        return horloge.aujourdhui(new CentreId(centre));
    }

    @Override
    public boolean siEchue(UUID centre, UUID patient) {
        CentreId centreId = new CentreId(centre);
        PatientId patientId = new PatientId(patient);
        LocalDate aujourdhui = horloge.aujourdhui(centreId);
        LocalDate veille = aujourdhui.minusDays(1);
        if (!liberation.estEchue(centreId, patientId, veille)) return false;
        return RefusDuPlacement.traduire(() -> liberation.liberer(centreId, patientId, veille, aujourdhui));
    }
}

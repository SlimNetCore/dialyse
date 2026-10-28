package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentre;
import com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentre.Capacite;
import com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentreRepository;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.CapaciteDesCentres;

import java.time.LocalDate;
import java.util.UUID;

/** Capacité hebdomadaire de placement d'un centre (lecture publiée pour le pilotage). */
public class ConsulterLaCapacite implements CapaciteDesCentres {

    private final UniteDeTravail uniteDeTravail;
    private final PlacesDuCentreRepository places;
    private final LectureDesRessources ressources;

    public ConsulterLaCapacite(UniteDeTravail uniteDeTravail, PlacesDuCentreRepository places, LectureDesRessources ressources) {
        this.uniteDeTravail = uniteDeTravail;
        this.places = places;
        this.ressources = ressources;
    }

    @Override
    public CapaciteDuCentre duCentre(UUID centreId, LocalDate le) {
        CentreId centre = new CentreId(centreId);
        return uniteDeTravail.executer(() -> {
            PlacesDuCentre lues = places.charger(centre, ressources.duCentre(centre, le), le);
            Capacite c = lues.capacite();
            return new CapaciteDuCentre(c.generateursEnService(), c.creneaux(), c.joursOuverts(), c.places(), c.occupees());
        });
    }
}

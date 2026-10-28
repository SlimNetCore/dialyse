package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.PlaceTenue;
import com.hemodialyse.backend.contexts.planification.domain.PlaceTenue.PlaceLiberee;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDesPlaces;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.CachesDesPatients;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Libère la place d'UN patient dans une transaction qui lui est propre (décision du 27/10 : plus de transaction pour tout le
 * centre, KB-PLN-78). Ordre global des verrous : le verrou du centre, puis la ligne de la fiche. Sous les verrous, la fiche est
 * relue : une place déjà libérée ou replacée entre-temps est jugée sur son état réel. Le mouvement {@code PLACE_LIBEREE} est écrit
 * dans la même transaction, avant l'effacement ; il n'est jamais écrit deux fois de suite pour la même place.
 */
class LibererUnePlace {

    private final UniteDeTravail uniteDeTravail;
    private final VerrouDesPlaces verrou;
    private final PlacesTenues places;
    private final MouvementsDesPatients mouvements;
    private final CachesDesPatients caches;
    private final Clock horloge;

    LibererUnePlace(UniteDeTravail uniteDeTravail, VerrouDesPlaces verrou, PlacesTenues places, MouvementsDesPatients mouvements,
                    CachesDesPatients caches, Clock horloge) {
        this.uniteDeTravail = uniteDeTravail;
        this.verrou = verrou;
        this.places = places;
        this.mouvements = mouvements;
        this.caches = caches;
        this.horloge = horloge;
    }

    /**
     * @param echeanceJugeeLe jour auquel le dernier jour de place est jugé dépassé
     * @param dateDEffet      jour du centre porté par le mouvement
     * @return vrai si la place a été libérée par cet appel
     */
    boolean liberer(CentreId centre, PatientId patient, LocalDate echeanceJugeeLe, LocalDate dateDEffet) {
        return uniteDeTravail.executer(() -> {
            verrou.prendre(centre);
            Optional<PlaceLiberee> liberee = places.verrouillee(centre, patient)
                    .flatMap(tenue -> tenue.liberer(echeanceJugeeLe, dateDEffet, Instant.now(horloge)));
            if (liberee.isEmpty()) return false;
            if (!mouvements.dejaTrace(liberee.get())) mouvements.tracer(liberee.get());
            places.liberer(liberee.get());
            caches.evincer(centre.valeur(), patient.valeur());
            return true;
        });
    }

    /**
     * Lecture sans verrou : la fiche tient-elle une place échue à ce jour ?
     */
    boolean estEchue(CentreId centre, PatientId patient, LocalDate echeanceJugeeLe) {
        return places.de(centre, patient).filter(tenue -> tenue.estEchueLe(echeanceJugeeLe)).isPresent();
    }
}

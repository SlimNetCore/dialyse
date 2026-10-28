package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.PlaceTenue;
import com.hemodialyse.backend.contexts.planification.domain.PlaceTenue.PlaceLiberee;

import java.util.List;
import java.util.Optional;

/**
 * Port sortant vers Patient et couverture : les places encore tenues sur les fiches (colonnes de place de {@code patients}, sans
 * migration) avec leur dernier jour de place, et l'effacement d'une place libérée. L'écriture ne touche QUE les colonnes de place
 * (jamais la ligne entière). Borné au centre.
 */
public interface PlacesTenues {

    /**
     * Les fiches du centre qui tiennent encore une salle, un créneau ou un générateur, triées par identifiant. Sans verrou.
     */
    List<PlaceTenue> duCentre(CentreId centre);

    /**
     * La place tenue par cette fiche, sans verrou ; vide si la fiche n'a plus de place ou n'est pas de ce centre.
     */
    Optional<PlaceTenue> de(CentreId centre, PatientId patient);

    /**
     * Comme {@link #de} mais verrouille la ligne de la fiche jusqu'à la fin de la transaction (après le verrou du centre).
     */
    Optional<PlaceTenue> verrouillee(CentreId centre, PatientId patient);

    /**
     * Efface salle, créneau, générateur et jours de la fiche.
     */
    void liberer(PlaceLiberee liberee);
}

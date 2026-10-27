package com.hemodialyse.backend.contexts.planification.publishedlanguage;

import java.util.UUID;

/**
 * Port publié (commande) appelé par la fiche du patient (Patient et couverture, ADR 0052/0053, slice P7) : à l'enregistrement d'une
 * fiche dont le dernier jour de place est dépassé de plus d'un jour, la Planification libère la place <b>dans la transaction de
 * l'appelant</b> (salle, créneau, générateur et jours effacés, mouvement {@code PLACE_LIBEREE} daté du jour du centre). L'échéance
 * est jugée à la veille : le contrôle des absences de la veille voit encore les jours de dialyse. La fiche enregistrée doit être
 * visible en SQL (l'appelant vide son contexte de persistance avant l'appel).
 * <p>
 * Un patient dont la libération a déjà été tracée n'en produit pas une seconde. Verrou du centre non obtenu en 5 s :
 * {@link PlacementDesPatients.EcritureConcurrente} (409).
 */
public interface LiberationDesPlaces {

    /**
     * @return vrai si la place a été libérée par cet appel (la fiche renvoyée doit alors être alignée), faux sinon
     */
    boolean siEchue(UUID centre, UUID patient);
}

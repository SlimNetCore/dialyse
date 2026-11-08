package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié de la Séance ({@code seance.v1}, ADR 0096) : la place d'une séance, telle que la Séance la porte. La salle et le
 * créneau sont lus par leur code et leur libellé tels qu'enregistrés ; leur interprétation (demi-journée, périmètre) reste au
 * consommateur. Vide si la séance est inconnue ou d'un autre centre. Lecture seule.
 */
public interface PlacesDesSeancesPubliees {

    Optional<PlaceDeSeancePubliee> de(UUID centre, UUID seance);

    /**
     * @param codeSalle      code de la salle du centre, {@code null} si la séance n'a pas de salle ou si la salle n'est pas du centre
     * @param codeCreneau    code du créneau du centre, {@code null} si la séance n'a pas de créneau ou s'il n'est pas du centre
     * @param libelleCreneau libellé du créneau du centre, {@code null} dans les mêmes cas
     */
    record PlaceDeSeancePubliee(String codeSalle, String codeCreneau, String libelleCreneau) {
    }
}

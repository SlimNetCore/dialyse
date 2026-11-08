package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Langage publié (fourni à la Séance par la plateforme, ADR 0091) : le rappel à l'administrateur du centre des séances à régulariser
 * (alerte durable {@code SEANCES_A_REGULARISER}, diffusée au canal du centre, cible {@code ADMIN}). Une seule alerte par centre et par
 * jour du centre (identifiant déterministe, sûr avec plusieurs instances du serveur).
 */
public interface RappelsFournis {

    /**
     * @param jour         jour du centre du rappel (avec le centre, la clé de l'alerte)
     * @param nbSeances    nombre de séances à régulariser (strictement positif)
     * @param plusAncienne date de la plus ancienne, nulle si elle est inconnue
     * @return vrai si l'alerte a été émise, faux si celle de ce jour existait déjà
     */
    boolean rappeler(UUID centre, LocalDate jour, long nbSeances, LocalDate plusAncienne);
}

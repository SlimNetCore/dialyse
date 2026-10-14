package com.hemodialyse.backend.contexts.gmao.application.piecejointe;

import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;

/**
 * Port sortant de lecture : les pièces d'une intervention, métadonnées seulement (les octets ne sont jamais lus), de la plus
 * récente à la plus ancienne (ex aequo départagés par identifiant, ce que le legacy laissait au hasard : KB-GMAO-67).
 * Les bornes refusées ({@code page < 0}, {@code size < 1}) lèvent {@link IllegalArgumentException}, comme Spring Data.
 */
public interface LecturePiecesJointes {

    PageDePiecesJointes deLIntervention(InterventionId intervention, int page, int size);
}

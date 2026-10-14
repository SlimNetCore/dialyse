package com.hemodialyse.backend.contexts.gmao.domain.piecejointe;

import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;

import java.util.Optional;

/**
 * Port sortant : persistance de l'agrégat {@link PieceJointe}, octets compris (stockés en base, ADR 0024). Les listes, elles,
 * passent par le read model {@code LecturePiecesJointes} qui ne lit jamais les octets.
 */
public interface PieceJointeRepository {

    void enregistrer(PieceJointe piece);

    Optional<PieceJointe> trouverParId(PieceJointeId id);

    /** La pièce sans ses octets (tableau vide) : suffit pour la retirer et en publier l'événement sans lire jusqu'à 5 Mo. */
    Optional<PieceJointe> trouverPourRetrait(PieceJointeId id);

    /** Nombre de pièces de l'intervention (R23). */
    long compter(InterventionId intervention);

    /** Suppression physique : la ligne et ses octets disparaissent. */
    void supprimer(PieceJointeId id);
}

package com.hemodialyse.backend.contexts.gmao.application.piecejointe;

import com.hemodialyse.backend.contexts.gmao.application.Horloge;
import com.hemodialyse.backend.contexts.gmao.application.PublicateurDEvenements;
import com.hemodialyse.backend.contexts.gmao.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.gmao.application.intervention.InterventionIntrouvable;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointe;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointeId;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointeRepository;

/**
 * Retire une pièce d'une intervention du centre : suppression physique, sans corbeille (KB-GMAO-65, reproduit) ; publie
 * {@code PieceJointeRetiree}. Refusé sur une intervention annulée, terminée ou supprimée (KB-GMAO-64).
 */
public final class RetirerPieceJointe {

    private final InterventionsHotes hotes;
    private final PieceJointeRepository pieces;
    private final PublicateurDEvenements publicateur;
    private final Horloge horloge;
    private final UniteDeTravail uniteDeTravail;

    public RetirerPieceJointe(InterventionsHotes hotes, PieceJointeRepository pieces, PublicateurDEvenements publicateur,
                              Horloge horloge, UniteDeTravail uniteDeTravail) {
        this.hotes = hotes;
        this.pieces = pieces;
        this.publicateur = publicateur;
        this.horloge = horloge;
        this.uniteDeTravail = uniteDeTravail;
    }

    public void executer(InterventionId intervention, CentreId centre, PieceJointeId id, UtilisateurId par) {
        uniteDeTravail.executer(() -> {
            hotes.verrouiller(intervention).filter(h -> h.centre().equals(centre)).orElseThrow(InterventionIntrouvable::new)
                    .exigerOuverte();
            PieceJointe piece = pieces.trouverPourRetrait(id).filter(p -> p.interventionId().equals(intervention))
                    .orElseThrow(PieceJointeIntrouvable::new);
            piece.retirer(par, horloge.maintenant());
            pieces.supprimer(id);
            publicateur.publier(piece.tirerEvenements());
            return null;
        });
    }
}

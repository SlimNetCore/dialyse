package com.hemodialyse.backend.contexts.gmao.application.piecejointe;

import com.hemodialyse.backend.contexts.gmao.application.GenerateurIdentifiants;
import com.hemodialyse.backend.contexts.gmao.application.Horloge;
import com.hemodialyse.backend.contexts.gmao.application.PublicateurDEvenements;
import com.hemodialyse.backend.contexts.gmao.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.gmao.application.intervention.InterventionIntrouvable;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.InterventionHote;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointe;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointeId;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointeRepository;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.TypeDePiece;

/**
 * Joint un justificatif à une intervention du centre (R22, R23) ; publie {@code PieceJointeAjoutee}. Une seule transaction :
 * l'intervention est verrouillée, puis dans l'ordre l'intervention doit être ouverte (KB-GMAO-64), le quota de 50 n'être pas
 * atteint (KB-GMAO-58, contrôlé sous verrou), le type de pièce exister, le fichier être acceptable.
 */
public final class JoindrePieceJointe {

    private final InterventionsHotes hotes;
    private final PieceJointeRepository pieces;
    private final PublicateurDEvenements publicateur;
    private final Horloge horloge;
    private final GenerateurIdentifiants identifiants;
    private final UniteDeTravail uniteDeTravail;

    public JoindrePieceJointe(InterventionsHotes hotes, PieceJointeRepository pieces, PublicateurDEvenements publicateur,
                              Horloge horloge, GenerateurIdentifiants identifiants, UniteDeTravail uniteDeTravail) {
        this.hotes = hotes;
        this.pieces = pieces;
        this.publicateur = publicateur;
        this.horloge = horloge;
        this.identifiants = identifiants;
        this.uniteDeTravail = uniteDeTravail;
    }

    public PieceJointeVue executer(JoindrePieceJointeCommande commande) {
        return uniteDeTravail.executer(() -> {
            InterventionHote hote = hotes.verrouiller(commande.intervention())
                    .filter(h -> h.centre().equals(commande.centre())).orElseThrow(InterventionIntrouvable::new);
            hote.exigerOuverte();
            PieceJointe.verifierLeQuota(pieces.compter(commande.intervention()));
            TypeDePiece type = TypeDePiece.depuis(commande.type());
            PieceJointe piece = PieceJointe.joindre(new PieceJointeId(identifiants.nouvelIdentifiant()), commande.intervention(),
                    commande.centre(), type, commande.nomFichier(), commande.contenu(), commande.par(), horloge.maintenant());
            pieces.enregistrer(piece);
            publicateur.publier(piece.tirerEvenements());
            return PieceJointeVue.depuis(piece);
        });
    }
}
